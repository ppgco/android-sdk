package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Live Notification template identifier. Wire values mirror the backend / iOS
 * contract (e.g. `FOOTBALL_MATCH_TRACKING`).
 */
enum class LiveActivityTemplate(
  val value: String,
) {
  FOOTBALL_MATCH_TRACKING("FOOTBALL_MATCH_TRACKING"),
  ;

  companion object {
    fun fromValue(value: String?): LiveActivityTemplate? = entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
  }
}
