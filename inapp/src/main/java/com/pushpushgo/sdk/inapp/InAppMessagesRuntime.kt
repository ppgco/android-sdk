package com.pushpushgo.sdk.inapp

import android.app.Activity
import android.app.Application
import android.util.Log
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import retrofit2.Retrofit

/**
 * Everything [InAppMessages] does for one project. A project switch releases this runtime and
 * starts a new one, while [InAppMessages] stays the same object for the app.
 */
internal class InAppMessagesRuntime(
  application: Application,
  val config: Config,
  pushSubscriptionProvider: PushSubscriptionProvider?,
  customCodeHandler: CustomCodeHandler?,
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

  private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val manager: InAppMessageManager
  private val displayer: InAppMessageDisplayer
  private val uiController: InAppUIController

  init {
    val persistence = InAppMessagePersistenceImpl(application, config.projectId, config.isDebug)
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
  }

  val route: String?
    get() = manager.getRoute()

  val currentActivity: Activity?
    get() = uiController.getCurrentActivity()

  /**
   * Fetches messages and starts displaying them. After a project switch, pass the [route] and
   * [activity] of the previous runtime, so messages of the new project show up on the current
   * screen right away.
   */
  fun start(
    route: String? = null,
    activity: Activity? = null,
  ) {
    sdkScope.launch {
      manager.initialize()

      if (route != null) {
        manager.refreshActiveMessages(route)
      }
    }
    uiController.start(activity)
  }

  /** Hides the displayed message and stops fetching and displaying messages of this project. */
  fun release() {
    uiController.stop()
    sdkScope.cancel()
  }

  fun showMessagesOnRoute(route: String) {
    sdkScope.launch {
      manager.refreshActiveMessages(route)
    }
  }

  fun showMessagesOnTrigger(trigger: Trigger) {
    sdkScope.launch {
      val messageToShow = manager.trigger(trigger.key, trigger.value)

      if (messageToShow != null) {
        uiController.displayCustomMessage(messageToShow)
      }
    }
  }

  private suspend fun dispatchInAppEvent(
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
        Log.e(InAppMessages.TAG, "Failed to send in-app event", e)
      }
    }
  }
}
