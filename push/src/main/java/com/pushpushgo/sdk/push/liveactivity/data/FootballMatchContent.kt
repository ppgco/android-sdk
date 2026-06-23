package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Static content of the football match template. Field-for-field mirror of the
 * backend / iOS `content` block. Empty image strings from the backend are kept
 * as-is here and normalized to `null` at render time.
 */
data class FootballMatchContent(
  val title: String,
  val homeTeamName: String,
  val homeTeamImage: String?,
  val awayTeamName: String,
  val awayTeamImage: String?,
)
