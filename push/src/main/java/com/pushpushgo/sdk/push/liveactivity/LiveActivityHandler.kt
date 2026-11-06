package com.pushpushgo.sdk.push.liveactivity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchState
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityAction
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityActionType
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityEvent
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPayloadParser
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPush
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityTemplate
import com.pushpushgo.sdk.push.liveactivity.data.MatchPhase
import com.pushpushgo.sdk.push.liveactivity.data.TrackedLiveActivity
import com.pushpushgo.sdk.push.liveactivity.notification.FootballMatchRenderer
import com.pushpushgo.sdk.push.liveactivity.notification.LiveActivityIntents
import com.pushpushgo.sdk.push.liveactivity.notification.LiveActivityNotificationChannel
import com.pushpushgo.sdk.push.liveactivity.notification.LiveActivityRenderer
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.utils.PendingIntentCompat
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.logWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(36)
internal class LiveActivityHandler(
  private val context: Context,
  private val scope: CoroutineScope,
  private val manager: LiveActivityManager,
  private val apiRepository: ApiRepository,
  private val onEvent: (
    (
      eventType: String,
      liveActivityId: String,
      projectId: String,
      subscriberId: String,
      liveDataVersion: Int,
    ) -> Unit
  )? = null,
) {
  companion object {
    const val ACTION_DISMISS = "com.pushpushgo.sdk.push.LIVE_ACTIVITY_DISMISS"
    const val EXTRA_LIVE_ACTIVITY_ID = "live_activity_id"
    const val EXTRA_DEEP_LINK = "deep_link"
    const val EXTRA_PROJECT_ID = "la_project_id"
    const val EXTRA_SUBSCRIBER_ID = "la_subscriber_id"

    /** Index of the tapped action button (0-based); absent/-1 for a content tap. */
    const val EXTRA_ACTION_INDEX = "la_action_index"

    private const val BITMAP_TIMEOUT_MS = 10_000L
    private const val ENDED_NOTIFICATION_DELAY_MS = 10_000L
  }

  private val footballMatchRenderer = FootballMatchRenderer(context)

  /**
   * Per-notification jobs re-posting the notification at
   * [LiveActivityRenderer.nextRefreshAtMs] so time-based content (e.g. the game
   * clock) stays fresh.
   */
  private val tickerJobs = ConcurrentHashMap<Int, Job>()

  private fun rendererFor(activity: TrackedLiveActivity): LiveActivityRenderer =
    when (activity.state) {
      is FootballMatchState -> footballMatchRenderer
    }

  fun handlePush(data: Map<String, String>) {
    if (!LiveActivityPayloadParser.isLiveActivityPush(data)) return

    if (Build.VERSION.SDK_INT < 36) {
      logWarning("LiveActivityHandler: Live Activities require API 36+, ignoring push")
      return
    }

    val push = LiveActivityPayloadParser.parsePush(data)
    if (push == null) {
      val template = data["template"]
      if (LiveActivityTemplate.fromValue(template) == null) {
        logWarning("LiveActivityHandler: unsupported template '$template', ignoring push (requires a newer SDK)")
      } else {
        logError("LiveActivityHandler: failed to parse live notification push")
      }
      return
    }

    logDebug("LiveActivityHandler: handling event=${push.event.value}, id=${push.id}")

    scope.launch {
      try {
        when (push.event) {
          LiveActivityEvent.START -> handleStart(push)
          LiveActivityEvent.UPDATE -> handleUpdate(push)
          LiveActivityEvent.END -> handleEnd(push)
        }
      } catch (e: Exception) {
        logError("LiveActivityHandler: error processing LA push", e)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun handleStart(push: LiveActivityPush) {
    LiveActivityNotificationChannel.ensureChannel(context)

    var activity =
      manager.startActivity(push) ?: run {
        logError("LiveActivityHandler: failed to start live activity ${push.id}")
        return
      }

    // The pre-match countdown is not part of the push, so fetch it separately.
    if ((activity.state as? FootballMatchState)?.liveData?.status == MatchPhase.PRE_MATCH) {
      activity = fetchCountdown(activity) ?: activity
    }

    val notificationId = manager.getNotificationId(activity.id)
    if (notificationId == -1) return

    notifyActivity(notificationId, activity)
    logDebug("LiveActivityHandler: started notification $notificationId for ${activity.id}")

    onEvent?.invoke("la.started", activity.id, activity.projectId, activity.subscriberId, activity.liveDataVersion)
  }

  private suspend fun fetchCountdown(activity: TrackedLiveActivity): TrackedLiveActivity? {
    val json = apiRepository.fetchLiveActivity(activity.id) ?: return null
    val countdown = LiveActivityPayloadParser.parseStartPolicy(json) ?: return null
    if (countdown.endAtMs <= System.currentTimeMillis()) return null
    logDebug("LiveActivityHandler: countdown until ${countdown.endAtMs} for ${activity.id}")
    return manager.setCountdown(activity.id, countdown.message, countdown.endAtMs)
  }

  @SuppressLint("MissingPermission")
  private suspend fun handleUpdate(push: LiveActivityPush) {
    val updatedActivity =
      manager.updateActivity(push) ?: run {
        // Activity not tracked locally — fall back to a fresh start when the
        // push carries a full configuration (otherwise nothing to render).
        if (LiveActivityPayloadParser.parseState(push.template, push.configurationJson, push.liveDataJson) != null) {
          logDebug("LiveActivityHandler: activity ${push.id} not found, treating update as start")
          handleStart(push)
        } else {
          logWarning("LiveActivityHandler: dropping update for untracked activity ${push.id}")
        }
        return
      }

    val notificationId = manager.getNotificationId(updatedActivity.id)
    if (notificationId == -1) return

    notifyActivity(notificationId, updatedActivity)
    logDebug("LiveActivityHandler: updated notification $notificationId for ${updatedActivity.id}")
  }

  @SuppressLint("MissingPermission")
  private suspend fun handleEnd(push: LiveActivityPush) {
    val endedActivity = manager.endActivity(push) ?: return
    val notificationId = manager.getNotificationId(endedActivity.id)
    if (notificationId == -1) return

    cancelTicker(notificationId)

    val renderer = rendererFor(endedActivity)
    val images = downloadImages(renderer.imageUrls(endedActivity))
    val notification =
      renderer.buildEndedNotification(
        activity = endedActivity,
        images = images,
        contentIntent = buildContentIntent(endedActivity, notificationId),
      )

    NotificationManagerCompat.from(context).notify(notificationId, notification)
    logDebug("LiveActivityHandler: ended notification $notificationId for ${endedActivity.id}")

    onEvent?.invoke(
      "la.ended",
      endedActivity.id,
      endedActivity.projectId,
      endedActivity.subscriberId,
      endedActivity.liveDataVersion,
    )

    // Auto-remove after delay
    scope.launch {
      delay(ENDED_NOTIFICATION_DELAY_MS)
      NotificationManagerCompat.from(context).cancel(notificationId)
      manager.removeActivity(endedActivity.id)
    }
  }

  /**
   * Post the live notification. When the activity carries a transient
   * [com.pushpushgo.sdk.push.liveactivity.data.HotMessage], it is shown first
   * for its display duration, then the notification reverts to the normal
   * content.
   */
  @SuppressLint("MissingPermission")
  private suspend fun notifyActivity(
    notificationId: Int,
    activity: TrackedLiveActivity,
  ) {
    val renderer = rendererFor(activity)
    val images = downloadImages(renderer.imageUrls(activity))
    val hotMessage = activity.hotMessage

    val notification =
      buildNotification(
        renderer = renderer,
        activity = activity,
        images = images,
        notificationId = notificationId,
        showHotMessage = hotMessage != null,
      )
    NotificationManagerCompat.from(context).notify(notificationId, notification)

    if (hotMessage != null) {
      // Pause the ticker while the hot message is displayed, then revert and resume.
      cancelTicker(notificationId)
      scope.launch {
        delay(hotMessage.displayDurationMs())
        // Re-fetch the latest tracked state so we don't overwrite a newer update.
        val latest = manager.getActivity(activity.id) ?: return@launch
        if (latest.status.value != "active") return@launch
        NotificationManagerCompat.from(context).notify(
          notificationId,
          buildNotification(
            renderer = renderer,
            activity = latest.copy(hotMessage = null),
            images = images,
            notificationId = notificationId,
            showHotMessage = false,
          ),
        )
        startTicker(notificationId, activity.id, renderer, images)
      }
    } else {
      startTicker(notificationId, activity.id, renderer, images)
    }
  }

  /**
   * Re-post the notification at [LiveActivityRenderer.nextRefreshAtMs] while
   * the activity is live (e.g. every second so the game clock ticks and the
   * break-bar tracker moves smoothly). Stops when the renderer has nothing
   * left to refresh. Each tick re-reads the tracked state, so newer updates
   * are never overwritten.
   */
  @SuppressLint("MissingPermission")
  private fun startTicker(
    notificationId: Int,
    activityId: String,
    renderer: LiveActivityRenderer,
    images: Map<String, Bitmap>,
  ) {
    cancelTicker(notificationId)

    tickerJobs[notificationId] =
      scope.launch {
        val startedAtMs = System.currentTimeMillis()
        while (isActive) {
          val current = manager.getActivity(activityId) ?: break
          val nowMs = System.currentTimeMillis()
          val refreshAtMs = renderer.nextRefreshAtMs(current, nowMs) ?: break
          delay((refreshAtMs - nowMs).coerceAtLeast(0L))
          val latest = manager.getActivity(activityId) ?: break
          if (latest.status.value != "active") break

          val notification =
            buildNotification(
              renderer = renderer,
              activity = latest.copy(hotMessage = null),
              images = images,
              notificationId = notificationId,
              showHotMessage = false,
              tickerElapsedMs = System.currentTimeMillis() - startedAtMs,
            )
          runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
        }
      }
  }

  private fun cancelTicker(notificationId: Int) {
    tickerJobs.remove(notificationId)?.cancel()
  }

  fun removeActivity(liveActivityId: String) {
    val notificationId = manager.getNotificationId(liveActivityId)

    if (notificationId != -1) {
      cancelTicker(notificationId)
    }

    manager.removeActivity(liveActivityId)

    if (notificationId != -1) {
      NotificationManagerCompat.from(context).cancel(notificationId)
    }
  }

  fun handleDismiss(liveActivityId: String) {
    val activity = manager.getActivity(liveActivityId)
    manager.markDismissedByUser(liveActivityId)

    val notificationId = manager.getNotificationId(liveActivityId)
    if (notificationId != -1) {
      cancelTicker(notificationId)
      NotificationManagerCompat.from(context).cancel(notificationId)
    }

    if (activity != null) {
      onEvent?.invoke("la.dismissed", liveActivityId, activity.projectId, activity.subscriberId, activity.liveDataVersion)
    }
    logDebug("LiveActivityHandler: dismissed $liveActivityId by user")
  }

  /**
   * Handle a tap on the Live Activity. [actionIndex] is the 0-based index of the
   * tapped action button, or -1 for a tap on the notification body. The first
   * two action buttons map to the `clicked_1` / `clicked_2` statistics events,
   * the body (and any further buttons) to `clicked`.
   */
  fun handleClick(
    liveActivityId: String,
    actionIndex: Int,
  ) {
    val activity = manager.getActivity(liveActivityId) ?: return
    val eventType =
      when (actionIndex) {
        0 -> "la.clicked_1"
        1 -> "la.clicked_2"
        else -> "la.clicked"
      }
    onEvent?.invoke(eventType, liveActivityId, activity.projectId, activity.subscriberId, activity.liveDataVersion)
  }

  private fun buildNotification(
    renderer: LiveActivityRenderer,
    notificationId: Int,
    activity: TrackedLiveActivity,
    images: Map<String, Bitmap>,
    showHotMessage: Boolean,
    tickerElapsedMs: Long? = null,
  ): android.app.Notification {
    val intents =
      LiveActivityIntents(
        contentIntent = buildContentIntent(activity, notificationId),
        deleteIntent = buildDeleteIntent(activity, notificationId),
        actions = buildActions(activity, notificationId),
      )

    return renderer.buildNotification(
      activity = activity,
      images = images,
      intents = intents,
      showHotMessage = showHotMessage,
      tickerElapsedMs = tickerElapsedMs,
    )
  }

  /** Map [LiveActivityAction]s to notification actions with intents. */
  private fun buildActions(
    activity: TrackedLiveActivity,
    notificationId: Int,
  ): List<android.app.Notification.Action> =
    activity.state.actions.mapIndexedNotNull { index, action ->
      val pendingIntent =
        when (action.type) {
          LiveActivityActionType.OPEN_APP ->
            buildOpenAppIntent(
              activity,
              notificationId,
              action.url ?: activity.state.url,
              requestOffset = index + 1,
              actionIndex = index,
            )
          LiveActivityActionType.REDIRECT, LiveActivityActionType.URL ->
            buildOpenAppIntent(
              activity,
              notificationId,
              action.url ?: activity.state.url,
              requestOffset = index + 1,
              actionIndex = index,
            )
          LiveActivityActionType.CLOSE ->
            buildDismissActionIntent(activity, notificationId, index)
        } ?: return@mapIndexedNotNull null

      android.app.Notification.Action
        .Builder(null, action.name, pendingIntent)
        .build()
    }

  private fun buildContentIntent(
    activity: TrackedLiveActivity,
    notificationId: Int,
  ): PendingIntent? = buildOpenAppIntent(activity, notificationId, activity.state.url, requestOffset = 0, actionIndex = -1)

  /**
   * Launch the host app, forwarding the deep link (if any) and the tapped action
   * index as intent extras. [requestOffset] keeps each button's PendingIntent
   * request code distinct (content tap = 0, action buttons start at 1) so their
   * extras don't overwrite each other.
   */
  private fun buildOpenAppIntent(
    activity: TrackedLiveActivity,
    notificationId: Int,
    deepLink: String?,
    requestOffset: Int,
    actionIndex: Int,
  ): PendingIntent? {
    val launcherIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null

    // Without these flags an already-running task is merely brought to the
    // front and the intent (with the click extras) is never delivered to the
    // activity — neither onCreate nor onNewIntent fires, so the click is lost.
    launcherIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    launcherIntent.putExtra(EXTRA_LIVE_ACTIVITY_ID, activity.id)
    deepLink?.let { launcherIntent.putExtra(EXTRA_DEEP_LINK, it) }
    launcherIntent.putExtra(EXTRA_PROJECT_ID, activity.projectId)
    launcherIntent.putExtra(EXTRA_SUBSCRIBER_ID, activity.subscriberId)
    if (actionIndex >= 0) launcherIntent.putExtra(EXTRA_ACTION_INDEX, actionIndex)

    return PendingIntent.getActivity(
      context,
      notificationId + 20_000 + requestOffset,
      launcherIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
    )
  }

  private fun buildDeleteIntent(
    activity: TrackedLiveActivity,
    notificationId: Int,
  ): PendingIntent = buildDismissActionIntent(activity, notificationId, requestOffset = 0, baseOffset = 10_000)

  private fun buildDismissActionIntent(
    activity: TrackedLiveActivity,
    notificationId: Int,
    requestOffset: Int,
    baseOffset: Int = 30_000,
  ): PendingIntent {
    val intent =
      Intent(ACTION_DISMISS).apply {
        setPackage(context.packageName)
        putExtra(EXTRA_LIVE_ACTIVITY_ID, activity.id)
        putExtra(EXTRA_PROJECT_ID, activity.projectId)
        putExtra(EXTRA_SUBSCRIBER_ID, activity.subscriberId)
      }

    return PendingIntent.getBroadcast(
      context,
      notificationId + baseOffset + requestOffset,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
    )
  }

  /** Download the given images one by one; failed or empty URLs are left out of the map. */
  private suspend fun downloadImages(urls: List<String>): Map<String, Bitmap> =
    buildMap {
      for (url in urls.distinct()) {
        downloadBitmap(url)?.let { put(url, it) }
      }
    }

  private suspend fun downloadBitmap(url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null

    val startedAt = SystemClock.elapsedRealtime()
    return try {
      val bitmap =
        withTimeoutOrNull(BITMAP_TIMEOUT_MS) {
          withContext(Dispatchers.IO) {
            apiRepository.getBitmapFromUrl(url)
          }
        }
      val elapsedMs = SystemClock.elapsedRealtime() - startedAt

      when {
        bitmap != null ->
          logDebug("LiveActivityHandler: bitmap downloaded in ${elapsedMs}ms (${bitmap.width}x${bitmap.height}): $url")

        elapsedMs >= BITMAP_TIMEOUT_MS ->
          logWarning("LiveActivityHandler: bitmap timed out after ${elapsedMs}ms (limit ${BITMAP_TIMEOUT_MS}ms): $url")

        else ->
          logWarning("LiveActivityHandler: bitmap decoded to null after ${elapsedMs}ms: $url")
      }

      bitmap
    } catch (e: Exception) {
      val elapsedMs = SystemClock.elapsedRealtime() - startedAt
      logError("LiveActivityHandler: failed to download bitmap from $url after ${elapsedMs}ms", e)
      null
    }
  }
}
