package com.pushpushgo.sdk.push.liveactivity.data

import com.pushpushgo.sdk.push.liveactivity.LiveActivityTemplate
import com.squareup.moshi.JsonAdapter

/**
 * Parses Live Activity payloads. The push envelope is a flat `Map<String, String>`
 * whose nested blocks are delivered as JSON strings. Those blocks and the live
 * notification document are read into DTOs with [LiveActivityJson] and mapped to
 * the domain models here.
 */
internal object LiveActivityPayloadParser {
  const val ENVELOPE_TYPE = "live_notification"

  /**
   * The only `lifecycle.status` worth rendering on catch-up: any other means the
   * activity has not started yet or is already over.
   */
  private const val LIFECYCLE_ONGOING = "ONGOING"

  private val configurationAdapter = LiveActivityJson.moshi.adapter(FootballMatchConfigurationDto::class.java)
  private val liveDataAdapter = LiveActivityJson.moshi.adapter(FootballMatchLiveDataDto::class.java)
  private val hotMessageAdapter = LiveActivityJson.moshi.adapter(HotMessageDto::class.java)
  private val documentAdapter = LiveActivityJson.moshi.adapter(LiveNotificationDocumentDto::class.java)

  fun isLiveActivityPush(data: Map<String, String>): Boolean = data["type"] == ENVELOPE_TYPE

  /**
   * Build a synthetic `start` push envelope from a live notification
   * document, so a subscriber that joined after the original `start` push can
   * render the current state. The document's `configuration` / `liveData`
   * objects are passed on as the JSON strings the push pipeline expects.
   *
   * Returns null when the document can't be parsed or the activity is not
   * currently [LIFECYCLE_ONGOING].
   */
  fun buildCatchUpEnvelope(json: String): Map<String, String>? {
    val document = documentAdapter.read(json) ?: return null
    val id = document.id.nullIfBlank() ?: return null
    val configuration = document.configuration ?: return null
    val liveData = document.liveData ?: return null
    if (document.lifecycle?.status?.uppercase() != LIFECYCLE_ONGOING) return null

    return buildMap {
      put("type", ENVELOPE_TYPE)
      put("event", LiveActivityEvent.START.value)
      put("liveNotificationId", id)
      document.template?.let { put("template", it) }
      put("configuration", configuration)
      put("liveData", liveData)
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
    val startPolicy = documentAdapter.read(json)?.startPolicy ?: return null
    val countdown = startPolicy.countdown ?: return null
    val endAtMs = startPolicy.scheduledAt ?: return null
    return CountdownInfo(message = countdown.message.nullIfBlank(), endAtMs = endAtMs)
  }

  /** Parse a football match configuration JSON object (static config block). */
  fun parseConfiguration(json: String): FootballMatchConfiguration? {
    val configuration = configurationAdapter.read(json) ?: return null
    val content = configuration.content ?: return null
    return FootballMatchConfiguration(
      content =
        FootballMatchContent(
          title = content.title.orEmpty(),
          homeTeamName = content.homeTeamName.orEmpty(),
          homeTeamImage = content.homeTeamImage.nullIfBlank(),
          awayTeamName = content.awayTeamName.orEmpty(),
          awayTeamImage = content.awayTeamImage.nullIfBlank(),
        ),
      design =
        configuration.design?.android?.let { android ->
          FootballMatchAndroidDesign(
            hasTrackerIcon = android.hasTrackerIcon ?: false,
            progressBarColor = android.progressBarColor?.toColorSet(),
            breakTimeBarColor = android.breakTimeBarColor?.toColorSet(),
          )
        },
      statusLabels = configuration.statusLabels.orEmpty(),
      actions = (configuration.actionSet ?: configuration.actions).orEmpty().mapNotNull { it?.toAction() },
      timeoutMinutes = configuration.timeout?.minutes,
      url = configuration.url.nullIfBlank(),
    )
  }

  /** Parse a football match live data JSON object (dynamic state block). */
  fun parseLiveData(json: String): FootballMatchLiveData? {
    val liveData = liveDataAdapter.read(json) ?: return null
    return FootballMatchLiveData(
      homeTeamScore = liveData.homeTeamScore ?: 0,
      awayTeamScore = liveData.awayTeamScore ?: 0,
      status = MatchPhase.fromValue(liveData.status),
      statusChangedAt = liveData.statusChangedAt,
      liveDataVersion = liveData.version ?: 0,
    )
  }

  /** Parse a hot message JSON object (`{ id, text, timestamp }`). */
  fun parseHotMessage(json: String): HotMessage? {
    val hotMessage = hotMessageAdapter.read(json) ?: return null
    val text = hotMessage.text.nullIfBlank() ?: return null
    return HotMessage(
      id = hotMessage.id.nullIfBlank() ?: text.hashCode().toString(),
      text = text,
      expiresAtMs = hotMessage.timestamp,
    )
  }

  /** A color set needs its light color; the dark one falls back to it. */
  private fun LiveActivityColorSetDto.toColorSet(): LiveActivityColorSet? {
    val light = lightMode ?: return null
    return LiveActivityColorSet(lightMode = light, darkMode = darkMode ?: light)
  }

  private fun LiveActivityActionDto.toAction(): LiveActivityAction? {
    val actionType = LiveActivityActionType.fromValue(type) ?: return null
    val actionName = name.nullIfBlank() ?: return null
    return LiveActivityAction(type = actionType, name = actionName, url = url.nullIfBlank())
  }

  /** Read [json], or null when it is not valid JSON of the expected shape. */
  private fun <T> JsonAdapter<T>.read(json: String): T? = runCatching { fromJson(json) }.getOrNull()

  private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
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
