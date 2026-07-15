package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.core.internal.ManifestConfigProvider
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.data.mapToDto
import com.pushpushgo.sdk.push.dto.PushPushGoNotification
import com.pushpushgo.sdk.push.liveactivity.LiveActivities
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.push.createNotificationChannel
import com.pushpushgo.sdk.push.push.deserializeNotificationData
import com.pushpushgo.sdk.push.push.handleNotificationLinkClick
import com.pushpushgo.sdk.push.subscription.DefaultPushSubscriptionProvider
import com.pushpushgo.sdk.push.subscription.SubscriptionController
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.mapToBundle
import com.pushpushgo.sdk.push.work.UploadDelegate
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.CompletableFuture

class PushNotifications private constructor(
  private val application: Application,
  internal val config: Config,
) {
  companion object {
    const val VERSION = "4.0.0"

    internal const val TAG = "[PushPushGo:PushNotifications]"

    @Volatile
    private var INSTANCE: PushNotifications? = null

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

    fun isInitialized(): Boolean = INSTANCE != null

    @JvmStatic
    fun getInstance(): PushNotifications = checkNotNull(INSTANCE) { "PushNotifications SDK is not initialized" }

    /**
     * Initializes the PushNotifications SDK using configuration defined in AndroidManifest.xml.
     *
     * Subsequent calls return the same instance.
     *
     * @throws IllegalStateException if required manifest values are missing.
     */
    @JvmStatic
    fun initialize(application: Application): PushNotifications =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: PushNotifications(application, ManifestConfigProvider(application).provide()).also { INSTANCE = it }
      }

    /**
     * Initializes the PushNotifications SDK using an explicit [Config] instance.
     *
     * Subsequent calls return the same instance.
     */
    @JvmStatic
    fun initialize(
      application: Application,
      config: Config,
    ): PushNotifications =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: PushNotifications(application, config).also { INSTANCE = it }
      }
  }

  init {
    check(WorkManager.isInitialized()) {
      "WorkManager must be initialized before using PushNotifications SDK"
    }
  }

  private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val subscriptionMutex = Mutex()

  internal val sharedPreferencesHelper = SharedPreferencesHelper(application)
  private val apiService = ApiService.fromConfig(config)
  internal val apiRepository = ApiRepository(application, apiService, sharedPreferencesHelper, config)
  internal val uploadDelegate = UploadDelegate(apiRepository)
  internal val uploadManager = UploadManager(application, sharedPreferencesHelper)
  internal val pushNotificationsDelegate = PushNotificationDelegate(sharedPreferencesHelper, apiRepository, uploadManager)

  private val subscriptionController =
    SubscriptionController(
      mutex = subscriptionMutex,
      apiRepository = apiRepository,
      uploadManager = uploadManager,
      sharedPref = sharedPreferencesHelper,
      notificationsEnabled = { areNotificationsEnabled() },
    )

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
    val startupMessage = "PushNotifications SDK $VERSION initialized (project id: ${config.projectId}, platform: $platformType)"
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

  /**
   * Handler invoked when a notification is clicked.
   */
  var notificationClickHandler: NotificationClickHandler = defaultNotificationClickHandler
    private set

  /**
   * Handler invoked when a push notification contains a project ID
   * that does not match the currently configured SDK project.
   */
  var invalidProjectIdHandler: InvalidProjectIdHandler = defaultInvalidProjectIdHandler
    private set

  /**
   * Intent flags applied when launching the application from
   * a notification click.
   */
  var customClickIntentFlags: Int = sharedPreferencesHelper.customIntentFlags
    get() = sharedPreferencesHelper.customIntentFlags
    private set

  /**
   * Indicates whether the user should be treated as subscribed by default
   */
  var defaultIsSubscribed: Boolean = false
    private set

  /**
   * Optional callback invoked with otherwise-swallowed SDK errors (network
   * failures during register/unregister/event upload, response parsing, etc.).
   *
   * Useful for integrators who want to surface delivery problems in their own
   * telemetry instead of relying on Logcat. The callback may be invoked on
   * background threads; keep it lightweight and thread-safe.
   */
  var errorCallback: ((Throwable) -> Unit)? = null
    private set

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

  /**
   * Subscribes the device to notifications.
   *
   * If notifications are disabled, an [IllegalStateException] is thrown.
   *
   * @throws IllegalStateException
   */
  @JvmSynthetic
  suspend fun subscribe() {
    subscriptionController.subscribe()
  }

  /**
   * Unsubscribes the device from notifications.
   */
  @JvmSynthetic
  suspend fun unsubscribe() {
    subscriptionController.unsubscribe()
  }

  /**
   * Subscribes the device to notifications asynchronously.
   *
   * Java-friendly wrapper for [subscribe].
   *
   * @returns [CompletableFuture]
   */
  fun subscribeAsync(): CompletableFuture<Void?> =
    sdkScope.future {
      subscribe()
      null
    }

  /**
   * Unsubscribes the device from notifications asynchronously.
   *
   * Java-friendly wrapper for [unsubscribe].
   *
   * @returns [CompletableFuture]
   */
  fun unsubscribeAsync(): CompletableFuture<Void?> =
    sdkScope.future {
      unsubscribe()
      null
    }

  /**
   * Checks whether the given notification intent belongs to PushPushGo.
   *
   * @param notificationIntent Intent associated with the clicked notification.
   *
   * @return `true` if the notification was sent by PushPushGo, `false` otherwise.
   */
  fun isPushPushGoNotification(notificationIntent: Intent?): Boolean = notificationIntent?.hasExtra("project") == true

  /**
   * Checks whether the given notification intent belongs to PushPushGo.
   *
   * @param notificationData Data payload of the received notification.
   *
   * @return `true` if the notification was sent by PushPushGo, `false` otherwise.
   */
  fun isPushPushGoNotification(notificationData: Map<String, String>): Boolean = notificationData.containsKey("project")

  /**
   * Retrieves PushPushGo notification details from the given intent.
   *
   * @param notificationIntent Intent associated with the clicked notification.
   *
   * @return [PushPushGoNotification] instance if the intent contains valid
   * PushPushGo notification data, or `null` otherwise.
   */
  fun getNotificationDetails(notificationIntent: Intent?): PushPushGoNotification? =
    deserializeNotificationData(notificationIntent?.extras)?.mapToDto()

  /**
   * Retrieves PushPushGo notification details from the given intent.
   *
   * @param notificationData Data payload of the received notification.
   *
   * @return [PushPushGoNotification] instance if the intent contains valid
   * PushPushGo notification data, or `null` otherwise.
   */
  fun getNotificationDetails(notificationData: Map<String, String>): PushPushGoNotification? =
    deserializeNotificationData(notificationData.mapToBundle())?.mapToDto()

  /**
   * Handles a PushPushGo notification click when the application is launched
   * or resumed from the background.
   *
   * This method should be called from:
   * - `Activity.onCreate()`
   * - `Activity.onNewIntent()`
   *
   * @param intent Intent received from the notification click.
   * @param overrideFlags Optional intent flags used when launching the target activity
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

  fun areNotificationsEnabled(): Boolean = areNotificationsEnabled(application)

  fun createBeacon(): BeaconBuilder = BeaconBuilder(uploadDelegate)

  fun getPushSubscriptionProvider(): PushSubscriptionProvider = DefaultPushSubscriptionProvider(application)
}

typealias NotificationClickHandler = (context: Context, url: String, overrideFlags: Int) -> Unit

typealias InvalidProjectIdHandler = (pushProjectId: String, pushSubscriberId: String, currentProjectId: String) -> Unit
