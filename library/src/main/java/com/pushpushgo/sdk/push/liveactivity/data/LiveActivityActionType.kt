package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Type of a Live Activity call-to-action button. Mirrors the backend / iOS
 * `LiveNotificationActionType`:
 * - `OPEN_APP` — just launches the host app.
 * - `REDIRECT` — opens a web url or app deep link carried in `url`.
 * - `CLOSE` — dismisses the Live Activity.
 *
 * `URL` is kept as a legacy alias of `REDIRECT`.
 */
enum class LiveActivityActionType(val value: String) {
  OPEN_APP("OPEN_APP"),
  REDIRECT("REDIRECT"),
  URL("URL"),
  CLOSE("CLOSE"),
  ;

  companion object {
    fun fromValue(value: String?): LiveActivityActionType? =
      entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
  }
}
