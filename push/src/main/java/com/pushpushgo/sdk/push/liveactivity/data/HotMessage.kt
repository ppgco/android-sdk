package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Transient, time-bounded message shown on top of a Live Activity (e.g.
 * "Goal cancelled after VAR"). Visibility is the minimum of a local display cap
 * and the expiry of the message.
 */
internal data class HotMessage(
  val id: String,
  val text: String,
  /** Hard cutoff as epoch millis, or `null` when the message has no expiry. */
  val expiresAtMs: Long?,
) {
  /**
   * Effective display duration (millis) from now: the smaller of the local cap
   * ([MAX_DISPLAY_MS]) and the time remaining until [expiresAtMs].
   */
  fun displayDurationMs(nowMs: Long = System.currentTimeMillis()): Long {
    val expires = expiresAtMs ?: return MAX_DISPLAY_MS
    val remaining = expires - nowMs
    return remaining.coerceIn(0L, MAX_DISPLAY_MS)
  }

  companion object {
    /** Maximum local display duration (10 s). */
    const val MAX_DISPLAY_MS = 10_000L
  }
}
