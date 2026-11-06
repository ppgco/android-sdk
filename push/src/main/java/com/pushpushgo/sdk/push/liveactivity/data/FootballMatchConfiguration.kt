package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Static configuration of a football match Live Activity, delivered in the
 * `configuration` field of the `start` push.
 */
internal data class FootballMatchConfiguration(
  val content: FootballMatchContent,
  val design: FootballMatchAndroidDesign?,
  val statusLabels: Map<String, String>,
  val actions: List<LiveActivityAction>,
  val timeoutMinutes: Int?,
  val url: String?,
) {
  /**
   * Display label for the given match phase. Falls back to the `OTHER` label,
   * then to [MatchPhase.displayText] when no label is provided.
   */
  fun label(status: MatchPhase): String =
    statusLabels[status.value]
      ?: statusLabels[MatchPhase.OTHER.value]
      ?: status.displayText
}
