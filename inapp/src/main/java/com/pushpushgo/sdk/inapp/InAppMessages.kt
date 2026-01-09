package com.pushpushgo.sdk.inapp

import android.app.Application
import android.util.Log
import com.pushpushgo.sdk.core.config.Config
import com.pushpushgo.sdk.core.config.ManifestConfigProvider
import com.pushpushgo.sdk.core.push.PushSubscriptionProvider
import com.pushpushgo.sdk.inapp.event.InAppMessageEvent
import com.pushpushgo.sdk.inapp.event.InAppMessageEventRepository
import com.pushpushgo.sdk.inapp.manager.InAppMessageManager
import com.pushpushgo.sdk.inapp.manager.InAppMessageManagerImpl
import com.pushpushgo.sdk.inapp.network.InAppEventApi
import com.pushpushgo.sdk.inapp.network.InAppListGetApi
import com.pushpushgo.sdk.inapp.network.RetrofitProvider
import com.pushpushgo.sdk.inapp.persistence.InAppMessagePersistenceImpl
import com.pushpushgo.sdk.inapp.repository.InAppMessageRepositoryImpl
import com.pushpushgo.sdk.inapp.ui.CustomCodeHandler
import com.pushpushgo.sdk.inapp.ui.InAppMessageDisplayer
import com.pushpushgo.sdk.inapp.ui.InAppMessageDisplayerImpl
import com.pushpushgo.sdk.inapp.ui.InAppUIController
import com.pushpushgo.sdk.inapp.ui.Route
import com.pushpushgo.sdk.inapp.ui.Trigger
import com.pushpushgo.sdk.inapp.utils.AutoCleanupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.Retrofit

class InAppMessages private constructor(
  private val application: Application,
  private val config: Config,
  private val pushSubscriptionProvider: PushSubscriptionProvider? = null,
  private val customCodeHandler: CustomCodeHandler? = null,
) {
  private val retrofit: Retrofit by lazy {
    RetrofitProvider.buildRetrofit(config.apiUrl)
  }
  private val api: InAppListGetApi by lazy {
    retrofit.create(InAppListGetApi::class.java)
  }
  private val eventApi: InAppEventApi by lazy {
    retrofit.create(InAppEventApi::class.java)
  }
  private val eventRepository by lazy {
    InAppMessageEventRepository(eventApi, debug = config.isDebug)
  }

  internal suspend fun dispatchInAppEvent(
    action: String,
    inAppId: String,
  ) {
    try {
      eventRepository.sendEvent(
        projectId = config.projectId,
        token = config.apiKey,
        event = InAppMessageEvent(action = action, inApp = inAppId),
      )
    } catch (e: Exception) {
      if (config.isDebug) {
        Log.e(TAG, "Failed to send in-app event", e)
      }
    }
  }

  private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val manager: InAppMessageManager
  private val displayer: InAppMessageDisplayer
  private val uiController: InAppUIController
  private var autoCleanupManager: AutoCleanupManager? = null

  companion object {
    internal const val TAG = "[PushPushGo:InAppMessages]"

    @Volatile
    private var INSTANCE: InAppMessages? = null

    @JvmStatic
    fun initialize(
      application: Application,
      pushSubscriptionProvider: PushSubscriptionProvider? = null,
    ): InAppMessages =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: InAppMessages(
          application = application,
          config = ManifestConfigProvider(application).provide(),
          pushSubscriptionProvider = pushSubscriptionProvider,
        ).also { INSTANCE = it }
      }

    @JvmStatic
    fun initialize(
      application: Application,
      config: Config,
      pushSubscriptionProvider: PushSubscriptionProvider? = null,
    ): InAppMessages =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: InAppMessages(
          application = application,
          config = config,
          pushSubscriptionProvider = pushSubscriptionProvider,
        ).also {
          INSTANCE = it
        }
      }

    @JvmStatic
    fun getInstance(): InAppMessages = INSTANCE ?: throw IllegalStateException("InAppMessages SDK is not initialized!")
  }

  init {
    val persistence = InAppMessagePersistenceImpl(application, config.isDebug)
    val repository = InAppMessageRepositoryImpl(api, config.projectId, config.apiKey, persistence, config.isDebug)
    manager =
      InAppMessageManagerImpl(
        scope = sdkScope,
        repository = repository,
        persistence = persistence,
        context = application,
        debug = config.isDebug,
        pushSubscriptionProvider = pushSubscriptionProvider,
      )
    displayer =
      InAppMessageDisplayerImpl(
        persistence = persistence,
        debug = config.isDebug,
        onMessageDismissed = {
          sdkScope.launch {
            manager.refreshActiveMessages(manager.getRoute())
          }
        },
        onMessageEvent = { eventType, message, ctaIndex ->
          sdkScope.launch {
            when (eventType) {
              "show" -> dispatchInAppEvent("inapp.show", message.id)
              "close" -> dispatchInAppEvent("inapp.close", message.id)
              "cta" -> dispatchInAppEvent("inapp.cta.$ctaIndex", message.id)
            }
          }
        },
        pushSubscriptionProvider = pushSubscriptionProvider,
        customCodeHandler = customCodeHandler,
      )
    uiController = InAppUIController(application, manager, displayer, config.isDebug)

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
  }

  /**
   * Cleans up resources used by the SDK
   * This is called automatically after app is in background for a prolonged period,
   * but can also be called manually from app's onDestroy()
   */
  private fun cleanup() {
    // Stop the auto-cleanup manager
    autoCleanupManager?.stop()
    autoCleanupManager = null

    uiController.stop()
    displayer.cancelPendingMessages()
    sdkScope.cancel()
  }

  /**
   * Shows all in-app messages that should be displayed automatically:
   * - If currentRoute is null: shows all messages with trigger.type == APP_OPEN
   * - If currentRoute is not null: shows all messages with trigger.type == ROUTE and trigger.route == currentRoute, and all with trigger.type == APP_OPEN
   *
   * Call this once on app start (with currentRoute = null),
   * and on route/view change (with currentRoute = route name).
   */
  fun showMessagesOnRoute(route: Route) {
    sdkScope.launch {
      manager.refreshActiveMessages(route)
    }
  }

  /**
   * Shows in-app messages for a custom trigger.
   * Only messages with trigger.type == CUSTOM_TRIGGER and matching key and value will be shown.
   * Also doesn't cancel pending messages for APP_OPEN trigger.
   */
  fun showMessagesOnTrigger(trigger: Trigger) {
    sdkScope.launch {
      val messageToShow = manager.trigger(trigger.key, trigger.value)

      if (messageToShow != null) {
        uiController.displayCustomMessage(messageToShow)
      }
    }
  }
}
