package com.pushpushgo.sdk.push.liveactivity.data

/** Lifecycle status of a tracked Live Activity on this device. */
internal enum class LiveActivityStatus(
  val value: String,
) {
  ACTIVE("active"),
  ENDED("ended"),
  ;

  companion object {
    fun fromValue(value: String): LiveActivityStatus = entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: ACTIVE
  }
}
