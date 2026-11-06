package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Android-specific design block of the football match template. All fields are
 * optional: the SDK falls back to built-in defaults.
 */
internal data class FootballMatchAndroidDesign(
  val hasTrackerIcon: Boolean,
  val progressBarColor: LiveActivityColorSet?,
  val breakTimeBarColor: LiveActivityColorSet?,
)
