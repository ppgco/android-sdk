package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Context
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
import com.pushpushgo.sdk.push.work.UploadDelegate
import com.pushpushgo.sdk.push.work.UploadManager
import java.util.concurrent.CompletableFuture

object PushNotifications {
  const val VERSION = "4.0.0"

  internal const val TAG = "[PushPushGo:PushNotifications]"

  @Volatile
  private var runtime: PushNotificationsRuntime? = null

  internal val config: Config
    get() = requireRuntime().config

  internal val sharedPreferencesHelper: SharedPreferencesHelper
    get() = requireRuntime().sharedPreferencesHelper

  internal val apiRepository: ApiRepository
    get() = requireRuntime().apiRepository

  internal val uploadDelegate: UploadDelegate
    get() = requireRuntime().uploadDelegate

  internal val uploadManager: UploadManager
    get() = requireRuntime().uploadManager

  internal val pushNotificationsDelegate: PushNotificationDelegate
    get() = requireRuntime().pushNotificationsDelegate

  val liveActivities: LiveActivities
    get() = requireRuntime().liveActivities

  val notificationClickHandler: NotificationClickHandler
    get() = requireRuntime().notificationClickHandler

  val invalidProjectIdHandler: InvalidProjectIdHandler
    get() = requireRuntime().invalidProjectIdHandler

  val customClickIntentFlags: Int
    get() = requireRuntime().customClickIntentFlags

  val defaultIsSubscribed: Boolean
    get() = requireRuntime().defaultIsSubscribed

  val errorCallback: ((Throwable) -> Unit)?
    get() = requireRuntime().errorCallback

  @JvmStatic
  fun isInitialized(): Boolean = runtime != null

  /**
   * Initializes the PushNotifications SDK using configuration defined in AndroidManifest.xml.
   *
   * Calling this method again with the same configuration has no effect. If the SDK is already
   * initialized with a different configuration, call [deactivate] before initializing it again.
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
   * initialized with a different configuration, call [deactivate] before initializing it again.
   *
   * @throws IllegalStateException if the SDK is already initialized with a different configuration.
   */
  @JvmStatic
  fun initialize(
    application: Application,
    config: Config,
  ): PushNotifications {
    synchronized(this) {
      val activeRuntime = runtime
      if (activeRuntime == null) {
        runtime = PushNotificationsRuntime(application, config)
      } else {
        check(activeRuntime.config == config) {
          "PushNotifications SDK is already initialized with a different configuration. " +
            "Call PushNotifications.deactivate() before initializing it again."
        }
      }
    }

    return this
  }

  /**
   * Deactivates the PushNotifications SDK, allowing it to be initialized again.
   */
  @JvmStatic
  fun deactivate() {
    synchronized(this) {
      runtime?.deactivate()
      runtime = null
    }
  }

  fun setCustomClickIntentFlags(flags: Int) {
    requireRuntime().setCustomClickIntentFlags(flags)
  }

  fun setDefaultIsSubscribed(isSubscribed: Boolean) {
    requireRuntime().setDefaultIsSubscribed(isSubscribed)
  }

  fun setNotificationClickHandler(handler: NotificationClickHandler) {
    requireRuntime().setNotificationClickHandler(handler)
  }

  fun setInvalidProjectIdHandler(handler: InvalidProjectIdHandler) {
    requireRuntime().setInvalidProjectIdHandler(handler)
  }

  fun setErrorCallback(callback: ((Throwable) -> Unit)?) {
    requireRuntime().setErrorCallback(callback)
  }

  fun getProjectId(): String = requireRuntime().getProjectId()

  fun getApiKey(): String = requireRuntime().getApiKey()

  fun isSubscribed(): Boolean = requireRuntime().isSubscribed()

  fun getSubscriberId(): String? = requireRuntime().getSubscriberId()

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
  fun subscribeAsync(): CompletableFuture<Void?> = requireRuntime().subscribeAsync()

  /**
   * Unsubscribes the device from notifications asynchronously.
   *
   * Java-friendly wrapper for [unsubscribe].
   *
   * @returns [CompletableFuture]
   */
  fun unsubscribeAsync(): CompletableFuture<Void?> = requireRuntime().unsubscribeAsync()

  /**
   * Checks whether the given notification intent belongs to PushPushGo.
   */
  fun isPushPushGoNotification(notificationIntent: Intent?): Boolean = notificationIntent?.hasExtra("project") == true

  /**
   * Checks whether the given notification data belongs to PushPushGo.
   */
  fun isPushPushGoNotification(notificationData: Map<String, String>): Boolean = notificationData.containsKey("project")

  /**
   * Retrieves PushPushGo notification details from the given intent.
   */
  fun getNotificationDetails(notificationIntent: Intent?): PushPushGoNotification? =
    deserializeNotificationData(notificationIntent?.extras)?.mapToDto()

  /**
   * Retrieves PushPushGo notification details from the given data payload.
   */
  fun getNotificationDetails(notificationData: Map<String, String>): PushPushGoNotification? =
    deserializeNotificationData(notificationData.mapToBundle())?.mapToDto()

  /**
   * Handles a PushPushGo notification click when the application is launched
   * or resumed from the background.
   */
  fun handleBackgroundNotificationClick(
    intent: Intent?,
    overrideFlags: Int = Intent.FLAG_ACTIVITY_NEW_TASK,
  ) {
    requireRuntime().handleBackgroundNotificationClick(intent, overrideFlags)
  }

  fun areNotificationsEnabled(): Boolean = requireRuntime().areNotificationsEnabled()

  fun createBeacon(): BeaconBuilder = requireRuntime().createBeacon()

  fun getPushSubscriptionProvider(): PushSubscriptionProvider = requireRuntime().getPushSubscriptionProvider()

  private fun requireRuntime(): PushNotificationsRuntime = checkNotNull(runtime) { "PushNotifications SDK is not initialized" }
}

typealias NotificationClickHandler = (context: Context, url: String, overrideFlags: Int) -> Unit

typealias InvalidProjectIdHandler = (pushProjectId: String, pushSubscriberId: String, currentProjectId: String) -> Unit
