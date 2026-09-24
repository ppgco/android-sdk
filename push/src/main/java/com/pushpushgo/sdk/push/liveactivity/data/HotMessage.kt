package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Transient, time-bounded message shown on top of a Live Activity (e.g.
 * "Goal cancelled after VAR"). Mirrors the backend / iOS `hotMessage`
 * (`{ id, text, timestamp }`, where `timestamp` is the Unix-epoch-seconds
 * hard expiry). Visibility is the minimum of a local display cap and the
 * backend expiry — matching the iOS behavior.
 */
data class HotMessage(
  val id: String,
  val text: String,
  /** Hard cutoff as epoch millis, or `null` when the backend sends no expiry. */
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
    /** SDK-controlled maximum local display duration (10 s, matches iOS). */
    const val MAX_DISPLAY_MS = 10_000L
  }
}
