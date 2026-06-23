package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Android-specific design block of the football match template. Mirrors the
 * backend / iOS `design.android` object. All fields are optional so the SDK
 * falls back to built-in defaults when the backend omits them.
 */
data class FootballMatchAndroidDesign(
  val hasTrackerIcon: Boolean,
  val progressBarColor: LiveActivityColorSet?,
  val breakTimeBarColor: LiveActivityColorSet?,
)
