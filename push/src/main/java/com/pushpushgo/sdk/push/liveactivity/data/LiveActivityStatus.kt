package com.pushpushgo.sdk.push.liveactivity.data

/** Lifecycle status of a [LiveActivity] on this device. */
enum class LiveActivityStatus(
  val value: String,
) {
  ACTIVE("active"),
  PAUSED("paused"),
  ENDED("ended"),
  ;

  internal companion object {
    fun fromValue(value: String): LiveActivityStatus = entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: ACTIVE
  }
}
