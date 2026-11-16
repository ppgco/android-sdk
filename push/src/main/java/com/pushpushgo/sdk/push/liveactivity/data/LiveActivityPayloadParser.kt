package com.pushpushgo.sdk.push.liveactivity.data

import com.pushpushgo.sdk.push.liveactivity.LiveActivityTemplate
import com.pushpushgo.sdk.push.utils.logWarning
import com.squareup.moshi.JsonAdapter

/**
 * Parses Live Activity payloads. The push envelope is a flat `Map<String, String>`
 * whose nested blocks are delivered as JSON strings. Those blocks are read into DTOs
 * with [LiveActivityJson] and mapped to the domain models here. The live notification
 * document carries the same data in another format and is converted to push blocks,
 * so everything downstream handles one format. A block that breaks the backend
 * contract is rejected as a whole, and the reason is logged.
 */
internal object LiveActivityPayloadParser {
  const val ENVELOPE_TYPE = "live_notification"

  /**
   * The only `lifecycle.status` worth rendering on catch-up: any other means the
   * activity has not started yet or is already over.
   */
  private const val LIFECYCLE_ONGOING = "ONGOING"

  private val configurationAdapter = LiveActivityJson.moshi.adapter(LiveActivityConfigurationDto::class.java)
  private val liveDataAdapter = LiveActivityJson.moshi.adapter(LiveActivityLiveDataDto::class.java)
  private val hotMessageAdapter = LiveActivityJson.moshi.adapter(HotMessageDto::class.java)
  private val blockAdapter = LiveActivityJson.moshi.adapter(LiveActivityBlockDto::class.java)
  private val documentAdapter = LiveActivityJson.moshi.adapter(LiveNotificationDocumentDto::class.java)

  fun isLiveActivityPush(data: Map<String, String>): Boolean = data["type"] == ENVELOPE_TYPE

  /**
   * Build a synthetic `start` push envelope from a live notification
   * document, so a subscriber that joined after the original `start` push can
   * render the current state. The document's `configuration` / `liveData`
   * objects are converted to the blocks a push carries.
   *
   * Returns null when the document breaks the contract or the activity is not
   * currently [LIFECYCLE_ONGOING].
   */
  fun buildCatchUpEnvelope(json: String): Map<String, String>? {
    val document = documentAdapter.read(json, "document") ?: return null
    if (document.lifecycle.status != LIFECYCLE_ONGOING) return null

    return mapOf(
      "type" to ENVELOPE_TYPE,
      "event" to LiveActivityEvent.START.value,
      "liveNotificationId" to document.id,
      "configuration" to configurationAdapter.toJson(document.configuration.toPushBlock()),
      "liveData" to liveDataAdapter.toJson(document.liveData.toPushBlock()),
    )
  }

  /**
   * Name of the template a push is for. The envelope doesn't state it, so it is
   * read from the blocks. Null for a push without blocks, like `end`.
   */
  fun templateName(data: Map<String, String>): String? =
    listOfNotNull(data["configuration"], data["liveData"])
      .firstNotNullOfOrNull { block -> runCatching { blockAdapter.fromJson(block)?.template }.getOrNull() }

  /**
   * Parse the full push envelope. Returns `null` when required fields are
   * missing or its blocks are of a template this SDK version doesn't support.
   */
  fun parsePush(data: Map<String, String>): LiveActivityPush? {
    if (!isLiveActivityPush(data)) return null
    val id = data["liveNotificationId"]?.takeIf { it.isNotBlank() } ?: return null
    val event = LiveActivityEvent.fromValue(data["event"].orEmpty()) ?: return null

    val configurationJson = data["configuration"]?.takeIf { it.isNotBlank() }
    val liveDataJson = data["liveData"]?.takeIf { it.isNotBlank() }
    val hotMessageJson = data["hotMessage"]?.takeIf { it.isNotBlank() }

    val template = LiveActivityTemplate.fromValue(templateName(data))
    if (template == null && (configurationJson != null || liveDataJson != null)) return null

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
   * blocks. Returns null when a block is missing or breaks the contract, or when
   * the template has no parser (every [LiveActivityTemplate.supported] one has one).
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
   * block that is missing or breaks the contract keeps its current value.
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
   * null when the document has no countdown.
   */
  fun parseStartPolicy(json: String): CountdownInfo? {
    val startPolicy = documentAdapter.read(json, "document")?.startPolicy ?: return null
    val countdown = startPolicy.countdown ?: return null
    return CountdownInfo(message = countdown.message.nullIfBlank(), endAtMs = startPolicy.scheduledAt.time)
  }

  /** Parse the `configuration` block of a football match. */
  fun parseConfiguration(json: String): FootballMatchConfiguration? =
    when (val configuration = configurationAdapter.read(json, "configuration")) {
      is FootballMatchConfigurationDto -> configuration.toConfiguration()
      null -> null
    }

  /** Parse the `liveData` block of a football match. */
  fun parseLiveData(json: String): FootballMatchLiveData? =
    when (val liveData = liveDataAdapter.read(json, "liveData")) {
      is FootballMatchLiveDataDto -> liveData.toLiveData()
      null -> null
    }

  /** Parse the `hotMessage` block of a push. */
  fun parseHotMessage(json: String): HotMessage? {
    val hotMessage = hotMessageAdapter.read(json, "hotMessage") ?: return null
    val text = hotMessage.text.nullIfBlank() ?: return null
    return HotMessage(id = hotMessage.id, text = text, expiresAtMs = hotMessage.expiresAt.time)
  }

  private fun FootballMatchConfigurationDto.toConfiguration() =
    FootballMatchConfiguration(
      content =
        FootballMatchContent(
          title = content.title,
          homeTeamName = content.homeTeamName,
          homeTeamImage = content.homeTeamImage.nullIfBlank(),
          awayTeamName = content.awayTeamName,
          awayTeamImage = content.awayTeamImage.nullIfBlank(),
        ),
      design =
        FootballMatchAndroidDesign(
          hasTrackerIcon = design.android.hasTrackerIcon,
          progressBarColor = design.android.progressBarColor.toColorSet(),
          breakTimeBarColor = design.android.breakTimeBarColor?.toColorSet(),
        ),
      statusLabels =
        MatchPhase.entries
          .zip(statusLabels)
          .filter { (_, label) -> label.isNotBlank() }
          .associate { (phase, label) -> phase.value to label },
      actions = actionSet.mapNotNull { it?.toAction() },
      timeoutMinutes = timeout.minutes,
      url = url.nullIfBlank(),
    )

  private fun FootballMatchLiveDataDto.toLiveData() =
    FootballMatchLiveData(
      homeTeamScore = homeTeamScore,
      awayTeamScore = awayTeamScore,
      status = status,
      statusChangedAt = statusChangedAt.time,
      liveDataVersion = version,
    )

  private fun LiveActivityColorSetDto.toColorSet() =
    LiveActivityColorSet(lightMode = LiveActivityColor(lightMode.hex), darkMode = LiveActivityColor(darkMode.hex))

  private fun LiveActivityActionDto.toAction(): LiveActivityAction =
    when (this) {
      is UrlActionDto -> LiveActivityAction(LiveActivityActionType.URL, name, url)
      is OpenAppActionDto -> LiveActivityAction(LiveActivityActionType.OPEN_APP, name, url = null)
      is CloseActionDto -> LiveActivityAction(LiveActivityActionType.CLOSE, name, url = null)
    }

  /** The `configuration` of a document as the block a push carries. */
  private fun LiveNotificationConfigurationDto.toPushBlock(): LiveActivityConfigurationDto =
    when (this) {
      is FootballMatchDocumentConfigurationDto ->
        FootballMatchConfigurationDto(
          content = content,
          design = design,
          statusLabels = MatchPhase.entries.map { statusLabels[it.value].orEmpty() },
          actionSet = actions,
          timeout = timeout,
          url = url,
        )
    }

  /** The `liveData` of a document as the block a push carries. The document has no version. */
  private fun LiveNotificationLiveDataDto.toPushBlock(): LiveActivityLiveDataDto =
    when (this) {
      is FootballMatchDocumentLiveDataDto ->
        FootballMatchLiveDataDto(
          homeTeamScore = homeTeamScore,
          awayTeamScore = awayTeamScore,
          status = status,
          statusChangedAt = statusChangedAt,
          version = 0,
        )
    }

  /** Read [json], or log why it breaks the contract of the [block] and return null. */
  private fun <T> JsonAdapter<T>.read(
    json: String,
    block: String,
  ): T? =
    runCatching { fromJson(json) }
      .onFailure { logWarning("Live Activity $block rejected: ${it.message}") }
      .getOrNull()

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
  /** Template of the blocks; null for a push without blocks, like `end`. */
  val template: LiveActivityTemplate?,
  val event: LiveActivityEvent,
  val configurationJson: String?,
  val liveDataJson: String?,
  val hotMessage: HotMessage?,
)
