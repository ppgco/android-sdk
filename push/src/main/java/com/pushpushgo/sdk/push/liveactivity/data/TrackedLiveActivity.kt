package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Internal, template-agnostic representation of a tracked Live Activity.
 * Combines the lifecycle fields shared by every template with the
 * template-specific [state]. The public [LiveActivity] is a projection of it
 * (see [toLiveActivity]).
 */
internal data class TrackedLiveActivity(
  val id: String,
  val projectId: String,
  val subscriberId: String,
  val template: LiveActivityTemplate,
  val status: LiveActivityStatus,
  val state: LiveActivityState,
  val hotMessage: HotMessage?,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
) {
  val liveDataVersion: Int
    get() = state.liveDataVersion

  /** Public projection exposed by `LiveActivities.getActiveActivities()`. */
  fun toLiveActivity(): LiveActivity =
    LiveActivity(
      id = id,
      template = template,
      status = status,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
}
