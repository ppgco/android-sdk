package com.pushpushgo.inappmessages

import android.app.Application
import android.util.Log
import com.pushpushgo.inappmessages.data.event.InAppMessageEvent
import com.pushpushgo.inappmessages.data.event.InAppMessageEventRepository
import com.pushpushgo.inappmessages.manager.InAppMessageManager
import com.pushpushgo.inappmessages.manager.InAppMessageManagerImpl
import com.pushpushgo.inappmessages.network.InAppEventApi
import com.pushpushgo.inappmessages.network.InAppListGetApi
import com.pushpushgo.inappmessages.network.RetrofitProvider
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistenceImpl
import com.pushpushgo.inappmessages.repository.InAppMessageRepositoryImpl
import com.pushpushgo.inappmessages.ui.InAppMessageDisplayer
import com.pushpushgo.inappmessages.ui.InAppMessageDisplayerImpl
import com.pushpushgo.inappmessages.ui.InAppUIController
import com.pushpushgo.inappmessages.utils.AutoCleanupManager
import com.pushpushgo.inappmessages.utils.DefaultPushNotificationSubscriber
import com.pushpushgo.inappmessages.utils.PushNotificationSubscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.Retrofit

class InAppMessagesSDK private constructor(
  private val application: Application,
  private val projectId: String,
  private val apiKey: String,
  private val debug: Boolean = false,
  private val baseUrl: String? = null,
  private var pushNotificationSubscriber: PushNotificationSubscriber = DefaultPushNotificationSubscriber(),
) {
  // --- Retrofit & APIs ---
  private val retrofit: Retrofit by lazy {
    RetrofitProvider.buildRetrofit(baseUrl ?: "https://api.pushpushgo.com/")
  }
  private val api: InAppListGetApi by lazy {
    retrofit.create(InAppListGetApi::class.java)
  }
  private val eventApi: InAppEventApi by lazy {
    retrofit.create(InAppEventApi::class.java)
  }
  private val eventRepository by lazy {
    InAppMessageEventRepository(eventApi, debug = debug)
  }

  internal suspend fun dispatchInAppEvent(
    action: String,
    inAppId: String,
  ) {
    try {
      eventRepository.sendEvent(
        token = apiKey,
        projectId = projectId,
        event = InAppMessageEvent(action = action, inApp = inAppId),
      )
    } catch (e: Exception) {
      if (debug) {
        Log.e(tag, "Failed to send in-app event", e)
      }
    }
  }

  private val tag = "InAppMessagesSDK"
  private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val manager: InAppMessageManager
  private val displayer: InAppMessageDisplayer
  private val uiController: InAppUIController
  private var autoCleanupManager: AutoCleanupManager? = null

  companion object {
    @Volatile
    private var INSTANCE: InAppMessagesSDK? = null

    @JvmStatic
    fun initialize(
      application: Application,
      projectId: String,
      apiKey: String,
      baseUrl: String? = null,
      debug: Boolean = false,
      pushNotificationSubscriber: PushNotificationSubscriber? = null,
    ): InAppMessagesSDK =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: InAppMessagesSDK(
          application,
          projectId,
          apiKey,
          debug,
          baseUrl,
          pushNotificationSubscriber ?: DefaultPushNotificationSubscriber(),
        ).also {
          INSTANCE = it
        }
      }

    @JvmStatic
    fun getInstance(): InAppMessagesSDK = INSTANCE ?: throw IllegalStateException("InAppMessagesSDK is not initialized!")
  }

  init {
    val repository = InAppMessageRepositoryImpl(api, projectId, apiKey)
    val persistence = InAppMessagePersistenceImpl(application)
    manager = InAppMessageManagerImpl(sdkScope, repository, persistence, application)
    displayer =
      InAppMessageDisplayerImpl(
        persistence,
        onMessageDismissed = { showActiveMessages() },
        onMessageEvent = { eventType, message, ctaIndex ->
          sdkScope.launch {
            when (eventType) {
              "show" -> dispatchInAppEvent("inapp.show", message.id)
              "close" -> dispatchInAppEvent("inapp.close", message.id)
              "cta" -> dispatchInAppEvent("inapp.cta.$ctaIndex", message.id)
            }
          }
        },
      )
    uiController = InAppUIController(application, manager, displayer)

    sdkScope.launch {
      manager.initialize()
    }
    uiController.start()

    autoCleanupManager =
      AutoCleanupManager(
        application = application,
        cleanupCallback = { cleanup() },
      )
    autoCleanupManager?.start()

    Log.d(tag, "InAppMessagesSDK initialized with automatic background cleanup")
  }

  /**
   * Cleans up resources used by the SDK
   * This is called automatically after app is in background for a prolonged period,
   * but can also be called manually from app's onDestroy()
   */
  private fun cleanup() {
    Log.d(tag, "Cleaning up InAppMessagesSDK resources")

    // Stop the auto-cleanup manager
    autoCleanupManager?.stop()
    autoCleanupManager = null

    uiController.stop()
    displayer.cancelPendingMessages()
    sdkScope.cancel()

    Log.d(tag, "InAppMessagesSDK resources cleaned up")
  }

  /**
   * Shows all in-app messages that should be displayed automatically:
   * - If currentRoute is null: shows all messages with trigger.type == APP_OPEN
   * - If currentRoute is not null: shows all messages with trigger.type == ROUTE and trigger.route == currentRoute, and all with trigger.type == APP_OPEN
   *
   * Call this once on app start (with currentRoute = null),
   * and on route/view change (with currentRoute = route name).
   */
  fun showActiveMessages(currentRoute: String? = null) {
    Log.d(tag, "Request to show active messages for route: ${currentRoute ?: "APP_OPEN"}")
    sdkScope.launch {
      manager.refreshActiveMessages(currentRoute)
    }
  }

  /**
   * Shows in-app messages for a custom trigger.
   * Only messages with trigger.type == CUSTOM and matching key (and value, if provided) will be shown.
   * Also doesn't cancel pending messages for APP_OPEN trigger.
   */
  fun showMessagesOnTrigger(
    key: String,
    value: String? = null,
  ) {
    Log.d(tag, "Request to show messages for custom trigger: $key")
    sdkScope.launch {
      val messageToShow = manager.trigger(key, value)
      if (messageToShow != null) {
        uiController.displayCustomMessage(messageToShow)
      }
    }
  }

  /**
   * Sets a handler for code actions from in-app messages.
   * When an in-app message with action type JS is clicked, the handler will be called with the given code.
   *
   * @param handler Function that takes a action button code string and processes it
   */
  fun setJsActionHandler(handler: (jsCall: String) -> Unit) {
    Log.d(tag, "Setting JS action handler")
    (displayer as? InAppMessageDisplayerImpl)?.setJsActionHandler(handler)
  }

  /**
   * Sets a custom implementation for handling subscription requests.
   * This will be called when an in-app message with a SUBSCRIBE action button is clicked.
   *
   * By default, this SDK attempts to use reflection to find and call the PushPushGo SDK.
   * You only need to provide a custom implementation if you're using a different push service
   * or have special requirements.
   *
   * @param subscriber The PushNotificationSubscriber implementation
   */
  fun setPushNotificationSubscriber(subscriber: PushNotificationSubscriber) {
    Log.d(tag, "Setting push notification subscriber")
    this.pushNotificationSubscriber = subscriber
    (displayer as? InAppMessageDisplayerImpl)?.setSubscriptionHandler(subscriber)
  }

  /**
   * Checks if the subscription bridge to the PushPushGo SDK is available.
   * Use this to determine if SUBSCRIBE action buttons will work automatically.
   *
   * @return true if the bridge is available, false otherwise
   */
  fun isSubscriptionBridgeAvailable(): Boolean =
    try {
      // Check if the bridge class exists
      Class.forName("com.pushpushgo.sdk.bridge.PushPushGoSubscriptionManager")
      true
    } catch (e: ClassNotFoundException) {
      Log.d(tag, "PushPushGo subscription bridge not available")
      false
    } catch (e: Exception) {
      Log.e(tag, "Error checking subscription bridge availability", e)
      false
    }
}
