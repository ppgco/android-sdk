package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Type of a Live Activity call-to-action button:
 * - `OPEN_APP` — just launches the host app.
 * - `URL` — opens a web url or app deep link carried in `url`.
 * - `CLOSE` — dismisses the Live Activity.
 */
internal enum class LiveActivityActionType {
  OPEN_APP,
  URL,
  CLOSE,
}
