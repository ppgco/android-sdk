package com.pushpushgo.sdk.push.liveactivity.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Parses the live notification push envelope: a flat `Map<String, String>` whose
 * nested objects are delivered as JSON strings.
 */
internal object LiveActivityPayloadParser {
  const val ENVELOPE_TYPE = "live_notification"

  private val moshi: Moshi = Moshi.Builder().build()
  private val mapAdapter =
    moshi.adapter<Map<String, Any?>>(
      Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

  fun isLiveActivityPush(data: Map<String, String>): Boolean = data["type"] == ENVELOPE_TYPE

  /**
   * The only `lifecycle.status` worth rendering on catch-up: any other means the
   * activity has not started yet or is already over.
   */
  private const val LIFECYCLE_ONGOING = "ONGOING"

  /**
   * Build a synthetic `start` push envelope from a live notification
   * document, so a subscriber that joined after the original `start` push can
   * render the current state. The document's nested `configuration` / `liveData`
   * objects are re-serialized to the JSON-string form the push pipeline expects.
   *
   * Returns null when the document can't be parsed or the activity is not
   * currently [LIFECYCLE_ONGOING].
   */
  fun buildCatchUpEnvelope(json: String): Map<String, String>? {
    val root = readJson(json) ?: return null
    val id = root.str("id")?.takeIf { it.isNotBlank() } ?: return null
    val configuration = root.map("configuration") ?: return null
    val liveData = root.map("liveData") ?: return null

    val lifecycleStatus = root.map("lifecycle")?.str("status")?.uppercase()
    if (lifecycleStatus != LIFECYCLE_ONGOING) return null

    return buildMap {
      put("type", ENVELOPE_TYPE)
      put("event", LiveActivityEvent.START.value)
      put("liveNotificationId", id)
      root.str("template")?.let { put("template", it) }
      put("configuration", mapAdapter.toJson(configuration))
      put("liveData", mapAdapter.toJson(liveData))
    }
  }

  /**
   * Parse the full push envelope. Returns `null` when required fields are
   * missing or the template is not supported by this SDK version.
   */
  fun parsePush(data: Map<String, String>): LiveActivityPush? {
    if (!isLiveActivityPush(data)) return null
    val id = data["liveNotificationId"]?.takeIf { it.isNotBlank() } ?: return null
    val event = LiveActivityEvent.fromValue(data["event"].orEmpty()) ?: return null
    val template = LiveActivityTemplate.fromValue(data["template"]) ?: return null

    val configurationJson = data["configuration"]?.takeIf { it.isNotBlank() }
    val liveDataJson = data["liveData"]?.takeIf { it.isNotBlank() }
    val hotMessageJson = data["hotMessage"]?.takeIf { it.isNotBlank() }

    return LiveActivityPush(
      id = id,
      projectId = data["project"].orEmpty(),
      subscriberId = data["subscriber"].orEmpty(),
      template = template,
      event = event,
      configurationJson = configurationJson,
      liveDataJson = liveDataJson,
      hotMessage = hotMessageJson?.let { parseHotMessage(it) },
    )
  }

  /**
   * Build the full state of a [template] from its `configuration` and `liveData`
   * blocks. Returns null when a block is missing or invalid, or when the template
   * has no parser (every [LiveActivityTemplate.supported] one has one).
   */
  fun parseState(
    template: LiveActivityTemplate,
    configurationJson: String?,
    liveDataJson: String?,
  ): LiveActivityState? =
    when (template) {
      LiveActivityTemplate.FOOTBALL_MATCH_TRACKING -> {
        val configuration = configurationJson?.let { parseConfiguration(it) }
        val liveData = liveDataJson?.let { parseLiveData(it) }
        if (configuration != null && liveData != null) FootballMatchState(configuration, liveData) else null
      }

      else -> null
    }

  /**
   * Apply the `configuration` and `liveData` blocks of a push onto [current]. A
   * block that is missing or invalid keeps its current value.
   */
  fun mergeState(
    current: LiveActivityState,
    configurationJson: String?,
    liveDataJson: String?,
  ): LiveActivityState =
    when (current) {
      is FootballMatchState ->
        current.copy(
          configuration = configurationJson?.let { parseConfiguration(it) } ?: current.configuration,
          liveData = liveDataJson?.let { parseLiveData(it) } ?: current.liveData,
        )
    }

  /**
   * Parse the pre-match countdown info from a live notification document. Returns
   * null when there is no countdown or no scheduled start.
   */
  fun parseStartPolicy(json: String): CountdownInfo? {
    val root = readJson(json) ?: return null
    val startPolicy = root.map("startPolicy") ?: return null
    val countdown = startPolicy.map("countdown") ?: return null
    val endAtMs = parseEpochMillis(startPolicy["scheduledAt"]) ?: return null
    return CountdownInfo(
      message = countdown.str("message").nullIfBlank(),
      endAtMs = endAtMs,
    )
  }

  /** Parse a football match configuration JSON object (static config block). */
  fun parseConfiguration(json: String): FootballMatchConfiguration? {
    val root = readJson(json) ?: return null
    val contentMap = root.map("content") ?: return null

    val content =
      FootballMatchContent(
        title = contentMap.str("title").orEmpty(),
        homeTeamName = contentMap.str("homeTeamName").orEmpty(),
        homeTeamImage = contentMap.str("homeTeamImage").nullIfBlank(),
        awayTeamName = contentMap.str("awayTeamName").orEmpty(),
        awayTeamImage = contentMap.str("awayTeamImage").nullIfBlank(),
      )

    val androidDesign =
      root.map("design")?.map("android")?.let { d ->
        FootballMatchAndroidDesign(
          hasTrackerIcon = (d["hasTrackerIcon"] as? Boolean) ?: false,
          progressBarColor = parseColorSet(d["progressBarColor"]),
          breakTimeBarColor = parseColorSet(d["breakTimeBarColor"]),
        )
      }

    val statusLabels = parseStatusLabels(root["statusLabels"])

    val actionsNode = root["actionSet"] ?: root["actions"]
    val actions = (actionsNode as? List<*>)?.mapNotNull { parseAction(it) } ?: emptyList()

    val timeoutMinutes = root.map("timeout")?.get("minutes").asIntOrNull()

    return FootballMatchConfiguration(
      content = content,
      design = androidDesign,
      statusLabels = statusLabels,
      actions = actions,
      timeoutMinutes = timeoutMinutes,
      url = root.str("url").nullIfBlank(),
    )
  }

  /** Parse a football match live data JSON object (dynamic state block). */
  fun parseLiveData(json: String): FootballMatchLiveData? {
    val root = readJson(json) ?: return null
    return FootballMatchLiveData(
      homeTeamScore = root["homeTeamScore"].asIntOrNull() ?: 0,
      awayTeamScore = root["awayTeamScore"].asIntOrNull() ?: 0,
      status = MatchPhase.fromValue(root.str("status")),
      statusChangedAt = parseEpochMillis(root["statusChangedAt"]),
      liveDataVersion = root["version"].asIntOrNull() ?: 0,
    )
  }

  /** Parse a hot message JSON object (`{ id, text, timestamp }`). */
  fun parseHotMessage(json: String): HotMessage? {
    val root = readJson(json) ?: return null
    val text = root.str("text").nullIfBlank() ?: return null
    return HotMessage(
      id = root.str("id").nullIfBlank() ?: text.hashCode().toString(),
      text = text,
      expiresAtMs = parseEpochMillis(root["timestamp"]),
    )
  }

  /**
   * Parse the `statusLabels` block: either an array of labels ordered exactly like
   * [MatchPhase] (index 0 = PRE_MATCH … 15 = OTHER), or a keyed `{phase: label}` object.
   */
  private fun parseStatusLabels(node: Any?): Map<String, String> =
    when (node) {
      is List<*> ->
        node
          .mapIndexedNotNull { index, label ->
            val phase = MatchPhase.entries.getOrNull(index) ?: return@mapIndexedNotNull null
            (label as? String)?.nullIfBlank()?.let { phase.value to it }
          }.toMap()
      is Map<*, *> ->
        node.entries
          .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? String)?.let { key to it } } }
          .toMap()
      else -> emptyMap()
    }

  private fun parseAction(node: Any?): LiveActivityAction? {
    val m = node as? Map<*, *> ?: return null
    val type = LiveActivityActionType.fromValue(m["type"] as? String) ?: return null
    val name = (m["name"] as? String)?.nullIfBlank() ?: return null
    return LiveActivityAction(type = type, name = name, url = (m["url"] as? String).nullIfBlank())
  }

  private fun parseColorSet(node: Any?): LiveActivityColorSet? {
    val m = node as? Map<*, *> ?: return null
    val light = parseColor(m["lightMode"]) ?: return null
    val dark = parseColor(m["darkMode"]) ?: light
    return LiveActivityColorSet(lightMode = light, darkMode = dark)
  }

  /** A color is either a plain hex string, `{hex}`, or a gradient `{fromHex,...}`. */
  private fun parseColor(node: Any?): LiveActivityColor? =
    when (node) {
      is String -> node.nullIfBlank()?.let { LiveActivityColor(it) }
      is Map<*, *> ->
        ((node["hex"] as? String) ?: (node["fromHex"] as? String))
          .nullIfBlank()
          ?.let { LiveActivityColor(it) }
      else -> null
    }

  private fun readJson(json: String): Map<String, Any?>? = runCatching { mapAdapter.fromJson(json) }.getOrNull()

  // Map / value helpers

  private fun Map<String, Any?>.str(key: String): String? = this[key] as? String

  private fun Map<String, Any?>.map(key: String): Map<String, Any?>? {
    @Suppress("UNCHECKED_CAST")
    return this[key] as? Map<String, Any?>
  }

  private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

  private fun Any?.asIntOrNull(): Int? =
    when (this) {
      is Number -> toInt()
      is String -> toIntOrNull() ?: toDoubleOrNull()?.toInt()
      else -> null
    }

  /**
   * Convert a timestamp to epoch millis. Accepts ISO-8601 strings, epoch
   * seconds, or epoch millis (numeric values < 10^12 are treated as seconds).
   */
  private fun parseEpochMillis(value: Any?): Long? =
    when (value) {
      is Number -> value.toLong().let { if (it < 1_000_000_000_000L) it * 1000L else it }
      is String ->
        value.nullIfBlank()?.let { s ->
          s.toLongOrNull()?.let { if (it < 1_000_000_000_000L) it * 1000L else it }
            ?: runCatching {
              java.time.OffsetDateTime
                .parse(s)
                .toInstant()
                .toEpochMilli()
            }.getOrNull()
            ?: runCatching {
              java.time.Instant
                .parse(s)
                .toEpochMilli()
            }.getOrNull()
        }
      else -> null
    }
}

/** Pre-match countdown of a live notification. */
internal data class CountdownInfo(
  val message: String?,
  val endAtMs: Long,
)

/**
 * Parsed representation of a live notification push envelope. The template-specific
 * blocks stay as raw JSON: they are parsed into a [LiveActivityState] when applied
 * and kept by persistence to rebuild the state.
 */
internal data class LiveActivityPush(
  val id: String,
  val projectId: String,
  val subscriberId: String,
  val template: LiveActivityTemplate,
  val event: LiveActivityEvent,
  val configurationJson: String?,
  val liveDataJson: String?,
  val hotMessage: HotMessage?,
)
