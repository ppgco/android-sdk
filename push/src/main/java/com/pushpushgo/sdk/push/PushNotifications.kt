package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.core.internal.ManifestConfigProvider
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.data.mapToDto
import com.pushpushgo.sdk.push.dto.PushPushGoNotification
import com.pushpushgo.sdk.push.liveactivity.LiveActivityHandler
import com.pushpushgo.sdk.push.liveactivity.LiveActivityManager
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivity
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPayloadParser
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.push.createNotificationChannel
import com.pushpushgo.sdk.push.push.deserializeNotificationData
import com.pushpushgo.sdk.push.push.handleNotificationLinkClick
import com.pushpushgo.sdk.push.subscription.DefaultPushSubscriptionProvider
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.mapToBundle
import com.pushpushgo.sdk.push.work.UploadDelegate
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

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
    if (!WorkManager.isInitialized()) {
      WorkManager.initialize(application, Configuration.Builder().build())
    }
  }

  private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val subscriptionMutex = Mutex()

  internal val isMigrating: AtomicBoolean = AtomicBoolean(false)

  internal val sharedPreferencesHelper = SharedPreferencesHelper(application)
  private val apiService = ApiService.fromConfig(config)
  internal val apiRepository = ApiRepository(application, apiService, sharedPreferencesHelper, config)
  internal val uploadDelegate = UploadDelegate(apiRepository)
  internal val uploadManager = UploadManager(application, sharedPreferencesHelper)
  internal val pushNotificationsDelegate = PushNotificationDelegate(sharedPreferencesHelper, apiRepository, uploadManager)

  private val liveActivityPersistence: LiveActivityPersistence? by lazy {
    if (Build.VERSION.SDK_INT >= 36) LiveActivityPersistence(application) else null
  }

  private val liveActivityManager: LiveActivityManager? by lazy {
    liveActivityPersistence?.let { LiveActivityManager(it) }
  }

  internal val liveActivityHandler: LiveActivityHandler? by lazy {
    val manager = liveActivityManager ?: return@lazy null
    LiveActivityHandler(
      context = application,
      scope = sdkScope,
      manager = manager,
      apiRepository = apiRepository,
      onEvent = { eventType, laId, _, _, liveDataVersion ->
        // Map internal LA lifecycle events to the backend statistics enum
        // (started / closed / clicked / clicked_1 / clicked_2) and report them
        // to the dedicated live-notification events endpoint.
        val statisticsType =
          when (eventType) {
            "la.started" -> "started"
            "la.clicked" -> "clicked"
            "la.clicked_1" -> "clicked_1"
            "la.clicked_2" -> "clicked_2"
            "la.dismissed" -> "closed"
            else -> null // la.ended not reported (no backend enum value)
          }
        if (statisticsType != null) {
          sdkScope.launch {
            runCatching {
              apiRepository.sendLiveActivityEvent(
                liveNotificationId = laId,
                eventType = statisticsType,
                liveDataVersion = liveDataVersion,
                subscriberId = getSubscriberId().orEmpty(),
              )
            }.onFailure { logError("Failed to send LA statistics event $statisticsType for $laId", it) }
          }
        }
      },
    )
  }

  init {
    if (Build.VERSION.SDK_INT >= 36) {
      liveActivityManager?.restoreFromPersistence()
    }
  }

  init {
    val platformType = getPlatformType()
    val startupMessage = "PushNotifications SDK $VERSION initialized (project id: ${config.projectId}, platform: $platformType)"
    println(startupMessage)

    createNotificationChannel(application)
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
   * This method enqueues the subscription request and returns immediately.
   *
   * If notifications are disabled or migration is in progress, the request is ignored.
   */
  fun subscribe() {
    if (!areNotificationsEnabled()) {
      return logError("Notifications disabled! Subscriber registration canceled")
    }

    sdkScope.launch {
      subscriptionMutex.withLock {
        if (isMigrating.get()) {
          return@withLock logError("Migration in progress")
        }
        sharedPreferencesHelper.isSubscribed = true
        uploadManager.sendRegister(null)
      }
    }
  }

  /**
   * Unsubscribes the device from notifications.
   *
   * This method enqueues the unsubscription request and returns immediately.
   *
   * If migration is in progress, the request is ignored.
   */
  fun unsubscribe() {
    sdkScope.launch {
      subscriptionMutex.withLock {
        if (isMigrating.get()) {
          return@withLock logError("Migration in progress")
        }

        uploadManager.sendUnregister()
        sharedPreferencesHelper.isSubscribed = false
      }
    }
  }

  /**
   * Subscribes the device to notifications immediately.
   *
   * If notifications are disabled or migration is in progress, an [IllegalStateException] is thrown.
   *
   * @throws IllegalStateException
   */
  suspend fun subscribeNow() {
    check(areNotificationsEnabled()) {
      "Notifications disabled! Subscriber registration canceled"
    }

    withContext(Dispatchers.IO) {
      subscriptionMutex.withLock {
        check(!isMigrating.get()) {
          "Migration in progress"
        }

        apiRepository.registerToken(null)
        sharedPreferencesHelper.isSubscribed = true
      }
    }
  }

  /**
   * Unsubscribes the device from notifications immediately.
   *
   * If migration is in progress, an [IllegalStateException] is thrown.
   */
  suspend fun unsubscribeNow() {
    withContext(Dispatchers.IO) {
      subscriptionMutex.withLock {
        check(!isMigrating.get()) {
          "Migration in progress"
        }

        apiRepository.unregisterSubscriber()
        sharedPreferencesHelper.isSubscribed = false
      }
    }
  }

  /**
   * Subscribes the device to notifications immediately.
   *
   * Java-friendly wrapper for [subscribeNow].
   *
   * @returns [CompletableFuture]
   */
  fun subscribeNowFuture(): CompletableFuture<Void?> =
    sdkScope.future {
      subscribeNow()
      null
    }

  /**
   * Unsubscribes the device from notifications immediately.
   *
   * Java-friendly wrapper for [unsubscribeNow].
   *
   * @returns [CompletableFuture]
   */
  fun unsubscribeNowFuture(): CompletableFuture<Void?> =
    sdkScope.future {
      unsubscribeNow()
      null
    }

  /**
   * Migrates the current subscriber to a different project.
   *
   * This method:
   * - unregisters the subscriber in the old project
   * - registers the subscriber in the new project
   * - initializes the SDK with the new project
   *
   * During migration, subscription operations are blocked.
   *
   * WARNING: after migration use the object returned by this function
   * instead of the previous one.
   *
   * @param newProjectId project id to which we are switching
   * @param newApiKey project api key
   *
   * @return [CompletableFuture] with the new [PushNotifications] instance
   *
   * @throws IllegalStateException if notifications are disabled or migration is in progress.
   */
  fun migrateToNewProject(
    newProjectId: String,
    newApiKey: String,
  ): CompletableFuture<PushNotifications> {
    require(Config.isProjectIdFormatValid(newProjectId)) {
      "Invalid project ID format"
    }

    require(Config.isApiKeyFormatValid(newApiKey)) {
      "Invalid API key format"
    }

    check(areNotificationsEnabled()) {
      "Notifications disabled! Subscriber registration canceled"
    }

    check(isMigrating.compareAndSet(false, true)) {
      "Migration is already in progress"
    }

    return sdkScope.future {
      try {
        subscriptionMutex.withLock {
          uploadManager.cancelAllJobs()

          apiRepository.migrateSubscriber(
            newProjectId = newProjectId,
            newApiKey = newApiKey,
          )

          reinitialize(
            application = application,
            config =
              Config.create(
                projectId = newProjectId,
                apiKey = newApiKey,
                isDebug = config.isDebug,
                apiUrl = config.apiUrl,
              ),
          ).apply {
            notificationClickHandler = this@PushNotifications.notificationClickHandler
            invalidProjectIdHandler = this@PushNotifications.invalidProjectIdHandler
            defaultIsSubscribed = this@PushNotifications.defaultIsSubscribed
          }
        }
      } finally {
        isMigrating.set(false)
      }
    }
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

  /**
   * Checks whether Live Activities are supported on this device.
   * Requires API 36+ (Android 16) for ProgressStyle notifications.
   */
  fun isLiveActivitiesSupported(): Boolean = Build.VERSION.SDK_INT >= 36

  /**
   * Returns the list of currently active live activities.
   * Returns empty list on API < 36.
   */
  fun getActiveLiveActivities(): List<LiveActivity> = liveActivityManager?.getActiveActivities() ?: emptyList()

  /**
   * Checks whether a specific live activity is currently active.
   * Returns false on API < 36.
   */
  fun isLiveActivityActive(id: String): Boolean = liveActivityManager?.isActivityActive(id) ?: false

  /**
   * Simulates a Live Activity push for testing purposes. No-op on API < 36.
   *
   * Pass a data map matching the Live Activity push payload format.
   */
  fun simulateLiveActivityPush(data: Map<String, String>) {
    liveActivityHandler?.handlePush(data)
  }

  /**
   * Subscribes this device to a backend live notification (Live Activity) so it
   * starts receiving its push updates.
   *
   * The device must already be a registered push subscriber (call [subscribe]
   * first). The returned future resolves to the backend LA subscriber id, which
   * is also persisted so [unsubscribeFromLiveActivity] can be called later
   * without tracking it yourself.
   *
   * @param liveNotificationId backend id of the live notification to follow.
   * @return future with the assigned LA subscriber id.
   */
  fun subscribeToLiveActivity(liveNotificationId: String): CompletableFuture<String> =
    sdkScope.future {
      val laSubscriberId = apiRepository.subscribeToLiveActivity(liveNotificationId)
      sharedPreferencesHelper.setLiveActivitySubscriberId(liveNotificationId, laSubscriberId)
      // Catch up: render the current state for subscribers that joined after the
      // `start` push was already delivered (no-op if the LA isn't live yet).
      catchUpLiveActivity(liveNotificationId)
      laSubscriberId
    }

  /**
   * Unsubscribes this device from a backend live notification it previously
   * subscribed to via [subscribeToLiveActivity]. Fails if the device is not
   * subscribed to it.
   *
   * @param liveNotificationId backend id of the live notification to leave.
   */
  fun unsubscribeFromLiveActivity(liveNotificationId: String): CompletableFuture<Void?> =
    sdkScope.future {
      val laSubscriberId = sharedPreferencesHelper.getLiveActivitySubscriberId(liveNotificationId)
      check(laSubscriberId.isNotEmpty()) {
        "Not subscribed to live notification $liveNotificationId"
      }
      apiRepository.unsubscribeFromLiveActivity(liveNotificationId, laSubscriberId)
      sharedPreferencesHelper.removeLiveActivitySubscriberId(liveNotificationId)
      null
    }

  /**
   * Returns the persisted LA subscriber id for a live notification, or empty
   * string if this device is not subscribed to it.
   */
  fun getLiveActivitySubscriberId(liveNotificationId: String): String =
    sharedPreferencesHelper.getLiveActivitySubscriberId(liveNotificationId)

  /**
   * Fetch the current live notification state and feed it through the render
   * pipeline as a synthetic `start`, so a late subscriber sees the running
   * activity without waiting for the next update push.
   */
  private suspend fun catchUpLiveActivity(liveNotificationId: String) {
    val handler = liveActivityHandler ?: return
    val json = apiRepository.fetchLiveActivity(liveNotificationId) ?: return
    val envelope = LiveActivityPayloadParser.buildCatchUpEnvelope(json) ?: return
    handler.handlePush(envelope)
  }

  /**
   * Handles a Live Activity notification click when the app is launched or
   * resumed from a tap. Call from `Activity.onCreate()` / `Activity.onNewIntent()`
   * alongside [handleBackgroundNotificationClick].
   *
   * Reports the click analytics event and, unless [openDeepLink] is false, opens
   * the carried deep link through [notificationClickHandler] (same routing as
   * regular push clicks).
   *
   * @return the deep link if this was a Live Activity click, `null` otherwise.
   */
  @JvmOverloads
  fun handleLiveActivityClick(
    intent: Intent?,
    openDeepLink: Boolean = true,
  ): String? {
    val laId = intent?.getStringExtra(LiveActivityHandler.EXTRA_LIVE_ACTIVITY_ID) ?: return null
    val deepLink = intent.getStringExtra(LiveActivityHandler.EXTRA_DEEP_LINK)
    val actionIndex = intent.getIntExtra(LiveActivityHandler.EXTRA_ACTION_INDEX, -1)

    liveActivityHandler?.handleClick(laId, actionIndex)
    intent.removeExtra(LiveActivityHandler.EXTRA_LIVE_ACTIVITY_ID)
    intent.removeExtra(LiveActivityHandler.EXTRA_ACTION_INDEX)

    if (openDeepLink && !deepLink.isNullOrBlank()) {
      notificationClickHandler(application, deepLink, Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return deepLink
  }
}

typealias NotificationClickHandler = (context: Context, url: String, overrideFlags: Int) -> Unit

typealias InvalidProjectIdHandler = (pushProjectId: String, pushSubscriberId: String, currentProjectId: String) -> Unit
