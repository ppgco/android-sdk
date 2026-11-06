package com.pushpushgo.sdk.push.liveactivity.data

/** Lifecycle event delivered in the `event` field of a live notification push. */
internal enum class LiveActivityEvent(
  val value: String,
) {
  START("start"),
  UPDATE("update"),
  END("end"),
  ;

  companion object {
    fun fromValue(value: String): LiveActivityEvent? = entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
  }
}
