package com.pushpushgo.sdk.push.liveactivity.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.RequiresApi
import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchState
import com.pushpushgo.sdk.push.liveactivity.data.TrackedLiveActivity

/**
 * [LiveActivityRenderer] of the `FOOTBALL_MATCH_TRACKING` template. Re-posts
 * every second so the game clock / pre-match countdown stays fresh, and
 * alternates the large icon between the team crests.
 */
@RequiresApi(36)
internal class FootballMatchRenderer(
  context: Context,
) : LiveActivityRenderer {
  companion object {
    private const val TICK_INTERVAL_MS = 1000L
    private const val ICON_ALTERNATION_INTERVAL_MS = 3500L
  }

  private val notificationBuilder = ProgressStyleBuilder(context)

  override fun nextRefreshAtMs(
    activity: TrackedLiveActivity,
    nowMs: Long,
  ): Long = nowMs + TICK_INTERVAL_MS

  override fun imageUrls(activity: TrackedLiveActivity): List<String> {
    val content = activity.football().configuration.content
    return listOfNotNull(content.homeTeamImage, content.awayTeamImage)
  }

  override fun buildNotification(
    activity: TrackedLiveActivity,
    images: Map<String, Bitmap>,
    intents: LiveActivityIntents,
    showHotMessage: Boolean,
    tickerElapsedMs: Long?,
  ): Notification {
    val (home, away) = teamBitmaps(activity, images)

    return notificationBuilder.buildMatchNotification(
      match = activity.football(),
      hotMessage = activity.hotMessage,
      homeTeamBitmap = home,
      awayTeamBitmap = away,
      contentIntent = intents.contentIntent,
      deleteIntent = intents.deleteIntent,
      actions = intents.actions,
      showHotMessage = showHotMessage,
      largeIconBitmap = largeIcon(home, away, tickerElapsedMs),
    )
  }

  override fun buildEndedNotification(
    activity: TrackedLiveActivity,
    images: Map<String, Bitmap>,
    contentIntent: PendingIntent?,
  ): Notification {
    val (home, away) = teamBitmaps(activity, images)

    return notificationBuilder.buildEndedNotification(
      match = activity.football(),
      homeTeamBitmap = home,
      awayTeamBitmap = away,
      contentIntent = contentIntent,
    )
  }

  /**
   * Outside of the ticker the home crest is shown; within it the crests
   * alternate every [ICON_ALTERNATION_INTERVAL_MS] (when both are available).
   */
  private fun largeIcon(
    home: Bitmap?,
    away: Bitmap?,
    tickerElapsedMs: Long?,
  ): Bitmap? {
    if (tickerElapsedMs == null) return home
    val showHome = (tickerElapsedMs / ICON_ALTERNATION_INTERVAL_MS) % 2L == 0L
    return when {
      home != null && away != null -> if (showHome) home else away
      else -> home ?: away
    }
  }

  private fun teamBitmaps(
    activity: TrackedLiveActivity,
    images: Map<String, Bitmap>,
  ): Pair<Bitmap?, Bitmap?> {
    val content = activity.football().configuration.content
    return content.homeTeamImage?.let(images::get) to content.awayTeamImage?.let(images::get)
  }

  private fun TrackedLiveActivity.football(): FootballMatchState =
    state as? FootballMatchState
      ?: error("FootballMatchRenderer can't render ${template.value} activity $id")
}
