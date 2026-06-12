package com.pushpushgo.sdk.push.liveactivity.data

enum class LiveActivityStatus(
  val value: String,
) {
  ACTIVE("active"),
  PAUSED("paused"),
  ENDED("ended"),
  ;

  companion object {
    fun fromValue(value: String): LiveActivityStatus = entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: ACTIVE
  }
}
