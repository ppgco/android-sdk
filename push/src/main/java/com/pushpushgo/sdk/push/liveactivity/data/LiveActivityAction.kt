package com.pushpushgo.sdk.push.liveactivity.data

/** A call-to-action button rendered on the Live Activity. */
internal data class LiveActivityAction(
  val type: LiveActivityActionType,
  val name: String,
  val url: String?,
)
