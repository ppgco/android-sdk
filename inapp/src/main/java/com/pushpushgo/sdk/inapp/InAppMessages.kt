package com.pushpushgo.sdk.inapp

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.core.internal.ManifestConfigProvider
import com.pushpushgo.sdk.inapp.ui.CustomCodeHandler
import com.pushpushgo.sdk.inapp.ui.Trigger

class InAppMessages private constructor(
  private val application: Application,
  config: Config,
  private val pushSubscriptionProvider: PushSubscriptionProvider?,
  private val customCodeHandler: CustomCodeHandler?,
) {
  @Volatile
  private var runtime = createRuntime(config).apply { start() }

  companion object {
    internal const val TAG = "[PushPushGo:InAppMessages]"

    @Volatile
    private var INSTANCE: InAppMessages? = null

    /**
     * Initializes the InAppMessages SDK using configuration defined in AndroidManifest.xml.
     *
     * Subsequent calls with the same configuration return the same instance. To move an
     * initialized SDK to another project, use [switchProject].
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
      obtain(
        application = application,
        config = ManifestConfigProvider(application).provide(),
        pushSubscriptionProvider = pushSubscriptionProvider,
        customCodeHandler = customCodeHandler,
      )

    /**
     * Initializes the InAppMessages SDK using an explicit [Config].
     *
     * Subsequent calls with the same configuration return the same instance. To move an
     * initialized SDK to another project, use [switchProject].
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
      obtain(
        application = application,
        config = config,
        pushSubscriptionProvider = pushSubscriptionProvider,
        customCodeHandler = customCodeHandler,
      )

    @JvmStatic
    fun getInstance(): InAppMessages = INSTANCE ?: throw IllegalStateException("InAppMessages SDK is not initialized!")

    private fun obtain(
      application: Application,
      config: Config,
      pushSubscriptionProvider: PushSubscriptionProvider?,
      customCodeHandler: CustomCodeHandler?,
    ): InAppMessages =
      synchronized(this) {
        val instance = INSTANCE

        if (instance != null) {
          check(instance.runtime.config == config) {
            "InAppMessages SDK is already initialized with a different configuration. " +
              "Use InAppMessages.getInstance().switchProject() to move it to another project."
          }
          return instance
        }

        InAppMessages(application, config, pushSubscriptionProvider, customCodeHandler).also { INSTANCE = it }
      }

    @VisibleForTesting
    internal fun resetInstance() {
      synchronized(this) {
        INSTANCE?.runtime?.release()
        INSTANCE = null
      }
    }
  }

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
   */
  fun switchProject(config: Config) {
    synchronized(this) {
      val previous = runtime
      if (previous.config == config) return

      val route = previous.route
      val activity = previous.currentActivity
      previous.release()

      runtime = createRuntime(config).apply { start(route, activity) }
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
   */
  fun showMessagesOnRoute(route: String) {
    require(route.isNotBlank()) {
      "Route name must not me blank"
    }

    runtime.showMessagesOnRoute(route)
  }

  /**
   * Displays in-app messages associated with a custom trigger.
   *
   * Only messages whose trigger conditions match the provided trigger
   * will be displayed.
   */
  fun showMessagesOnTrigger(trigger: Trigger) {
    runtime.showMessagesOnTrigger(trigger)
  }

  private fun createRuntime(config: Config) = InAppMessagesRuntime(application, config, pushSubscriptionProvider, customCodeHandler)
}
