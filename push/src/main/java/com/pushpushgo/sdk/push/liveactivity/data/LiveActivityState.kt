package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Template-specific state of a tracked Live Activity: the static configuration
 * plus the latest live data. Each [LiveActivityTemplate] has its own
 * implementation; the fields below are the parts the generic handler needs to
 * build intents and report analytics without knowing the template.
 */
internal sealed interface LiveActivityState {
  /** Version of the current live data, echoed back in analytics events. */
  val liveDataVersion: Int

  /** Deep link opened by a tap on the notification body. */
  val url: String?

  /** Call-to-action buttons rendered on the notification. */
  val actions: List<LiveActivityAction>

  /**
   * Epoch millis at which the SDK ends the activity on its own (for templates
   * with a fixed end time), or null when only the backend ends it.
   */
  val autoEndAtMs: Long?
}

/**
 * Template-specific blocks parsed from one push. Either block may be absent
 * (an `update` usually carries only `liveData`), so the state is either built
 * from scratch ([toInitialState]) or merged onto the tracked one ([mergeInto]).
 */
internal sealed interface LiveActivityStateUpdate {
  /** Full state when the push carried every block a `start` needs, else null. */
  fun toInitialState(): LiveActivityState?

  /**
   * Apply this update onto [current]; blocks missing from the push keep their
   * current value. Returns null when [current] belongs to another template.
   */
  fun mergeInto(current: LiveActivityState): LiveActivityState?
}
