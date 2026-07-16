package com.pushpushgo.sdk.push.liveactivity

import android.app.Application
import android.content.Intent
import androidx.annotation.RestrictTo
import com.pushpushgo.sdk.push.PushNotificationsCallbacks
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivity
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

/**
 * Entry point for Live Activities functionality.
 */
class LiveActivities internal constructor(
  private val application: Application,
  private val scope: CoroutineScope,
  apiRepository: ApiRepository,
  sharedPreferencesHelper: SharedPreferencesHelper,
  getSubscriberId: () -> String?,
  callbacks: PushNotificationsCallbacks,
) {
  private val controller =
    LiveActivityController(
      application = application,
      scope = scope,
      apiRepository = apiRepository,
      sharedPref = sharedPreferencesHelper,
      getSubscriberId = getSubscriberId,
      callbacks = callbacks,
    )

  internal val handler: LiveActivityHandler?
    get() = controller.handler

  internal fun clearProjectData() {
    controller.clearProjectData()
  }

  init {
    controller.restoreFromPersistence()
  }

  /** Checks whether Live Activities are supported on this device (API 36+). */
  fun isSupported(): Boolean = controller.isSupported()

  /**
   * Returns the currently active Live Activities.
   * Returns an empty list on API < 36.
   */
  fun getActiveActivities(): List<LiveActivity> = controller.getActiveActivities()

  /**
   * Checks whether a specific Live Activity is currently active.
   * Returns `false` on API < 36.
   */
  fun isActive(id: String): Boolean = controller.isActive(id)

  /**
   * Simulates a Live Activity push for SDK testing. No-op on API < 36.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  fun simulatePush(data: Map<String, String>) {
    controller.simulatePush(data)
  }

  /**
   * Subscribes this device to a backend Live Activity.
   *
   * The device must already be a registered push subscriber. Returns the backend
   * Live Activity subscriber ID, which is persisted for later [unsubscribe] calls.
   *
   * @param liveNotificationId backend ID of the Live Activity to follow
   * @return the assigned Live Activity subscriber ID
   */
  @JvmSynthetic
  suspend fun subscribe(liveNotificationId: String): String = controller.subscribe(liveNotificationId)

  /**
   * Subscribes this device to a backend Live Activity.
   *
   * Java-friendly wrapper for [subscribe].
   *
   * @param liveNotificationId backend ID of the Live Activity to follow
   * @return future with the assigned Live Activity subscriber ID
   */
  fun subscribeAsync(liveNotificationId: String): CompletableFuture<String> =
    scope.future {
      subscribe(liveNotificationId)
    }

  /**
   * Unsubscribes this device from a backend Live Activity previously followed via
   * [subscribe]. Fails if the device is not subscribed to it.
   *
   * @param liveNotificationId backend ID of the Live Activity to leave
   */
  @JvmSynthetic
  suspend fun unsubscribe(liveNotificationId: String) {
    controller.unsubscribe(liveNotificationId)
  }

  /**
   * Unsubscribes this device from a backend Live Activity previously followed via
   * [subscribe]. Fails if the device is not subscribed to it.
   *
   * Java-friendly wrapper for [unsubscribe].
   *
   * @param liveNotificationId backend ID of the Live Activity to leave
   */
  fun unsubscribeAsync(liveNotificationId: String): CompletableFuture<Void?> =
    scope.future {
      unsubscribe(liveNotificationId)
      null
    }

  /**
   * Returns the persisted Live Activity subscriber ID, or an empty string if this
   * device is not subscribed to it.
   */
  fun getSubscriberId(liveNotificationId: String): String = controller.getSubscriberId(liveNotificationId)

  /**
   * Handles a Live Activity notification click. Call from `Activity.onCreate()` or
   * `Activity.onNewIntent()` alongside background notification click handling.
   *
   * Reports click analytics and, unless [openDeepLink] is `false`, opens the carried
   * deep link through the configured notification click handler.
   *
   * @return the deep link, or `null` if this was not a Live Activity click
   */
  @JvmOverloads
  fun handleClick(
    intent: Intent?,
    openDeepLink: Boolean = true,
  ): String? = controller.handleClick(application, intent, openDeepLink)
}
