package com.pushpushgo.sdk.push.liveactivity

import android.app.Application
import android.content.Intent
import com.pushpushgo.sdk.core.internal.NotificationPermissionProvider
import com.pushpushgo.sdk.push.PushNotificationsCallbacks
import com.pushpushgo.sdk.push.liveactivity.notification.LiveActivityNotificationChannel
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CompletableFuture

/**
 * Entry point for Live Activities functionality.
 */
class LiveActivities internal constructor(
  private val application: Application,
  private val scope: CoroutineScope,
  apiRepository: ApiRepository,
  sharedPreferencesHelper: SharedPreferencesHelper,
  callbacks: PushNotificationsCallbacks,
  private val operationMutex: Mutex,
  private val assertActive: () -> Unit,
) {
  private val controller =
    LiveActivityController(
      application = application,
      scope = scope,
      apiRepository = apiRepository,
      sharedPref = sharedPreferencesHelper,
      callbacks = callbacks,
    )

  internal val handler: LiveActivityHandler?
    get() = controller.handler

  init {
    controller.restoreFromPersistence()
  }

  /** Checks whether Live Activities are supported on this device (API 36+). */
  fun isSupported(): Boolean {
    assertActive()
    return controller.isSupported()
  }

  /**
   * Returns the currently active Live Activities.
   * Returns an empty list on API < 36.
   */
  fun getActiveLiveActivities(): List<LiveActivity> {
    assertActive()
    return controller.getActiveLiveActivities()
  }

  /**
   * Subscribes this device to a Live Activity.
   *
   * Returns the assigned Live Activity subscriber ID. The SDK keeps it for later
   * [unsubscribe] calls.
   *
   * Request the notification permission before calling this method.
   *
   * @param liveNotificationId ID of the Live Activity to follow
   * @return the assigned Live Activity subscriber ID
   * @throws IllegalStateException if notifications are disabled
   */
  @JvmSynthetic
  suspend fun subscribe(liveNotificationId: String): String =
    operationMutex.withLock {
      assertActive()

      val notificationsEnabled =
        NotificationPermissionProvider.canPostNotifications(application) &&
          LiveActivityNotificationChannel.isEnabled(application)
      check(notificationsEnabled) {
        "Cannot subscribe to Live Activity because notifications are disabled"
      }

      controller.subscribe(liveNotificationId)
    }

  /**
   * Subscribes this device to a Live Activity.
   *
   * Java-friendly wrapper for [subscribe].
   *
   * Request the notification permission before calling this method.
   *
   * @param liveNotificationId ID of the Live Activity to follow
   * @return future with the assigned Live Activity subscriber ID; it fails with
   * [IllegalStateException] if notifications are disabled
   */
  fun subscribeAsync(liveNotificationId: String): CompletableFuture<String> =
    scope.future {
      subscribe(liveNotificationId)
    }

  /**
   * Unsubscribes this device from a Live Activity previously followed via
   * [subscribe]. Does nothing if the device is not subscribed to it.
   *
   * @param liveNotificationId ID of the Live Activity to leave
   */
  @JvmSynthetic
  suspend fun unsubscribe(liveNotificationId: String) {
    operationMutex.withLock {
      assertActive()
      controller.unsubscribe(liveNotificationId)
    }
  }

  /**
   * Unsubscribes this device from a Live Activity previously followed via
   * [subscribe]. Does nothing if the device is not subscribed to it.
   *
   * Java-friendly wrapper for [unsubscribe].
   *
   * @param liveNotificationId ID of the Live Activity to leave
   */
  fun unsubscribeAsync(liveNotificationId: String): CompletableFuture<Void?> =
    scope.future {
      unsubscribe(liveNotificationId)
      null
    }

  /** Handles a Live Activity notification click. Returns `false` if [intent] is not one. */
  internal fun handleClick(
    intent: Intent?,
    overrideFlags: Int,
  ): Boolean {
    assertActive()
    return controller.handleClick(application, intent, overrideFlags)
  }
}
