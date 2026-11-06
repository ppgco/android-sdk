package com.pushpushgo.sdk.push.liveactivity.data

/** A Live Activity currently tracked on this device. */
class LiveActivity internal constructor(
  /** ID of the live notification. */
  val id: String,
  /** Template the activity is rendered with. */
  val template: LiveActivityTemplate,
) {
  override fun toString(): String = "LiveActivity(id=$id, template=$template)"
}
