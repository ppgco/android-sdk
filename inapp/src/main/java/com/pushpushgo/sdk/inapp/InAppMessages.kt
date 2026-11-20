package com.pushpushgo.sdk.inapp

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.core.internal.ManifestConfigProvider
import com.pushpushgo.sdk.inapp.ui.CustomCodeHandler
import com.pushpushgo.sdk.inapp.ui.Trigger

object InAppMessages {
  internal const val TAG = "[PushPushGo:InAppMessages]"

  @Volatile
  private var runtime: InAppMessagesRuntime? = null

  /**
   * Initializes the InAppMessages SDK using configuration defined in AndroidManifest.xml.
   *
   * Calling this method again with the same configuration has no effect. To move an initialized
   * SDK to another project, use [switchProject].
   *
   * @throws IllegalStateException if required manifest values are missing or the SDK is already
   * initialized with a different configuration.
   */
  @JvmStatic
  @JvmOverloads
  fun initialize(
    application: Application,
    pushSubscriptionProvider: PushSubscriptionProvider? = null,
    customCodeHandler: CustomCodeHandler? = null,
  ): InAppMessages =
    initialize(
      application = application,
      config = ManifestConfigProvider(application).provide(),
      pushSubscriptionProvider = pushSubscriptionProvider,
      customCodeHandler = customCodeHandler,
    )

  /**
   * Initializes the InAppMessages SDK using an explicit [Config].
   *
   * Calling this method again with the same configuration has no effect. To move an initialized
   * SDK to another project, use [switchProject].
   *
   * @throws IllegalStateException if the SDK is already initialized with a different
   * configuration.
   */
  @JvmStatic
  @JvmOverloads
  fun initialize(
    application: Application,
    config: Config,
    pushSubscriptionProvider: PushSubscriptionProvider? = null,
    customCodeHandler: CustomCodeHandler? = null,
  ): InAppMessages =
    synchronized(this) {
      val activeRuntime = runtime

      if (activeRuntime == null) {
        runtime = InAppMessagesRuntime(application, config, pushSubscriptionProvider, customCodeHandler).apply { start() }
      } else {
        check(activeRuntime.config == config) {
          "InAppMessages SDK is already initialized with a different configuration. " +
            "Use InAppMessages.switchProject() to move it to another project."
        }
      }

      this
    }

  @JvmStatic
  fun isInitialized(): Boolean = runtime != null

  /**
   * Moves the SDK to the project described by [config].
   *
   * The message displayed for the current project is hidden and its messages are no longer shown.
   * Messages of the new project are fetched and, when they apply to the current screen, displayed
   * without waiting for the next navigation. Messages the user already dismissed stay dismissed.
   *
   * Switching to the configuration the SDK already uses has no effect. The push subscription
   * provider and the custom code handler stay in place. The SDK does not remember [config] across
   * app restarts - initialize it with the configuration of the current project on the next start.
   *
   * @throws IllegalStateException if the SDK is not initialized.
   */
  @JvmStatic
  fun switchProject(config: Config) {
    synchronized(this) {
      val previous = requireRuntime()
      if (previous.config == config) return

      val route = previous.route
      val activity = previous.currentActivity
      previous.release()

      runtime =
        InAppMessagesRuntime(
          application = previous.application,
          config = config,
          pushSubscriptionProvider = previous.pushSubscriptionProvider,
          customCodeHandler = previous.customCodeHandler,
        ).apply { start(route, activity) }
    }
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
   * @throws IllegalStateException if the SDK is not initialized.
   */
  @JvmStatic
  fun showMessagesOnRoute(route: String) {
    require(route.isNotBlank()) {
      "Route name must not me blank"
    }

    requireRuntime().showMessagesOnRoute(route)
  }

  /**
   * Displays in-app messages associated with a custom trigger.
   *
   * Only messages whose trigger conditions match the provided trigger
   * will be displayed.
   *
   * @throws IllegalStateException if the SDK is not initialized.
   */
  @JvmStatic
  fun showMessagesOnTrigger(trigger: Trigger) {
    requireRuntime().showMessagesOnTrigger(trigger)
  }

  @VisibleForTesting
  internal fun reset() {
    synchronized(this) {
      runtime?.release()
      runtime = null
    }
  }

  private fun requireRuntime(): InAppMessagesRuntime = checkNotNull(runtime) { "InAppMessages SDK is not initialized" }
}
