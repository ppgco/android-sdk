package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Intent
import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.flow.StateFlow
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

  internal fun notifyError(throwable: Throwable) {
    callbacks.notifyError(throwable)
  }

  @JvmStatic
  val liveActivities: LiveActivities
    get() = requireRuntime().liveActivities

  @JvmStatic
  fun getCustomClickIntentFlags(): Int = requireRuntime().customClickIntentFlags

  @JvmStatic
  fun isInitialized(): Boolean = runtime != null

  /**
   * Initializes the PushNotifications SDK using configuration defined in AndroidManifest.xml.
   *
   * Calling this method again with the same configuration has no effect. To move an initialized
   * SDK to another project, use [switchProject].
   *
   * @throws IllegalStateException if required manifest values are missing or the SDK is already
   * initialized with a different configuration.
   */
  @JvmStatic
  fun initialize(application: Application): PushNotifications = initialize(application, ManifestConfigProvider(application).provide())

  /**
   * Initializes the PushNotifications SDK using an explicit [Config] instance.
   *
   * Calling this method again with an equal configuration has no effect. To move an initialized
   * SDK to another project, use [switchProject].
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
        PushNotificationsRuntime(application, config, callbacks).also {
          runtime = it
          it.start()
        }
      } else {
        check(activeRuntime.config == config) {
          "PushNotifications SDK is already initialized with a different configuration. " +
            "Use PushNotifications.switchProject() to move it to another project."
        }
      }

      this
    }

  /**
   * Moves the SDK to the project described by [config].
   *
   * The switch is local and does not need network. The previous project's subscriber is removed
   * and, if the user requested a subscription and notifications are enabled, the device is
   * subscribed to the new project, both in the background. Handlers and callbacks stay in place.
   *
   * The SDK does not remember [config] across app restarts - initialize it with the selected
   * configuration on the next start.
   *
   * @throws IllegalStateException if the SDK is not initialized.
   */
  @JvmSynthetic
  suspend fun switchProject(config: Config) {
    lifecycleMutex.withLock {
      val activeRuntime = requireRuntime()
      if (activeRuntime.config == config) return

      activeRuntime.release()
      PushNotificationsRuntime(activeRuntime.application, config, callbacks).also {
        runtime = it
        it.start()
      }
    }
  }

  /**
   * Java-friendly wrapper for [switchProject].
   */
  @JvmStatic
  fun switchProjectAsync(config: Config): CompletableFuture<Void?> =
    asyncScope.future {
      switchProject(config)
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
   * The handler may be configured before [initialize] and survives [switchProject]. Pass `null` to
   * restore the default handler. The SDK invokes the handler on the main thread.
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
   * [switchProject]. Pass `null` to restore the default handler, which logs the mismatch. The SDK
   * invokes the handler on the main thread.
   */
  @JvmStatic
  fun setInvalidProjectIdHandler(handler: InvalidProjectIdHandler?) {
    callbacks.invalidProjectIdHandler = handler ?: DefaultInvalidProjectIdHandler()
  }

  /**
   * Sets the process-wide SDK error callback.
   *
   * The callback may be configured before [initialize], survives [switchProject], and is invoked on
   * the main thread. Pass `null` to disable it.
   */
  @JvmStatic
  fun setErrorCallback(callback: PushNotificationsErrorCallback?) {
    callbacks.errorCallback = callback
  }

  @JvmStatic
  fun getProjectId(): String = requireRuntime().getProjectId()

  @JvmStatic
  fun getApiKey(): String = requireRuntime().getApiKey()

  /**
   * Returns whether the device is registered in PushPushGo.
   *
   * The status is independent of the user's choice and the notification permission: e.g. after an
   * offline [unsubscribe] it stays [SubscriptionStatus.Subscribed] until the device is removed from
   * PushPushGo. Use [isSubscriptionRequested] for the user's choice.
   */
  @JvmStatic
  fun getSubscriptionStatus(): SubscriptionStatus = requireRuntime().getSubscriptionStatus()

  /**
   * Returns the user's latest choice: `true` after [subscribe], `false` after [unsubscribe].
   *
   * Use it, together with [areNotificationsEnabled], for a notifications toggle in your app.
   */
  @JvmStatic
  fun isSubscriptionRequested(): Boolean = requireRuntime().isSubscriptionRequested()

  /**
   * The subscription status as a flow. Emits the current status and then every registration
   * change, including the ones the SDK makes in the background.
   *
   * The status is [SubscriptionStatus.Unsubscribed] until [initialize].
   */
  @get:JvmSynthetic
  val subscriptionStatusFlow: StateFlow<SubscriptionStatus>
    get() = callbacks.subscriptionStatusFlow

  /**
   * Sets the subscription status callback, the Java alternative to [subscriptionStatusFlow].
   * Receives the current status right away and then every registration change, on the main thread.
   *
   * The status is [SubscriptionStatus.Unsubscribed] until [initialize]. Pass `null` to remove the
   * callback.
   */
  @JvmStatic
  fun setSubscriptionStatusCallback(callback: SubscriptionStatusCallback?) {
    callbacks.subscriptionStatusCallback = callback
  }

  /**
   * Returns whether the device is registered in PushPushGo, i.e. [getSubscriptionStatus] is
   * [SubscriptionStatus.Subscribed]. Do not use it for the user's choice - see
   * [isSubscriptionRequested].
   */
  @JvmStatic
  fun isSubscribed(): Boolean = requireRuntime().isSubscribed()

  @JvmStatic
  fun getSubscriberId(): String? = requireRuntime().getSubscriberId()

  @JvmStatic
  fun getPushToken(): String? = requireRuntime().getPushToken()

  /**
   * Subscribes the device to notifications.
   *
   * Stores the user's choice right away, also without network, and registers the device in the
   * background, retrying once the network is available. Failures are reported to the error
   * callback. The device may not be registered yet when this method returns - follow
   * [subscriptionStatusFlow] (Java: [setSubscriptionStatusCallback]) to know when it is.
   *
   * Request the notification permission before calling this method.
   *
   * @throws IllegalStateException if notifications are disabled, without changing
   * [isSubscriptionRequested].
   */
  @JvmStatic
  fun subscribe() {
    requireRuntime().subscribe()
  }

  /**
   * Unsubscribes the device from notifications.
   *
   * Stores the user's choice right away, also without network, and regular notifications stop
   * being displayed. The subscriber is removed in the background; until then
   * [getSubscriptionStatus] keeps reporting the device as registered.
   *
   * Call it whenever the user turns notifications off in your app, whatever the current status.
   */
  @JvmStatic
  fun unsubscribe() {
    requireRuntime().unsubscribe()
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
   * Handles a PushPushGo notification click, including Live Activity notifications,
   * when the application is launched or resumed from the background.
   */
  @JvmStatic
  @JvmOverloads
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
   *
   * Send beacons once the device is subscribed: on [SubscriptionStatus.Subscribed] or when
   * [isSubscribed] returns `true`. Beacons sent before that, or after [unsubscribe], are discarded.
   * [unsubscribe] and [switchProject] also drop beacons that are still queued. The SDK sends beacons
   * in the background, in order, and retries them while offline.
   */
  @JvmStatic
  fun sendBeacon(beacon: Beacon) {
    requireRuntime().sendBeacon(beacon)
  }

  @JvmStatic
  fun getPushSubscriptionProvider(): PushSubscriptionProvider = requireRuntime().getPushSubscriptionProvider()

  internal suspend fun reconcileSubscription() {
    runtime?.reconcile()
  }

  @VisibleForTesting
  internal suspend fun reset() {
    lifecycleMutex.withLock {
      runtime?.release()
      runtime = null
      callbacks.publishSubscriptionStatus(SubscriptionStatus.Unsubscribed)
    }
  }

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
