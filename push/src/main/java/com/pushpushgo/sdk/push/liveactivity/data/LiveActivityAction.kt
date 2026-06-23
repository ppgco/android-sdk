package com.pushpushgo.sdk.push.liveactivity.data

/**
 * A call-to-action button rendered on the Live Activity. Mirrors an element of
 * the backend / iOS `actionSet` (`{ type, name, url? }`). The platform-specific
 * `design` block from the backend is intentionally not modeled here — Android
 * notification actions do not support custom button styling.
 */
data class LiveActivityAction(
  val type: LiveActivityActionType,
  val name: String,
  val url: String?,
)
