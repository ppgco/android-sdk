package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Dynamic, real-time state of a football match Live Activity, delivered in the
 * `liveData` field of `start` / `update` pushes. Mirrors the backend / iOS
 * football match live data DTO.
 */
data class FootballMatchLiveData(
  val homeTeamScore: Int,
  val awayTeamScore: Int,
  val status: MatchPhase,
  /** Epoch millis when the current [status] was entered; used to compute the live minute. */
  val statusChangedAt: Long?,
  /** Monotonic version of this live data, echoed back in analytics events. */
  val liveDataVersion: Int = 0,
) {
  /** Score formatted as `"home:away"`. */
  val scoreText: String
    get() = "$homeTeamScore:$awayTeamScore"
}
