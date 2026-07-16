package com.pushpushgo.sdk.push.liveactivity

import android.app.Application
import android.content.Intent
import android.os.Build
import com.pushpushgo.sdk.push.PushNotificationsCallbacks
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivity
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPayloadParser
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the Live Activity subsystem (persistence + manager + push handler) and
 * the subscriber-side operations exposed through [LiveActivities].
 *
 * Everything here is a no-op on API < 36 (Live Activities require ProgressStyle
 * notifications), which is why [manager]/[handler] are nullable and lazy.
 */
internal class LiveActivityController(
  application: Application,
  private val scope: CoroutineScope,
  private val apiRepository: ApiRepository,
  private val sharedPref: SharedPreferencesHelper,
  private val getSubscriberId: () -> String?,
  private val callbacks: PushNotificationsCallbacks,
) {
  private val persistence: LiveActivityPersistence by lazy { LiveActivityPersistence(application) }

  private val manager: LiveActivityManager? by lazy {
    if (Build.VERSION.SDK_INT >= 36) LiveActivityManager(persistence) else null
  }

  val handler: LiveActivityHandler? by lazy {
    val manager = manager ?: return@lazy null
    LiveActivityHandler(
      context = application,
      scope = scope,
      manager = manager,
      apiRepository = apiRepository,
      onEvent = { eventType, laId, _, _, liveDataVersion ->
        // Map internal LA lifecycle events to the backend statistics enum
        // (started / closed / clicked / clicked_1 / clicked_2) and report them
        // to the dedicated live-notification events endpoint.
        val statisticsType =
          when (eventType) {
            "la.started" -> "started"
            "la.clicked" -> "clicked"
            "la.clicked_1" -> "clicked_1"
            "la.clicked_2" -> "clicked_2"
            "la.dismissed" -> "closed"
            else -> null // la.ended not reported (no backend enum value)
          }
        if (statisticsType != null) {
          scope.launch {
            runCatching {
              apiRepository.sendLiveActivityEvent(
                liveNotificationId = laId,
                eventType = statisticsType,
                liveDataVersion = liveDataVersion,
                subscriberId = getSubscriberId().orEmpty(),
              )
            }.onFailure { logError("Failed to send LA statistics event $statisticsType for $laId", it) }
          }
        }
      },
    )
  }

  fun restoreFromPersistence() {
    if (Build.VERSION.SDK_INT >= 36) {
      manager?.restoreFromPersistence()
    }
  }

  fun clearProjectData() {
    persistence.clearAll()
  }

  fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 36

  fun getActiveActivities(): List<LiveActivity> = manager?.getActiveActivities() ?: emptyList()

  fun isActive(id: String): Boolean = manager?.isActivityActive(id) ?: false

  fun simulatePush(data: Map<String, String>) {
    handler?.handlePush(data)
  }

  suspend fun subscribe(liveNotificationId: String): String {
    val laSubscriberId = apiRepository.subscribeToLiveActivity(liveNotificationId)
    sharedPref.setLiveActivitySubscriberId(liveNotificationId, laSubscriberId)
    // Catch up: render the current state for subscribers that joined after the
    // `start` push was already delivered (no-op if the LA isn't live yet).
    catchUp(liveNotificationId)
    return laSubscriberId
  }

  suspend fun unsubscribe(liveNotificationId: String) {
    val laSubscriberId = sharedPref.getLiveActivitySubscriberId(liveNotificationId)
    check(laSubscriberId.isNotEmpty()) {
      "Not subscribed to live notification $liveNotificationId"
    }
    apiRepository.unsubscribeFromLiveActivity(liveNotificationId, laSubscriberId)
    sharedPref.removeLiveActivitySubscriberId(liveNotificationId)
  }

  fun getSubscriberId(liveNotificationId: String): String = sharedPref.getLiveActivitySubscriberId(liveNotificationId)

  /**
   * Handles a Live Activity notification click: reports the click analytics event
   * and, unless [openDeepLink] is false, opens the carried deep link through the
   * shared notification click handler. Returns the deep link, or `null` when the
   * intent is not a Live Activity click.
   */
  fun handleClick(
    application: Application,
    intent: Intent?,
    openDeepLink: Boolean,
  ): String? {
    val laId = intent?.getStringExtra(LiveActivityHandler.EXTRA_LIVE_ACTIVITY_ID) ?: return null
    val deepLink = intent.getStringExtra(LiveActivityHandler.EXTRA_DEEP_LINK)
    val actionIndex = intent.getIntExtra(LiveActivityHandler.EXTRA_ACTION_INDEX, -1)

    handler?.handleClick(laId, actionIndex)
    intent.removeExtra(LiveActivityHandler.EXTRA_LIVE_ACTIVITY_ID)
    intent.removeExtra(LiveActivityHandler.EXTRA_ACTION_INDEX)

    if (openDeepLink && !deepLink.isNullOrBlank()) {
      callbacks.notificationClickHandler.onNotificationClick(application, deepLink, Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return deepLink
  }

  /**
   * Fetch the current live notification state and feed it through the render
   * pipeline as a synthetic `start`, so a late subscriber sees the running
   * activity without waiting for the next update push.
   */
  private suspend fun catchUp(liveNotificationId: String) {
    val handler = handler ?: return
    val json = apiRepository.fetchLiveActivity(liveNotificationId) ?: return
    val envelope = LiveActivityPayloadParser.buildCatchUpEnvelope(json) ?: return
    handler.handlePush(envelope)
  }
}
