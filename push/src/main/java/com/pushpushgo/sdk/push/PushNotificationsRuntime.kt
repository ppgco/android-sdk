package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.liveactivity.LiveActivities
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.push.createNotificationChannel
import com.pushpushgo.sdk.push.push.deserializeNotificationData
import com.pushpushgo.sdk.push.push.handleNotificationLinkClick
import com.pushpushgo.sdk.push.subscription.DefaultPushSubscriptionProvider
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.work.UploadDelegate
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PushNotificationsRuntime(
  val application: Application,
  val config: Config,
) {
  init {
    check(WorkManager.isInitialized()) {
      "WorkManager must be initialized before using PushNotifications SDK"
    }
  }

  private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val subscriptionMutex = Mutex()
  private var isDeinitialized = false

  val sharedPreferencesHelper = SharedPreferencesHelper(application)
  private val apiService = ApiService.fromConfig(config)
  val apiRepository = ApiRepository(application, apiService, sharedPreferencesHelper, config)
  val uploadDelegate = UploadDelegate(apiRepository)
  val uploadManager = UploadManager(application, sharedPreferencesHelper)
  val pushNotificationsDelegate = PushNotificationDelegate(sharedPreferencesHelper, apiRepository, uploadManager)

  val defaultInvalidProjectIdHandler: InvalidProjectIdHandler = { pushProjectId, _, currentProjectId ->
    logDebug("Project ID inconsistency detected! Project ID from push is $pushProjectId while SDK is configured with $currentProjectId")
  }

  val defaultNotificationClickHandler: NotificationClickHandler = { context, url, overrideFlags ->
    handleNotificationLinkClick(
      context,
      url,
      overrideFlags,
    )
  }

  val liveActivities: LiveActivities =
    LiveActivities(
      application = application,
      scope = sdkScope,
      apiRepository = apiRepository,
      sharedPreferencesHelper = sharedPreferencesHelper,
      getSubscriberId = { getSubscriberId() },
      notificationClickHandler = { notificationClickHandler },
    )

  init {
    val platformType = getPlatformType()
    val startupMessage =
      "PushNotifications SDK ${PushNotifications.VERSION} initialized (project id: ${config.projectId}, platform: $platformType)"
    println(startupMessage)

    createNotificationChannel(application)

    if (sharedPreferencesHelper.isSubscribed) {
      uploadManager.syncToken(null)
      uploadManager.schedulePeriodicTokenSync()
    }
  }

  init {
    NotificationStatusChecker(
      context = application,
      sdkScope = sdkScope,
      sharedPreferencesHelper = sharedPreferencesHelper,
    ).start()
  }

  var notificationClickHandler: NotificationClickHandler = defaultNotificationClickHandler
    private set

  var invalidProjectIdHandler: InvalidProjectIdHandler = defaultInvalidProjectIdHandler
    private set

  var defaultIsSubscribed: Boolean = false
    private set

  var errorCallback: ((Throwable) -> Unit)? = null
    private set

  val customClickIntentFlags: Int
    get() = sharedPreferencesHelper.customIntentFlags

  fun setCustomClickIntentFlags(flags: Int) {
    sharedPreferencesHelper.customIntentFlags = flags
  }

  fun setDefaultIsSubscribed(isSubscribed: Boolean) {
    defaultIsSubscribed = isSubscribed
  }

  fun setNotificationClickHandler(handler: NotificationClickHandler) {
    notificationClickHandler = handler
  }

  fun setInvalidProjectIdHandler(handler: InvalidProjectIdHandler) {
    invalidProjectIdHandler = handler
  }

  fun setErrorCallback(callback: ((Throwable) -> Unit)?) {
    errorCallback = callback
  }

  fun getProjectId(): String = config.projectId

  fun getApiKey(): String = config.apiKey

  fun isSubscribed(): Boolean = sharedPreferencesHelper.isSubscribed

  fun getSubscriberId(): String? = sharedPreferencesHelper.subscriberId

  fun getPushToken(): String? = sharedPreferencesHelper.lastToken

  suspend fun subscribe() {
    subscriptionMutex.withLock {
      assertActive()
      subscribeLocked()
    }
  }

  suspend fun unsubscribe() {
    subscriptionMutex.withLock {
      assertActive()
      unsubscribeLocked()
    }
  }

  suspend fun deinitialize() {
    subscriptionMutex.withLock {
      assertActive()

      if (sharedPreferencesHelper.isSubscribed) {
        unsubscribeLocked()
      }

      sharedPreferencesHelper.clearProjectData()
      liveActivities.clearProjectData()

      isDeinitialized = true
      sdkScope.cancel()
    }
  }

  private suspend fun subscribeLocked() {
    apiRepository.registerToken(null)
    uploadManager.schedulePeriodicTokenSync()
    sharedPreferencesHelper.isSubscribed = true
  }

  private suspend fun unsubscribeLocked() {
    apiRepository.unregisterSubscriber()
    uploadManager.cancelPeriodicTokenSync()
    sharedPreferencesHelper.isSubscribed = false
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
      return invalidProjectIdHandler(intentProjectId.orEmpty(), intentSubscriberId, config.projectId)
    }

    NotificationManagerCompat.from(application).cancel(intentNotificationId)

    // TODO Remove duplicated code
    val notify = deserializeNotificationData(intent.extras)
    notificationClickHandler(application, notify?.redirectLink ?: intentLink, overrideFlags)
    intent.removeExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)

    uploadManager.sendEvent(
      type = EventType.CLICKED,
      buttonId = intentButtonId,
      projectId = notify?.project ?: intentProjectId,
      subscriberId = notify?.subscriber ?: intentSubscriberId,
      campaign = notify?.campaignId ?: intentCampaignId,
    )
  }

  fun areNotificationsEnabled(): Boolean =
    com.pushpushgo.sdk.push.push
      .areNotificationsEnabled(application)

  fun createBeacon(): BeaconBuilder = BeaconBuilder(uploadDelegate)

  fun getPushSubscriptionProvider(): PushSubscriptionProvider = DefaultPushSubscriptionProvider(application)
}
