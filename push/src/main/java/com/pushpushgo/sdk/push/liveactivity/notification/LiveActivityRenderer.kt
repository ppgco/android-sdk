package com.pushpushgo.sdk.push.liveactivity.notification

import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import com.pushpushgo.sdk.push.liveactivity.data.TrackedLiveActivity

/**
 * Renders the notification of one Live Activity template. The handler owns the
 * lifecycle (start / update / end, hot messages, ticker, intents, analytics)
 * and delegates only the template-specific look to the renderer.
 */
internal interface LiveActivityRenderer {
  /**
   * Epoch millis at which the notification should be re-posted while the
   * activity is live (its content changes over time), or null when nothing
   * changes until the next push.
   */
  fun nextRefreshAtMs(
    activity: TrackedLiveActivity,
    nowMs: Long,
  ): Long?

  /** Image URLs the notification needs; downloaded by the handler before rendering. */
  fun imageUrls(activity: TrackedLiveActivity): List<String>

  /**
   * Build the live notification. [tickerElapsedMs] is the time since the ticker
   * started, or null for a post outside of the ticker (start / update / hot
   * message revert).
   */
  fun buildNotification(
    activity: TrackedLiveActivity,
    images: Map<String, Bitmap>,
    intents: LiveActivityIntents,
    showHotMessage: Boolean,
    tickerElapsedMs: Long?,
  ): Notification

  /** Build the final notification shown briefly after the `end` event. */
  fun buildEndedNotification(
    activity: TrackedLiveActivity,
    images: Map<String, Bitmap>,
    contentIntent: PendingIntent?,
  ): Notification
}

/** Template-agnostic intents attached to a live notification by the handler. */
internal class LiveActivityIntents(
  val contentIntent: PendingIntent?,
  val deleteIntent: PendingIntent?,
  val actions: List<Notification.Action>,
)
