package com.pushpushgo.sdk.push.liveactivity.data

/**
 * Live Notification template identifier. Wire values mirror the backend / iOS
 * contract (e.g. `FOOTBALL_MATCH_TRACKING`).
 *
 * Not an enum on purpose: new templates are added in minor releases, so code
 * matching on it should handle other values too (e.g. an `else` branch).
 */
class LiveActivityTemplate private constructor(
  val value: String,
) {
  override fun equals(other: Any?): Boolean = other is LiveActivityTemplate && other.value == value

  override fun hashCode(): Int = value.hashCode()

  override fun toString(): String = value

  companion object {
    @JvmField
    val FOOTBALL_MATCH_TRACKING = LiveActivityTemplate("FOOTBALL_MATCH_TRACKING")

    /** Templates rendered by this SDK version. */
    internal val supported: List<LiveActivityTemplate> = listOf(FOOTBALL_MATCH_TRACKING)

    /** The template with the given wire value (case-insensitive), or null when this SDK version doesn't support it. */
    internal fun fromValue(value: String?): LiveActivityTemplate? = supported.firstOrNull { it.value.equals(value, ignoreCase = true) }
  }
}
