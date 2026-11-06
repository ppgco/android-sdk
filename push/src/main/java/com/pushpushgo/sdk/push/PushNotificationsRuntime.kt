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
import com.pushpushgo.sdk.push.network.ApiServiceFactory
import com.pushpushgo.sdk.push.network.DefaultApiServiceFactory
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.push.createNotificationChannel
import com.pushpushgo.sdk.push.push.deserializeNotificationData
import com.pushpushgo.sdk.push.subscription.DefaultPushSubscriptionProvider
import com.pushpushgo.sdk.push.utils.PlatformPushTokenProvider
import com.pushpushgo.sdk.push.utils.PushTokenProvider
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.isSameProjectAs
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.logWarning
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PushNotificationsRuntime(
  val application: Application,
  val config: Config,
  private val callbacks: PushNotificationsCallbacks,
  apiServiceFactory: ApiServiceFactory = DefaultApiServiceFactory,
  pushTokenProvider: PushTokenProvider = PlatformPushTokenProvider(application),
  dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  init {
    check(WorkManager.isInitialized()) {
      "WorkManager must be initialized before using PushNotifications SDK"
    }
  }

  private val sdkScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val operationMutex = Mutex()

  @Volatile
  private var isReleased = false

  val sharedPreferencesHelper = SharedPreferencesHelper(application)
  private val apiService = apiServiceFactory.create(config)
  val apiRepository = ApiRepository(apiService, sharedPreferencesHelper, config, pushTokenProvider)
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

    if (sharedPreferencesHelper.subscriptionRequested && areNotificationsEnabled(application)) {
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
      onCheck = ::requestReconcile,
    )

  fun start() {
    publishSubscriptionStatus()
    notificationStatusChecker.start()
  }

  val customClickIntentFlags: Int
    get() = sharedPreferencesHelper.customIntentFlags

  fun setCustomClickIntentFlags(flags: Int) {
    sharedPreferencesHelper.customIntentFlags = flags
  }

  fun getProjectId(): String = config.projectId

  fun getApiKey(): String = config.apiKey

  /** Returns the backend state last confirmed by the SDK. */
  fun getSubscriptionStatus(): SubscriptionStatus =
    sharedPreferencesHelper.subscriberId?.let {
      SubscriptionStatus.Subscribed(
        subscriberId = it,
        projectId = config.projectId,
      )
    }
      ?: SubscriptionStatus.Unsubscribed

  fun isSubscriptionRequested(): Boolean = sharedPreferencesHelper.subscriptionRequested

  fun isSubscribed(): Boolean = sharedPreferencesHelper.subscriberId != null

  private fun publishSubscriptionStatus() {
    if (!isReleased) callbacks.publishSubscriptionStatus(getSubscriptionStatus())
  }

  fun getSubscriberId(): String? = sharedPreferencesHelper.subscriberId

  fun getPushToken(): String? = sharedPreferencesHelper.lastToken

  fun subscribe() {
    assertActive()

    check(areNotificationsEnabled(application)) {
      "Cannot subscribe because notifications are disabled"
    }

    sharedPreferencesHelper.subscriptionRequested = true
    requestReconcile()
  }

  fun unsubscribe() {
    assertActive()

    sharedPreferencesHelper.subscriptionRequested = false
    requestReconcile()
  }

  /** Runs [reconcile] in the background; a transient failure is retried by SubscriptionReconcileWorker. */
  fun requestReconcile() {
    sdkScope.launch {
      try {
        reconcile()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logError("Subscription reconciliation failed", e)
        if (e.isTransientApiError()) uploadManager.scheduleSubscriptionReconcile()
      }
    }
  }

  /**
   * Brings the backend registration in line with the user's subscription request and the
   * notification permission. Decides on the state found under the operation lock, so
   * reconciliations and project switches act one after another. A released runtime is left alone.
   */
  suspend fun reconcile() {
    try {
      operationMutex.withLock {
        if (isReleased) return

        val shouldBeRegistered = sharedPreferencesHelper.subscriptionRequested && areNotificationsEnabled(application)
        val isRegistered = sharedPreferencesHelper.subscriberId != null

        when {
          shouldBeRegistered && !isRegistered -> registerLocked()
          !shouldBeRegistered && isRegistered -> unregisterLocked()
        }
      }
    } finally {
      publishSubscriptionStatus()
    }
  }

  /**
   * Stops this runtime without calling the API and leaves the persisted state to the runtime that
   * replaces it, which releases that state when it belongs to another project.
   */
  suspend fun release() {
    operationMutex.withLock {
      assertActive()

      isReleased = true
      notificationStatusChecker.stop()
      sdkScope.cancel()
    }
  }

  fun sendBeacon(beacon: Beacon) {
    assertActive()

    if (!sharedPreferencesHelper.subscriptionRequested) {
      return logWarning("Beacon discarded - the device is not subscribed")
    }

    val subscriberId =
      getSubscriberId() ?: return logWarning("Beacon discarded - the device is not subscribed")

    uploadManager.enqueueBeacon(subscriberId, beacon.payload)
  }

  private suspend fun registerLocked() {
    apiRepository.registerToken(null)
    // Before the background work below is scheduled, which can take a moment
    publishSubscriptionStatus()

    val subscriberId = sharedPreferencesHelper.subscriberId ?: return

    // Back in a project whose cleanup is still pending - the API returned the same subscriber
    uploadManager.cancelProjectCleanup(config.projectId, subscriberId)
    uploadManager.schedulePeriodicTokenSync(subscriberId)
  }

  /** Removes the registration strictly - a failure is thrown and the local state is kept. */
  private suspend fun unregisterLocked() {
    apiRepository.unregisterSubscriber()
    uploadManager.cancelAllJobs()
    uploadManager.cancelBeacons(config.projectId)
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
    uploadManager.cancelBeacons(owner.projectId)
    discardPersistedLiveActivities()

    sharedPreferencesHelper.clearProjectData()
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
    check(!isReleased) { "This instance has been released" }
  }

  fun handleBackgroundNotificationClick(
    intent: Intent?,
    overrideFlags: Int,
  ) {
    if (liveActivities.handleClick(intent, overrideFlags)) return
    if (intent?.hasExtra(PushNotificationDelegate.PROJECT_ID_EXTRA) != true) return

    val intentProjectId = intent.getStringExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)
    val intentSubscriberId = intent.getStringExtra(PushNotificationDelegate.SUBSCRIBER_ID_EXTRA).orEmpty()
    val intentButtonId = intent.getIntExtra(PushNotificationDelegate.BUTTON_ID_EXTRA, 0)
    val intentLink = intent.getStringExtra(PushNotificationDelegate.LINK_EXTRA).orEmpty()
    val intentCampaignId = intent.getStringExtra(PushNotificationDelegate.CAMPAIGN_ID_EXTRA).orEmpty()
    val intentNotificationId = intent.getIntExtra(PushNotificationDelegate.NOTIFICATION_ID_EXTRA, 0)

    if (intentProjectId != config.projectId) {
      return callbacks.notifyInvalidProjectId(
        intentProjectId.orEmpty(),
        intentSubscriberId,
        config.projectId,
      )
    }

    NotificationManagerCompat.from(application).cancel(intentNotificationId)

    // TODO Remove duplicated code
    val notify = deserializeNotificationData(intent.extras)
    callbacks.notifyNotificationClick(
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
