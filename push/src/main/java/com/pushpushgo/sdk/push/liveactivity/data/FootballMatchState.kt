package com.pushpushgo.sdk.push.liveactivity.data

/** [LiveActivityState] of the `FOOTBALL_MATCH_TRACKING` template. */
internal data class FootballMatchState(
  val configuration: FootballMatchConfiguration,
  val liveData: FootballMatchLiveData,
  /** Pre-match countdown message (from `startPolicy.countdown`), if any. */
  val countdownMessage: String? = null,
  /** Epoch millis of `startPolicy.scheduledAt` — the moment the countdown hits zero. */
  val countdownEndAtMs: Long? = null,
) : LiveActivityState {
  override val liveDataVersion: Int
    get() = liveData.liveDataVersion

  override val url: String?
    get() = configuration.url

  override val actions: List<LiveActivityAction>
    get() = configuration.actions
}

/** [LiveActivityStateUpdate] of the `FOOTBALL_MATCH_TRACKING` template. */
internal data class FootballMatchUpdate(
  val configuration: FootballMatchConfiguration?,
  val liveData: FootballMatchLiveData?,
) : LiveActivityStateUpdate {
  override fun toInitialState(): FootballMatchState? {
    if (configuration == null || liveData == null) return null
    return FootballMatchState(configuration, liveData)
  }

  override fun mergeInto(current: LiveActivityState): FootballMatchState? {
    val football = current as? FootballMatchState ?: return null
    return football.copy(
      configuration = configuration ?: football.configuration,
      liveData = liveData ?: football.liveData,
    )
  }
}
