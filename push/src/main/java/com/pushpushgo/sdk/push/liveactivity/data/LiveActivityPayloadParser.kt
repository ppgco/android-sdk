package com.pushpushgo.sdk.push.liveactivity.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Parses the unified PushPushGo "live notification" push envelope shared by the
 * iOS and Android SDKs.
 *
 * The FCM data message is a flat `Map<String, String>` whose nested objects are
 * delivered as JSON strings:
 * ```
 * type               = "live_notification"
 * liveNotificationId = "<id>"
 * event              = "start" | "update" | "end"
 * template           = "FOOTBALL_MATCH_TRACKING"
 * project            = "<projectId>"
 * subscriber         = "<subscriberId>"
 * configuration      = "{ ...static config... }"   // only on `start`
 * liveData           = "{ ...dynamic state... }"
 * hotMessage         = "{ id, text, timestamp }"    // optional
 * ```
 * The `configuration` / `liveData` JSON mirror the iOS DTOs field-for-field.
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
   * The only `lifecycle.status` (from the GET live-notification document) that is
   * currently live and worth rendering on catch-up. The others
   * (`DRAFT`, `PENDING`, `STOPPED`, `EXPIRED`) are either not started yet or
   * already over, so there is nothing to show.
   */
  private const val LIFECYCLE_ONGOING = "ONGOING"

  /**
   * Build a synthetic `start` push envelope from a GET live-notification
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

  /** Parse the full push envelope. Returns `null` when required fields are missing. */
  fun parsePush(data: Map<String, String>): LiveActivityPush? {
    if (!isLiveActivityPush(data)) return null
    val id = data["liveNotificationId"]?.takeIf { it.isNotBlank() } ?: return null
    val event = LiveActivityEvent.fromValue(data["event"].orEmpty()) ?: return null
    val template =
      LiveActivityTemplate.fromValue(data["template"])
        ?: LiveActivityTemplate.FOOTBALL_MATCH_TRACKING

    val configurationJson = data["configuration"]?.takeIf { it.isNotBlank() }
    val liveDataJson = data["liveData"]?.takeIf { it.isNotBlank() }
    val hotMessageJson = data["hotMessage"]?.takeIf { it.isNotBlank() }

    return LiveActivityPush(
      id = id,
      projectId = data["project"].orEmpty(),
      subscriberId = data["subscriber"].orEmpty(),
      template = template,
      event = event,
      configuration = configurationJson?.let { parseConfiguration(it) },
      configurationJson = configurationJson,
      liveData = liveDataJson?.let { parseLiveData(it) },
      liveDataJson = liveDataJson,
      hotMessage = hotMessageJson?.let { parseHotMessage(it) },
    )
  }

  /**
   * Parse the `startPolicy` block of a GET live-notification document into the
   * pre-match countdown info. Returns null when the campaign has no countdown
   * or no scheduled start. `endAtMs` is the `scheduledAt` moment — the backend
   * sends the `start` push `countdown.seconds` ahead of it, and the client
   * counts down to zero.
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

    // Backend sends `actionSet`; the local-simulation envelope uses `actions`.
    val actionsNode = root["actionSet"] ?: root["actions"]
    val actions = (actionsNode as? List<*>)?.mapNotNull { parseAction(it) } ?: emptyList()

    val timeoutMinutes = root.map("timeout")?.get("minutes").asIntOrNull()

    return FootballMatchConfiguration(
      template =
        LiveActivityTemplate.fromValue(root.str("type"))
          ?: LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
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
   * Parse the `statusLabels` block. The backend now sends a plain array of
   * labels ordered exactly like [MatchPhase] (index 0 = PRE_MATCH … 15 = OTHER)
   * to shrink the payload; the local-simulation envelope still sends a keyed
   * `{phase: label}` object. Both are supported.
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

/** Pre-match countdown extracted from a live notification's `startPolicy`. */
internal data class CountdownInfo(
  val message: String?,
  val endAtMs: Long,
)

/**
 * Parsed representation of a live notification push envelope. Carries both the
 * typed models and the raw JSON (needed by persistence to rebuild state).
 */
internal data class LiveActivityPush(
  val id: String,
  val projectId: String,
  val subscriberId: String,
  val template: LiveActivityTemplate,
  val event: LiveActivityEvent,
  val configuration: FootballMatchConfiguration?,
  val configurationJson: String?,
  val liveData: FootballMatchLiveData?,
  val liveDataJson: String?,
  val hotMessage: HotMessage?,
)
