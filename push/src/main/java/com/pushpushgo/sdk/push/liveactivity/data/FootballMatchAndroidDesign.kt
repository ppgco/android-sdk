package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Android-specific design block of the football match template. A missing
 * [progressBarColor] falls back to a built-in color, and a missing
 * [breakTimeBarColor] leaves the bar without a break section.
 */
internal data class FootballMatchAndroidDesign(
  val hasTrackerIcon: Boolean,
  val progressBarColor: LiveActivityColorSet?,
  val breakTimeBarColor: LiveActivityColorSet?,
)
