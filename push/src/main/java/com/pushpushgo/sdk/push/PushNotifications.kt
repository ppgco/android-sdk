package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.common.util.concurrent.ListenableFuture
import com.pushpushgo.sdk.core.config.Config
import com.pushpushgo.sdk.core.config.ManifestConfigProvider
import com.pushpushgo.sdk.core.push.PushSubscriptionProvider
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.data.mapToDto
import com.pushpushgo.sdk.push.di.NetworkModule
import com.pushpushgo.sdk.push.di.WorkModule
import com.pushpushgo.sdk.push.dto.PPGoNotification
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.push.createNotificationChannel
import com.pushpushgo.sdk.push.push.deserializeNotificationData
import com.pushpushgo.sdk.push.push.handleNotificationLinkClick
import com.pushpushgo.sdk.push.subscription.DefaultPushSubscriptionProvider
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.mapToBundle
import com.pushpushgo.sdk.push.work.UploadDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future

class PushNotifications private constructor(
  private val application: Application,
  internal val config: Config,
) {
  companion object {
    const val VERSION = "2.2.4-20250303~1"

    internal const val TAG = "[PushPushGo:PushNotifications]"

    /**
     * an instance of PushPushGo library
     */
    @Volatile
    private var INSTANCE: PushNotifications? = null

    fun isInitialized(): Boolean = INSTANCE != null

    @JvmStatic
    fun getInstance(): PushNotifications = checkNotNull(INSTANCE) { "PushNotifications SDK is not initialized" }

    /**
     * function to create an instance of PushPushGo object to handle push notifications
     * @param application - an application to get apiKey from META DATA stored in Your Manifest.xml file
     * @return PushPushGo instance
     */
    @JvmStatic
    fun initialize(application: Application): PushNotifications =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: PushNotifications(application, ManifestConfigProvider(application).provide()).also { INSTANCE = it }
      }

    /**
     * function to create an instance of PushPushGo object to handle push notifications
     * @param application - an application to handle DI
     * @param config - application config
     * @return PushPushGo instance
     */
    @JvmStatic
    fun initialize(
      application: Application,
      config: Config,
    ): PushNotifications {
      if (INSTANCE == null) {
        INSTANCE = PushNotifications(application, config)
      }

      return INSTANCE as PushNotifications
    }

    @JvmStatic
    private fun reinitialize(
      application: Application,
      config: Config,
    ): PushNotifications {
      INSTANCE = PushNotifications(application, config)

      return INSTANCE as PushNotifications
    }
  }

  init {
    val platformType = getPlatformType()
    val startupMessage = "PushNotifications SDK $VERSION initialized (project id: ${config.projectId}, platform: $platformType)"
    println(startupMessage)

    createNotificationChannel(application)
    NotificationStatusChecker.start(application)
  }

  private val networkModule by lazy {
    NetworkModule(
      context = application,
      config = config,
    )
  }

  private val workModule by lazy { WorkModule(application) }

  private val uploadDelegate by lazy { UploadDelegate() }

  internal fun getNetwork() = networkModule.apiRepository

  internal fun getUploadManager() = workModule.uploadManager

  var onInvalidProjectIdHandler: InvalidProjectIdHandler = { pushProjectId, _, currentProjectId ->
    logDebug("Project ID inconsistency detected! Project ID from push is $pushProjectId while SDK is configured with $currentProjectId")
  }

  /**
   * Settings used for migration to support switch user before start first time app after upgrade/switch
   * defaultIsSubscribed  default is false
   */
  var defaultIsSubscribed: Boolean = false

  var notificationHandler: NotificationHandler = { context, url, overrideFlags ->
    handleNotificationLinkClick(
      context,
      url,
      overrideFlags,
    )
  }

  /**
   * function to check whether the given notification data belongs to the PPGo sender
   *
   * @param notificationIntent - pending intent of clicked notification
   *
   * @return boolean
   */
  fun isPPGoPush(notificationIntent: Intent?): Boolean = notificationIntent?.hasExtra("project") == true

  /**
   * function to check whether the given notification data belongs to the PPGo sender
   *
   * @param notificationData - data field of received notification
   *
   * @return boolean
   */
  fun isPPGoPush(notificationData: Map<String, String>): Boolean = notificationData.containsKey("project")

  /**
   * function to retrieve PPGo notification details
   *
   * @param notificationIntent - pending intent of clicked notification
   *
   * @return Notification
   */
  fun getNotificationDetails(notificationIntent: Intent?): PPGoNotification? =
    deserializeNotificationData(notificationIntent?.extras)?.mapToDto()

  /**
   * function to retrieve PPGo notification details
   *
   * @param notificationData - data field of received notification
   *
   * @return Notification
   */
  fun getNotificationDetails(notificationData: Map<String, String>): PPGoNotification? =
    deserializeNotificationData(notificationData.mapToBundle())?.mapToDto()

  /**
   * function set custom intent flags to shared preferences
   * when push receives then check for this flags and add
   * them in PendingIntent launcherActivity as flags
   */
  fun setCustomClickIntentFlags(flags: Int) {
    networkModule.sharedPref.customIntentFlags = flags
  }

  /**
   * function returns custom flags from shared contexts for click intent
   */
  fun getCustomClickIntentFlags(): Int = networkModule.sharedPref.customIntentFlags

  /**
   * helper function to handle click on notification from background
   */
  fun handleBackgroundNotificationClick(
    intent: Intent?,
    overrideFlags: Int = Intent.FLAG_ACTIVITY_NEW_TASK,
  ) {
    if (intent?.hasExtra(PushNotificationDelegate.PROJECT_ID_EXTRA) != true) return

    val intentProjectId = intent.getStringExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)
    val intentSubscriberId = intent.getStringExtra(PushNotificationDelegate.SUBSCRIBER_ID_EXTRA).orEmpty()
    val intentButtonId = intent.getIntExtra(PushNotificationDelegate.BUTTON_ID_EXTRA, 0)
    val intentLink = intent.getStringExtra(PushNotificationDelegate.LINK_EXTRA).orEmpty()
    val intentCampaignId = intent.getStringExtra(PushNotificationDelegate.CAMPAIGN_ID_EXTRA).orEmpty()
    val intentNotificationId = intent.getIntExtra(PushNotificationDelegate.NOTIFICATION_ID_EXTRA, 0)

    if (intentProjectId != config.projectId) {
      return onInvalidProjectIdHandler(intentProjectId.orEmpty(), intentSubscriberId, config.projectId)
    }

    NotificationManagerCompat.from(application).cancel(intentNotificationId)

    // TODO Remove duplicated code
    val notify = deserializeNotificationData(intent.extras)
    notificationHandler(application, notify?.redirectLink ?: intentLink, overrideFlags)
    intent.removeExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)

    uploadDelegate.sendEvent(
      type = EventType.CLICKED,
      buttonId = intentButtonId,
      projectId = notify?.project ?: intentProjectId,
      subscriberId = notify?.subscriber ?: intentSubscriberId,
      campaign = notify?.campaignId ?: intentCampaignId,
    )
  }

  /**
   * function to read Your API Key from an PushPushGo library instance
   * @return API Key String
   */
  fun getProjectId(): String = config.projectId

  /**
   * function to read Your API Key from an PushPushGo library instance
   * @return API Key String
   */
  fun getApiKey(): String = config.apiKey

  /**
   * function to read Your subscriber id from an PushPushGo library instance
   * @return subscriber id String
   */
  fun getSubscriberId(): String = networkModule.sharedPref.subscriberId

  /**
   * function to check if user subscribed to notifications
   * @return boolean true if subscribed
   */
  fun isSubscribed(): Boolean = networkModule.sharedPref.isSubscribed

  /**
   * function to retrieve last push token used to subscribe that
   */
  fun getPushToken(): ListenableFuture<String> =
    CoroutineScope(Job() + Dispatchers.IO).future {
      networkModule.sharedPref.lastToken.takeIf { it.isNotEmpty() } ?: getPlatformPushToken(application)
    }

  /**
   * function to register subscriber
   */
  fun registerSubscriber() {
    if (!areNotificationsEnabled()) {
      return logError("Notifications disabled! Subscriber registration canceled")
    }

    networkModule.sharedPref.isSubscribed = true
    getUploadManager().sendRegister(null)
  }

  /**
   * function to register subscriber and returns future with subscriber id
   *
   * @return string subscriber ID
   */
  fun createSubscriber(): ListenableFuture<String> =
    CoroutineScope(Job() + Dispatchers.IO).future {
      check(areNotificationsEnabled()) {
        "Notifications disabled! Subscriber registration canceled"
      }

      getNetwork().registerToken(null)
      networkModule.sharedPref.isSubscribed = true
      getSubscriberId()
    }

  /**
   * function to unregister subscriber
   */
  fun unregisterSubscriber() {
    getUploadManager().sendUnregister()
    networkModule.sharedPref.isSubscribed = false
  }

  fun unregisterSubscriber(
    projectId: String,
    projectToken: String,
    subscriberId: String,
  ): ListenableFuture<Unit> =
    CoroutineScope(Job() + Dispatchers.IO).future {
      getInstance().getNetwork().unregisterSubscriber(
        projectId = projectId,
        token = projectToken,
        subscriberId = subscriberId,
      )
      networkModule.sharedPref.isSubscribed = false
    }

  /**
   * function to re-subscribe to different project (previously unsubscribe from current project)
   * WARNING: after resubscribe use object returned by this function instead of previous one
   *
   * @param newProjectId - project id to which we are switching
   * @param newProjectToken - project token
   */
  fun migrateToNewProject(
    newProjectId: String,
    newProjectToken: String,
  ): ListenableFuture<PushNotifications> =
    CoroutineScope(Job() + Dispatchers.IO).future {
      check(areNotificationsEnabled()) {
        "Notifications disabled! Subscriber registration canceled"
      }

      getInstance().getNetwork().migrateSubscriber(
        newProjectId = newProjectId,
        newToken = newProjectToken,
      )
      reinitialize(
        application = application,
        config =
          Config(
            projectId = newProjectId,
            apiKey = newProjectToken,
            isDebug = config.isDebug,
            apiUrl = config.apiUrl,
          ),
      ).apply {
        notificationHandler = this@PushNotifications.notificationHandler
        onInvalidProjectIdHandler = this@PushNotifications.onInvalidProjectIdHandler
      }
    }

  fun areNotificationsEnabled(): Boolean = areNotificationsEnabled(application)

  /**
   * function to construct and send beacon
   */
  fun createBeacon(): BeaconBuilder = BeaconBuilder(uploadDelegate)

  fun getPushSubscriptionProvider(): PushSubscriptionProvider = DefaultPushSubscriptionProvider(application)
}

typealias NotificationHandler = (context: Context, url: String, overrideFlags: Int) -> Unit

typealias InvalidProjectIdHandler = (pushProjectId: String, pushSubscriberId: String, currentProjectId: String) -> Unit
