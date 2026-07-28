package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Static configuration of a football match Live Activity, delivered in the
 * `configuration` field of the `start` push. Mirrors the backend / iOS
 * football match configuration DTO.
 */
data class FootballMatchConfiguration(
  val template: LiveActivityTemplate,
  val content: FootballMatchContent,
  val design: FootballMatchAndroidDesign?,
  val statusLabels: Map<String, String>,
  val actions: List<LiveActivityAction>,
  val timeoutMinutes: Int?,
  val url: String?,
) {
  /**
   * Display label for the given match phase. Falls back to the `OTHER` label,
   * then to [MatchPhase.displayText] when the backend provides no override.
   */
  fun label(status: MatchPhase): String =
    statusLabels[status.value]
      ?: statusLabels[MatchPhase.OTHER.value]
      ?: status.displayText
}
