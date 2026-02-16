package com.pushpushgo.sdk.inapp

import android.app.Application
import android.util.Log
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.core.internal.ManifestConfigProvider
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
import com.pushpushgo.sdk.inapp.ui.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

  companion object {
    internal const val TAG = "[PushPushGo:InAppMessages]"

    @Volatile
    private var INSTANCE: InAppMessages? = null

    /**
     * Initializes the InAppMessages SDK using configuration defined in AndroidManifest.xml.
     *
     * Subsequent calls return the same instance.
     *
     * @throws IllegalStateException if required manifest values are missing.
     */
    @JvmStatic
    fun initialize(
      application: Application,
      pushSubscriptionProvider: PushSubscriptionProvider? = null,
      customCodeHandler: CustomCodeHandler? = null,
    ): InAppMessages =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: InAppMessages(
          application = application,
          config = ManifestConfigProvider(application).provide(),
          pushSubscriptionProvider = pushSubscriptionProvider,
          customCodeHandler = customCodeHandler,
        ).also { INSTANCE = it }
      }

    /**
     * Initializes the InAppMessages SDK using an explicit [Config].
     *
     * Subsequent calls return the same instance.
     */
    @JvmStatic
    fun initialize(
      application: Application,
      config: Config,
      pushSubscriptionProvider: PushSubscriptionProvider? = null,
      customCodeHandler: CustomCodeHandler? = null,
    ): InAppMessages =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: InAppMessages(
          application = application,
          config = config,
          pushSubscriptionProvider = pushSubscriptionProvider,
          customCodeHandler = customCodeHandler,
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
  }

  /**
   * Displays in-app messages applicable to the given route.
   *
   * Messages will be shown if they:
   * - Are configured to display on all pages
   * - Have a route filter that matches the provided route
   *
   * This method should be called:
   * - Once when the app starts
   * - Whenever the active route changes
   *
   * @param route Non-blank route identifier.
   *
   * @throws IllegalArgumentException if route is blank.
   */
  fun showMessagesOnRoute(route: String) {
    require(route.isNotBlank()) {
      "Route name must not me blank"
    }

    sdkScope.launch {
      manager.refreshActiveMessages(route)
    }
  }

  /**
   * Displays in-app messages associated with a custom trigger.
   *
   * Only messages whose trigger conditions match the provided trigger
   * will be displayed.
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
