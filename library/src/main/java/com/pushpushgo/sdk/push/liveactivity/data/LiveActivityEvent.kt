package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Lifecycle event delivered in the `event` field of a Live Notification push.
 * Mirrors the backend / iOS contract (`start` / `update` / `end`).
 */
enum class LiveActivityEvent(val value: String) {
  START("start"),
  UPDATE("update"),
  END("end"),
  ;

  companion object {
    fun fromValue(value: String): LiveActivityEvent? =
      entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
  }
}
