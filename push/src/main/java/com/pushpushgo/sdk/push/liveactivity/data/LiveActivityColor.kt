package com.pushpushgo.sdk.push.liveactivity.data

/**
 * A single color value resolved to a hex string. Mirrors the backend / iOS
 * `IColor` type: a solid `hex` or a gradient (for which Android uses `fromHex`
 * as the representative solid color, since [android.app.Notification.ProgressStyle]
 * segments only support solid colors).
 */
data class LiveActivityColor(
  val hex: String,
)

/**
 * Light / dark variant pair for a color. Mirrors the backend / iOS
 * `IColorSet` (`{ lightMode, darkMode }`).
 */
data class LiveActivityColorSet(
  val lightMode: LiveActivityColor,
  val darkMode: LiveActivityColor,
) {
  /** Resolve the hex string for the given mode. */
  fun resolve(darkMode: Boolean): String = if (darkMode) this.darkMode.hex else this.lightMode.hex
}
