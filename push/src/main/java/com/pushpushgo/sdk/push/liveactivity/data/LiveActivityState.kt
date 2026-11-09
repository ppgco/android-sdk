package com.pushpushgo.sdk.push.liveactivity.data

import com.pushpushgo.sdk.push.liveactivity.LiveActivityTemplate

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
}
