package com.pushpushgo.sdk.push.liveactivity.data

/** A solid color, as a hex string. */
internal data class LiveActivityColor(
  val hex: String,
)

/** Light / dark variant pair for a color. */
internal data class LiveActivityColorSet(
  val lightMode: LiveActivityColor,
  val darkMode: LiveActivityColor,
) {
  /** Resolve the hex string for the given mode. */
  fun resolve(darkMode: Boolean): String = if (darkMode) this.darkMode.hex else this.lightMode.hex
}
