package com.pushpushgo.sdk.push.liveactivity.data

/**
 * All possible phases of a football match. Wire values mirror the backend /
 * iOS `MatchPhase` exactly (including the `OTHER` fallback bucket).
 */
enum class MatchPhase(val value: String) {
  PRE_MATCH("PRE_MATCH"),
  FIRST_HALF("FIRST_HALF"),
  FIRST_HALF_ADDED_TIME("FIRST_HALF_ADDED_TIME"),
  HALF_TIME_BREAK("HALF_TIME_BREAK"),
  SECOND_HALF("SECOND_HALF"),
  SECOND_HALF_ADDED_TIME("SECOND_HALF_ADDED_TIME"),
  FULL_TIME("FULL_TIME"),
  EXTRA_TIME_BREAK("EXTRA_TIME_BREAK"),
  EXTRA_TIME_FIRST_HALF("EXTRA_TIME_FIRST_HALF"),
  EXTRA_TIME_FIRST_HALF_ADDED_TIME("EXTRA_TIME_FIRST_HALF_ADDED_TIME"),
  EXTRA_TIME_HALF_TIME_BREAK("EXTRA_TIME_HALF_TIME_BREAK"),
  EXTRA_TIME_SECOND_HALF("EXTRA_TIME_SECOND_HALF"),
  EXTRA_TIME_SECOND_HALF_ADDED_TIME("EXTRA_TIME_SECOND_HALF_ADDED_TIME"),
  PENALTY_SHOOTOUT("PENALTY_SHOOTOUT"),
  MATCH_ENDED("MATCH_ENDED"),
  OTHER("OTHER"),
  ;

  /** Default display text used when the backend does not override it via `statusLabels`. */
  val displayText: String
    get() = when (this) {
      PRE_MATCH -> "Pre-Match"
      FIRST_HALF -> "1st Half"
      FIRST_HALF_ADDED_TIME -> "1st Half +AT"
      HALF_TIME_BREAK -> "Half Time"
      SECOND_HALF -> "2nd Half"
      SECOND_HALF_ADDED_TIME -> "2nd Half +AT"
      FULL_TIME -> "Full Time"
      EXTRA_TIME_BREAK -> "ET Break"
      EXTRA_TIME_FIRST_HALF -> "ET 1st Half"
      EXTRA_TIME_FIRST_HALF_ADDED_TIME -> "ET 1st Half +AT"
      EXTRA_TIME_HALF_TIME_BREAK -> "ET Half Time"
      EXTRA_TIME_SECOND_HALF -> "ET 2nd Half"
      EXTRA_TIME_SECOND_HALF_ADDED_TIME -> "ET 2nd Half +AT"
      PENALTY_SHOOTOUT -> "Penalties"
      MATCH_ENDED -> "Match Ended"
      OTHER -> ""
    }

  /** Whether the ball is actively in play (regular, added or extra time, penalties). */
  val isPlaying: Boolean
    get() = when (this) {
      FIRST_HALF, FIRST_HALF_ADDED_TIME,
      SECOND_HALF, SECOND_HALF_ADDED_TIME,
      EXTRA_TIME_FIRST_HALF, EXTRA_TIME_FIRST_HALF_ADDED_TIME,
      EXTRA_TIME_SECOND_HALF, EXTRA_TIME_SECOND_HALF_ADDED_TIME,
      PENALTY_SHOOTOUT,
      -> true
      else -> false
    }

  /** Whether the match is currently in a break period. */
  val isBreak: Boolean
    get() = this == HALF_TIME_BREAK || this == EXTRA_TIME_BREAK || this == EXTRA_TIME_HALF_TIME_BREAK

  /** Whether the match has concluded. */
  val isFinished: Boolean
    get() = this == FULL_TIME || this == MATCH_ENDED

  /** Whether this is added/injury time. */
  val isAddedTime: Boolean
    get() = this == FIRST_HALF_ADDED_TIME || this == SECOND_HALF_ADDED_TIME ||
      this == EXTRA_TIME_FIRST_HALF_ADDED_TIME || this == EXTRA_TIME_SECOND_HALF_ADDED_TIME

  /**
   * Fallback match minute used to position the progress bar when the backend
   * does not provide `statusChangedAt` (i.e. when the exact minute cannot be
   * computed client-side).
   */
  val baseMinute: Int
    get() = when (this) {
      PRE_MATCH -> 0
      FIRST_HALF -> 0
      FIRST_HALF_ADDED_TIME -> 45
      HALF_TIME_BREAK -> 45
      SECOND_HALF -> 45
      SECOND_HALF_ADDED_TIME -> 90
      FULL_TIME, MATCH_ENDED -> 90
      EXTRA_TIME_BREAK -> 90
      EXTRA_TIME_FIRST_HALF -> 90
      EXTRA_TIME_FIRST_HALF_ADDED_TIME -> 105
      EXTRA_TIME_HALF_TIME_BREAK -> 105
      EXTRA_TIME_SECOND_HALF -> 105
      EXTRA_TIME_SECOND_HALF_ADDED_TIME -> 120
      PENALTY_SHOOTOUT -> 120
      OTHER -> 0
    }

  companion object {
    /** Tolerant lookup — unknown values map to [OTHER] (mirrors iOS fallback). */
    fun fromValue(value: String?): MatchPhase =
      entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: OTHER
  }
}
