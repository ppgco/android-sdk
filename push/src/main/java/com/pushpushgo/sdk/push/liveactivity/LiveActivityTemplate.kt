package com.pushpushgo.sdk.push.liveactivity

/** Identifier of a Live Activity template. */
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

    /** The template with the given value (case-insensitive), or null when this SDK version doesn't support it. */
    internal fun fromValue(value: String?): LiveActivityTemplate? = supported.firstOrNull { it.value.equals(value, ignoreCase = true) }
  }
}
