package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.exception.isTransientApiError
import com.pushpushgo.sdk.push.liveactivity.LiveActivities
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.push.createNotificationChannel
import com.pushpushgo.sdk.push.push.deserializeNotificationData
import com.pushpushgo.sdk.push.subscription.DefaultPushSubscriptionProvider
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.isSameProjectAs
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.logWarning
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PushNotificationsRuntime(
  val application: Application,
  val config: Config,
  private val callbacks: PushNotificationsCallbacks,
) {
  init {
    check(WorkManager.isInitialized()) {
      "WorkManager must be initialized before using PushNotifications SDK"
    }
  }

  private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val operationMutex = Mutex()

  @Volatile
  private var isDeinitialized = false

  val sharedPreferencesHelper = SharedPreferencesHelper(application)
  private val apiService = ApiService.fromConfig(config)
  val apiRepository = ApiRepository(application, apiService, sharedPreferencesHelper, config)
  val uploadManager = UploadManager(application, config)
  val pushNotificationsDelegate = PushNotificationDelegate(sharedPreferencesHelper, apiRepository, uploadManager, callbacks)

  // Must run before liveActivities restores the Live Activities persisted by the previous runtime
  init {
    reconcileProjectState()
  }

  val liveActivities: LiveActivities =
    LiveActivities(
      application = application,
      scope = sdkScope,
      apiRepository = apiRepository,
      sharedPreferencesHelper = sharedPreferencesHelper,
      getSubscriberId = { getSubscriberId() },
      callbacks = callbacks,
      operationMutex = operationMutex,
      assertActive = ::assertActive,
    )

  init {
    val platformType = getPlatformType()
    val startupMessage =
      "PushNotifications SDK ${PushNotifications.VERSION} initialized (project id: ${config.projectId}, platform: $platformType)"
    println(startupMessage)

    createNotificationChannel(application)

    if (sharedPreferencesHelper.subscriptionRequested) {
      val subscriberId = sharedPreferencesHelper.subscriberId

      if (subscriberId != null) {
        uploadManager.syncToken(subscriberId, null)
        uploadManager.schedulePeriodicTokenSync(subscriberId)
      }
    }
  }

  private val notificationStatusChecker =
    NotificationStatusChecker(
      application = application,
      sdkScope = sdkScope,
      sharedPreferencesHelper = sharedPreferencesHelper,
      register = ::registerIfRequested,
      unregister = ::unregisterKeepingRequest,
      onChecked = ::publishSubscriptionStatus,
    )

  init {
    notificationStatusChecker.start()
    publishSubscriptionStatus()
  }

  val customClickIntentFlags: Int
    get() = sharedPreferencesHelper.customIntentFlags

  fun setCustomClickIntentFlags(flags: Int) {
    sharedPreferencesHelper.customIntentFlags = flags
  }

  fun getProjectId(): String = config.projectId

  fun getApiKey(): String = config.apiKey

  /** Computes the status from the persisted state and the current notification settings. */
  fun getSubscriptionStatus(): SubscriptionStatus =
    when {
      !sharedPreferencesHelper.subscriptionRequested -> SubscriptionStatus.UNSUBSCRIBED
      !areNotificationsEnabled(application) -> SubscriptionStatus.NOTIFICATIONS_DISABLED
      sharedPreferencesHelper.subscriberId == null -> SubscriptionStatus.PENDING
      else -> SubscriptionStatus.SUBSCRIBED
    }.also { status ->
      // Keeps the flow in line with what callers read, also for changes the SDK has not acted on yet
      if (!isDeinitialized) callbacks.publishSubscriptionStatus(status)
    }

  fun isSubscribed(): Boolean = getSubscriptionStatus() == SubscriptionStatus.SUBSCRIBED

  private fun publishSubscriptionStatus() {
    getSubscriptionStatus()
  }

  /** Whether the user's subscription is registered, regardless of the notification settings. */
  private fun hasActiveRegistration(): Boolean =
    sharedPreferencesHelper.subscriptionRequested && sharedPreferencesHelper.subscriberId != null

  fun getSubscriberId(): String? = sharedPreferencesHelper.subscriberId

  fun getPushToken(): String? = sharedPreferencesHelper.lastToken

  suspend fun subscribe() {
    operationMutex.withLock {
      assertActive()

      check(areNotificationsEnabled(application)) {
        "Cannot subscribe because notifications are disabled"
      }

      subscribeLocked()
    }
  }

  /**
   * Cancels the user's subscription. The choice is stored before anything else and never fails
   * because of the API: a subscriber that cannot be removed right now is removed in the background.
   */
  suspend fun unsubscribe() {
    operationMutex.withLock {
      assertActive()

      withContext(Dispatchers.IO) {
        sharedPreferencesHelper.storeSubscriptionRequestedNow(false)
      }
      uploadManager.cancelAllJobs()

      sharedPreferencesHelper.subscriberId?.let { subscriberId ->
        removeSubscriber(subscriberId)
        sharedPreferencesHelper.subscriberId = ""
      }
    }

    publishSubscriptionStatus()
  }

  private suspend fun removeSubscriber(subscriberId: String) {
    try {
      apiRepository.deleteSubscriber(subscriberId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (e.isTransientApiError()) {
        logWarning("Subscriber $subscriberId cannot be removed now, it will be removed in the background: $e")
        uploadManager.scheduleProjectCleanup(config, subscriberId, emptyMap())
      } else {
        logError("Failed to remove subscriber $subscriberId", e)
      }
    }
  }

  /**
   * Unregisters the device but keeps the user's subscription request, so the notification status
   * checker can register it again once notifications are enabled back.
   */
  suspend fun unregisterKeepingRequest() {
    operationMutex.withLock {
      assertActive()
      apiRepository.unregisterSubscriber()
      uploadManager.cancelAllJobs()
    }

    publishSubscriptionStatus()
  }

  /**
   * Starts [subscribeIfRequested] without waiting for it, e.g. right after a project switch. The
   * attempt is cancelled when this runtime is released.
   */
  fun subscribeIfRequestedInBackground(): Job = sdkScope.launch { subscribeIfRequested() }

  /**
   * Subscribes the device when the user asked for notifications but it is not registered in this
   * project. A failure is reported to the error callback rather than thrown; the notification
   * status checker keeps retrying while the app is in the foreground.
   */
  suspend fun subscribeIfRequested() {
    if (!areNotificationsEnabled(application)) return

    try {
      registerIfRequested()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // A runtime released in the meantime is not an error of the new project
      if (!isDeinitialized) logError("Subscription to project ${config.projectId} failed, it will be retried", e)
    } finally {
      publishSubscriptionStatus()
    }
  }

  /**
   * Registers the device when the user asked for notifications and it is not registered yet. The
   * check runs under the operation lock, so a project switch and the notification status checker
   * running at the same time register the device only once.
   */
  private suspend fun registerIfRequested() {
    operationMutex.withLock {
      assertActive()

      if (!sharedPreferencesHelper.subscriptionRequested || sharedPreferencesHelper.subscriberId != null) return

      check(areNotificationsEnabled(application)) {
        "Cannot subscribe because notifications are disabled"
      }

      subscribeLocked()
    }
  }

  /**
   * Stops this runtime without calling the API and leaves the persisted state to the runtime that
   * replaces it, which releases that state when it belongs to another project.
   */
  suspend fun release() {
    operationMutex.withLock {
      assertActive()

      isDeinitialized = true
      notificationStatusChecker.stop()
      sdkScope.cancel()
    }
  }

  suspend fun deinitialize() {
    operationMutex.withLock {
      assertActive()

      liveActivities.deinitialize()

      // Not isSubscribed(): a registration must be removed also while notifications are disabled
      if (hasActiveRegistration()) {
        unsubscribeLocked()
      } else {
        uploadManager.cancelAllJobs()
      }

      sharedPreferencesHelper.clearProjectData()
      liveActivities.clearProjectData()

      isDeinitialized = true
      notificationStatusChecker.stop()
      sdkScope.cancel()
    }
  }

  suspend fun sendBeacon(beacon: Beacon) {
    operationMutex.withLock {
      assertActive()
      apiRepository.sendBeacon(beacon.payload)
    }
  }

  private suspend fun subscribeLocked() {
    try {
      apiRepository.registerToken(null)
      sharedPreferencesHelper.subscriptionRequested = true
      // Before the background work below is scheduled, which can take a moment
      publishSubscriptionStatus()

      val subscriberId = sharedPreferencesHelper.subscriberId

      if (subscriberId != null) {
        // The API returns the same subscriber for the same push token while it is active, so a
        // removal scheduled by an earlier unsubscribe must not run for it anymore
        uploadManager.cancelProjectCleanup(config.projectId, subscriberId)
        uploadManager.schedulePeriodicTokenSync(subscriberId)
      }
    } finally {
      publishSubscriptionStatus()
    }
  }

  /** Removes the registration strictly - a failure is thrown and the local state is kept. */
  private suspend fun unsubscribeLocked() {
    apiRepository.unregisterSubscriber()
    uploadManager.cancelAllJobs()
    sharedPreferencesHelper.subscriptionRequested = false
  }

  /**
   * Makes the persisted subscription state belong to [config]. State saved for another project -
   * e.g. when the app initializes the SDK with a different configuration than before - is
   * released: its subscriber and Live Activity subscriptions are removed in the background with
   * that project's credentials, local state is cleared, and the user's subscription request is
   * kept, so the device registers in the current project once notifications are enabled.
   *
   * State saved before the SDK tracked its project is adopted by the current configuration.
   */
  private fun reconcileProjectState() {
    val owner = sharedPreferencesHelper.projectOwner

    when {
      owner == null -> adoptLegacyState()
      !owner.isSameProjectAs(config) -> releaseProjectState(owner)
    }

    sharedPreferencesHelper.projectOwner = config
  }

  /**
   * SDK 3.x registered the device on push token refresh and on project migration without storing
   * the user's subscription request, and apps could make a missing request count as given
   * (`defaultIsSubscribed`). A registered device without a stored request is therefore kept
   * subscribed, so updating the SDK does not unsubscribe users who receive notifications today. A
   * stored `false` stays an opt-out.
   */
  private fun adoptLegacyState() {
    if (!sharedPreferencesHelper.isSubscriptionRequestStored() && sharedPreferencesHelper.subscriberId != null) {
      sharedPreferencesHelper.subscriptionRequested = true
    }
  }

  private fun releaseProjectState(owner: Config) {
    logWarning("Persisted subscription belongs to project ${owner.projectId}, releasing it for project ${config.projectId}")

    val subscriberId = sharedPreferencesHelper.subscriberId
    val liveActivitySubscriptions = sharedPreferencesHelper.getLiveActivitySubscriptions()

    if (subscriberId != null || liveActivitySubscriptions.isNotEmpty()) {
      uploadManager.scheduleProjectCleanup(owner, subscriberId, liveActivitySubscriptions)
    }

    uploadManager.cancelAllJobs()
    discardPersistedLiveActivities()

    val subscriptionRequested = sharedPreferencesHelper.subscriptionRequested
    sharedPreferencesHelper.clearProjectData()
    sharedPreferencesHelper.subscriptionRequested = subscriptionRequested
  }

  private fun discardPersistedLiveActivities() {
    val persistence = LiveActivityPersistence(application)
    val notificationManager = NotificationManagerCompat.from(application)

    persistence
      .getActiveIds()
      .map(persistence::getNotificationId)
      .filter { it != -1 }
      .forEach(notificationManager::cancel)

    persistence.clearAll()
  }

  private fun assertActive() {
    check(!isDeinitialized) { "PushNotifications is deinitialized" }
  }

  fun handleBackgroundNotificationClick(
    intent: Intent?,
    overrideFlags: Int,
  ) {
    if (intent?.hasExtra(PushNotificationDelegate.PROJECT_ID_EXTRA) != true) return

    val intentProjectId = intent.getStringExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)
    val intentSubscriberId = intent.getStringExtra(PushNotificationDelegate.SUBSCRIBER_ID_EXTRA).orEmpty()
    val intentButtonId = intent.getIntExtra(PushNotificationDelegate.BUTTON_ID_EXTRA, 0)
    val intentLink = intent.getStringExtra(PushNotificationDelegate.LINK_EXTRA).orEmpty()
    val intentCampaignId = intent.getStringExtra(PushNotificationDelegate.CAMPAIGN_ID_EXTRA).orEmpty()
    val intentNotificationId = intent.getIntExtra(PushNotificationDelegate.NOTIFICATION_ID_EXTRA, 0)

    if (intentProjectId != config.projectId) {
      return callbacks.invalidProjectIdHandler.onInvalidProjectId(
        intentProjectId.orEmpty(),
        intentSubscriberId,
        config.projectId,
      )
    }

    NotificationManagerCompat.from(application).cancel(intentNotificationId)

    // TODO Remove duplicated code
    val notify = deserializeNotificationData(intent.extras)
    callbacks.notificationClickHandler.onNotificationClick(
      application,
      notify?.redirectLink ?: intentLink,
      overrideFlags,
    )
    intent.removeExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)

    uploadManager.sendEvent(
      type = EventType.CLICKED,
      buttonId = intentButtonId,
      subscriberId = notify?.subscriber ?: intentSubscriberId,
      campaign = notify?.campaignId ?: intentCampaignId,
    )
  }

  fun areNotificationsEnabled(): Boolean = areNotificationsEnabled(application)

  fun getPushSubscriptionProvider(): PushSubscriptionProvider = DefaultPushSubscriptionProvider(application)
}
