package com.pushpushgo.inappmessages.utils

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.pushpushgo.inappmessages.InAppMessagesSDK

/**
 * Helper class that simplifies in-app message integration across different UI frameworks.
 * It provides convenient methods to show messages at the right time in activities, fragments,
 * and when using navigation components.
 */
object InAppMessageHelper {
  private const val TAG = "InAppMessageHelper"

  /**
   * Shows in-app messages for a specific screen/route
   *
   * @param screenName The name of the current screen/route
   */
  fun showMessagesForScreen(screenName: String) {
    Log.d(TAG, "Showing messages for screen: $screenName")
    InAppMessagesSDK.getInstance().showActiveMessages(screenName)
  }

  /**
   * Shows in-app messages for a Fragment
   * Uses the Fragment's simple class name as the screen name by default,
   * or a custom name if provided.
   *
   * @param fragment The current fragment
   * @param customName Optional custom screen name
   */
  fun showMessagesForFragment(
    fragment: Fragment,
    customName: String? = null,
  ) {
    val screenName = customName ?: fragment.javaClass.simpleName
    showMessagesForScreen(screenName)
  }

  /**
   * Attaches a NavController listener to automatically show in-app messages
   * when the destination changes.
   *
   * @param navController The NavController to observe
   * @param nameProvider Optional function to convert NavDestination to screen name
   * @return The listener that was added, so it can be removed later
   */
  fun setupWithNavController(
    navController: NavController,
    nameProvider: (Int, String?) -> String = { id, label -> label ?: id.toString() },
  ): NavController.OnDestinationChangedListener {
    val listener =
      NavController.OnDestinationChangedListener { _, destination, _ ->
        val screenName = nameProvider(destination.id, destination.label?.toString())
        showMessagesForScreen(screenName)
      }

    navController.addOnDestinationChangedListener(listener)
    return listener
  }

  /**
   * Creates a lifecycle observer that automatically shows in-app messages
   * during onResume.
   *
   * @param screenNameProvider A function that returns the current screen name
   * @return A lifecycle observer that can be added to a lifecycle owner
   */
  fun createLifecycleObserver(screenNameProvider: () -> String): DefaultLifecycleObserver =
    object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        showMessagesForScreen(screenNameProvider())
      }
    }

  /**
   * A Composable function to integrate with Jetpack Compose Navigation.
   * Use this inside your NavHost or composable that observe navigation.
   *
   * @param currentRoute The current route from NavBackStackEntry or other source
   */
  @Composable
  fun ObserveNavigation(currentRoute: String?) {
    LaunchedEffect(currentRoute) {
      // Use empty string instead of null for route
      showMessagesForScreen(currentRoute ?: "")
    }
  }

  /**
   * A convenience function for common Compose navigation pattern where
   * you get routes from NavBackStackEntry state.
   *
   * Usage example:
   * ```
   * val navController = rememberNavController()
   * val navBackStackEntry by navController.currentBackStackEntryAsState()
   *
   * InAppMessageHelper.ObserveNavBackStack(
   *     activity = LocalContext.current as Activity,
   *     navBackStackEntry = navBackStackEntry
   * )
   * ```
   *
   * @param navBackStackEntry The current NavBackStackEntry from navController
   * @param routeProvider Optional function to extract the route from a NavBackStackEntry
   */
  @Composable
  fun ObserveNavBackStack(
    navBackStackEntry: androidx.navigation.NavBackStackEntry?,
    routeProvider: (androidx.navigation.NavBackStackEntry?) -> String? = { it?.destination?.route },
  ) {
    val currentRoute = routeProvider(navBackStackEntry)
    ObserveNavigation(currentRoute)
  }
}
