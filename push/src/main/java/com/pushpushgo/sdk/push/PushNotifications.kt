package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Intent
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.core.internal.ManifestConfigProvider
import com.pushpushgo.sdk.push.data.mapToDto
import com.pushpushgo.sdk.push.dto.PushPushGoNotification
import com.pushpushgo.sdk.push.liveactivity.LiveActivities
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.push.deserializeNotificationData
import com.pushpushgo.sdk.push.utils.mapToBundle
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CompletableFuture

object PushNotifications {
  const val VERSION = "4.0.0"

  internal const val TAG = "[PushPushGo:PushNotifications]"

  @Volatile
  private var runtime: PushNotificationsRuntime? = null

  private val callbacks = PushNotificationsCallbacks()
  private val lifecycleMutex = Mutex()
  private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  internal val config: Config
    get() = requireRuntime().config

  internal val sharedPreferencesHelper: SharedPreferencesHelper
    get() = requireRuntime().sharedPreferencesHelper

  internal val apiRepository: ApiRepository
    get() = requireRuntime().apiRepository

  internal val uploadManager: UploadManager
    get() = requireRuntime().uploadManager

  internal val pushNotificationsDelegate: PushNotificationDelegate
    get() = requireRuntime().pushNotificationsDelegate

  internal val notificationClickHandler: NotificationClickHandler
    get() = callbacks.notificationClickHandler

  internal val invalidProjectIdHandler: InvalidProjectIdHandler
    get() = callbacks.invalidProjectIdHandler

  internal val errorCallback: PushNotificationsErrorCallback?
    get() = callbacks.errorCallback

  @JvmStatic
  val liveActivities: LiveActivities
    get() = requireRuntime().liveActivities

  @JvmStatic
  val customClickIntentFlags: Int
    get() = requireRuntime().customClickIntentFlags

  @JvmStatic
  fun isInitialized(): Boolean = runtime != null

  /**
   * Initializes the PushNotifications SDK using configuration defined in AndroidManifest.xml.
   *
   * Calling this method again with the same configuration has no effect. If the SDK is already
   * initialized with a different configuration, call [deinitialize] before initializing it again.
   *
   * @throws IllegalStateException if required manifest values are missing or the SDK is already
   * initialized with a different configuration.
   */
  @JvmStatic
  fun initialize(application: Application): PushNotifications = initialize(application, ManifestConfigProvider(application).provide())

  /**
   * Initializes the PushNotifications SDK using an explicit [Config] instance.
   *
   * Calling this method again with an equal configuration has no effect. If the SDK is already
   * initialized with a different configuration, call [deinitialize] before initializing it again.
   *
   * @throws IllegalStateException if the SDK is already initialized with a different configuration.
   */
  @JvmStatic
  fun initialize(
    application: Application,
    config: Config,
  ): PushNotifications =
    withLifecycleLock {
      val activeRuntime = runtime
      if (activeRuntime == null) {
        runtime = PushNotificationsRuntime(application, config, callbacks)
      } else {
        check(activeRuntime.config == config) {
          "PushNotifications SDK is already initialized with a different configuration. " +
            "Call PushNotifications.deinitialize() before initializing it again."
        }
      }

      this
    }

  /**
   * Unsubscribes the current project, clears its persisted state, and releases the SDK runtime.
   *
   * After this method completes, [initialize] may be called with another project configuration.
   */
  @JvmSynthetic
  suspend fun deinitialize() {
    lifecycleMutex.withLock {
      val activeRuntime = requireRuntime()
      activeRuntime.deinitialize()
      runtime = null
    }
  }

  /**
   * Java-friendly wrapper for [deinitialize].
   */
  @JvmStatic
  fun deinitializeAsync(): CompletableFuture<Void?> =
    asyncScope.future {
      deinitialize()
      null
    }

  /**
   * Sets the intent flags used when opening a notification.
   *
   * This setting requires an initialized SDK.
   */
  @JvmStatic
  fun setCustomClickIntentFlags(flags: Int) {
    withLifecycleLock {
      requireRuntime().setCustomClickIntentFlags(flags)
    }
  }

  /**
   * Sets the process-wide notification click handler.
   *
   * The handler may be configured before [initialize] and survives [deinitialize]. Pass `null` to
   * restore the default handler.
   */
  @JvmStatic
  fun setNotificationClickHandler(handler: NotificationClickHandler?) {
    callbacks.notificationClickHandler = handler ?: DefaultNotificationClickHandler()
  }

  /**
   * Sets the handler invoked when a received notification belongs to a different project than the one
   * currently initialized.
   *
   * The handler is process-wide, may be configured before [initialize], and survives
   * [deinitialize]. Pass `null` to restore the default handler, which logs the mismatch.
   */
  @JvmStatic
  fun setInvalidProjectIdHandler(handler: InvalidProjectIdHandler?) {
    callbacks.invalidProjectIdHandler = handler ?: DefaultInvalidProjectIdHandler()
  }

  /**
   * Sets the process-wide SDK error callback.
   *
   * The callback may be configured before [initialize], survives [deinitialize]. Pass `null` to disable it.
   */
  @JvmStatic
  fun setErrorCallback(callback: PushNotificationsErrorCallback?) {
    callbacks.errorCallback = callback
  }

  @JvmStatic
  fun getProjectId(): String = requireRuntime().getProjectId()

  @JvmStatic
  fun getApiKey(): String = requireRuntime().getApiKey()

  @JvmStatic
  fun isSubscribed(): Boolean = requireRuntime().isSubscribed()

  @JvmStatic
  fun getSubscriberId(): String? = requireRuntime().getSubscriberId()

  @JvmStatic
  fun getPushToken(): String? = requireRuntime().getPushToken()

  /**
   * Subscribes the device to notifications.
   *
   * If notifications are disabled, an [IllegalStateException] is thrown.
   *
   * @throws IllegalStateException
   */
  @JvmSynthetic
  suspend fun subscribe() {
    requireRuntime().subscribe()
  }

  /**
   * Unsubscribes the device from notifications.
   */
  @JvmSynthetic
  suspend fun unsubscribe() {
    requireRuntime().unsubscribe()
  }

  /**
   * Subscribes the device to notifications asynchronously.
   *
   * Java-friendly wrapper for [subscribe].
   *
   * @returns [CompletableFuture]
   */
  @JvmStatic
  fun subscribeAsync(): CompletableFuture<Void?> =
    asyncScope.future {
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
  @JvmStatic
  fun unsubscribeAsync(): CompletableFuture<Void?> =
    asyncScope.future {
      unsubscribe()
      null
    }

  /**
   * Checks whether the given notification intent belongs to PushPushGo.
   */
  @JvmStatic
  fun isPushPushGoNotification(notificationIntent: Intent?): Boolean = notificationIntent?.hasExtra("project") == true

  /**
   * Checks whether the given notification data belongs to PushPushGo.
   */
  @JvmStatic
  fun isPushPushGoNotification(notificationData: Map<String, String>): Boolean = notificationData.containsKey("project")

  /**
   * Retrieves PushPushGo notification details from the given intent.
   */
  @JvmStatic
  fun getNotificationDetails(notificationIntent: Intent?): PushPushGoNotification? =
    deserializeNotificationData(notificationIntent?.extras)?.mapToDto()

  /**
   * Retrieves PushPushGo notification details from the given data payload.
   */
  @JvmStatic
  fun getNotificationDetails(notificationData: Map<String, String>): PushPushGoNotification? =
    deserializeNotificationData(notificationData.mapToBundle())?.mapToDto()

  /**
   * Handles a PushPushGo notification click when the application is launched
   * or resumed from the background.
   */
  @JvmStatic
  fun handleBackgroundNotificationClick(
    intent: Intent?,
    overrideFlags: Int = Intent.FLAG_ACTIVITY_NEW_TASK,
  ) {
    requireRuntime().handleBackgroundNotificationClick(intent, overrideFlags)
  }

  @JvmStatic
  fun areNotificationsEnabled(): Boolean = requireRuntime().areNotificationsEnabled()

  /**
   * Sends a beacon created with [BeaconBuilder].
   */
  @JvmStatic
  @JvmSynthetic
  suspend fun sendBeacon(beacon: Beacon) {
    requireRuntime().sendBeacon(beacon)
  }

  /**
   * Sends a beacon asynchronously.
   *
   * Java-friendly wrapper for [sendBeacon].
   */
  @JvmStatic
  fun sendBeaconAsync(beacon: Beacon): CompletableFuture<Void?> =
    asyncScope.future {
      sendBeacon(beacon)
      null
    }

  @JvmStatic
  fun getPushSubscriptionProvider(): PushSubscriptionProvider = requireRuntime().getPushSubscriptionProvider()

  private fun requireRuntime(): PushNotificationsRuntime = checkNotNull(runtime) { "PushNotifications SDK is not initialized" }

  private inline fun <T> withLifecycleLock(block: () -> T): T {
    check(lifecycleMutex.tryLock()) {
      "PushNotifications lifecycle mutation is in progress"
    }

    return try {
      block()
    } finally {
      lifecycleMutex.unlock()
    }
  }
}
