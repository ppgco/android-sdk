package com.pushpushgo.sdk.push.liveactivity.data

/**
 * A Live Activity currently tracked on this device.
 *
 * Exposes only the fields shared by every template: the SDK renders and updates
 * the notification on its own, so template-specific data stays internal. This
 * keeps the type stable when new templates are added in minor releases.
 */
class LiveActivity internal constructor(
  /** Backend ID of the live notification. */
  val id: String,
  /** Template the activity is rendered with. */
  val template: LiveActivityTemplate,
  val status: LiveActivityStatus,
  /** Epoch millis the activity was first shown on this device. */
  val createdAt: Long,
  /** Epoch millis of the last update applied on this device. */
  val updatedAt: Long,
) {
  override fun toString(): String = "LiveActivity(id=$id, template=$template, status=$status, createdAt=$createdAt, updatedAt=$updatedAt)"
}
