package com.pushpushgo.sdk.push.liveactivity.data

import com.squareup.moshi.JsonClass
import java.util.Date

// Live Activity payloads as the backend sends them. The same data comes in two formats:
// the blocks of a push, whose subtype is named by `template`, and the live notification
// document, whose blocks name it by `type`. LiveActivityJson registers the subtypes.
// A key is required unless its comment says it is optional, a value is nullable only
// where the backend sends null, and only the fields Android uses are declared.

// Blocks of a push

/** `configuration` block of a push. */
internal sealed interface LiveActivityConfigurationDto

@JsonClass(generateAdapter = true)
internal data class FootballMatchConfigurationDto(
  val content: FootballMatchContentDto,
  val design: FootballMatchDesignDto,
  /** Labels of the match phases, in the order of [MatchPhase]. */
  val statusLabels: List<String>,
  /** An action of a type this SDK version doesn't know is null. */
  val actionSet: List<LiveActivityActionDto?>,
  val timeout: LiveActivityTimeoutDto,
  /**
   * Deep link of the notification. Optional: a push may come without it, while a block
   * built from the document always has it.
   */
  val url: String?,
) : LiveActivityConfigurationDto

/** `liveData` block of a push. */
internal sealed interface LiveActivityLiveDataDto

@JsonClass(generateAdapter = true)
internal data class FootballMatchLiveDataDto(
  val homeTeamScore: Int,
  val awayTeamScore: Int,
  val status: MatchPhase,
  val statusChangedAt: Date,
  val version: Int,
) : LiveActivityLiveDataDto

/** `hotMessage` block of a push. */
@JsonClass(generateAdapter = true)
internal data class HotMessageDto(
  val id: String,
  val text: String,
  val expiresAt: Date,
)

/** The part of a push block that names its template. */
@JsonClass(generateAdapter = true)
internal data class LiveActivityBlockDto(
  val template: String?,
)

// Live notification document

/** Document of `GET /core/v1/android/projects/{project}/live-notifications/{id}`. */
@JsonClass(generateAdapter = true)
internal data class LiveNotificationDocumentDto(
  val id: String,
  val configuration: LiveNotificationConfigurationDto,
  val liveData: LiveNotificationLiveDataDto,
  val lifecycle: LiveNotificationLifecycleDto,
  val startPolicy: LiveNotificationStartPolicyDto,
)

/** `configuration` of a document. */
internal sealed interface LiveNotificationConfigurationDto

@JsonClass(generateAdapter = true)
internal data class FootballMatchDocumentConfigurationDto(
  val url: String,
  val content: FootballMatchContentDto,
  val design: FootballMatchDesignDto,
  /** Label of every match phase, keyed by the phase. */
  val statusLabels: Map<String, String>,
  /** An action of a type this SDK version doesn't know is null. */
  val actions: List<LiveActivityActionDto?>,
  val timeout: LiveActivityTimeoutDto,
) : LiveNotificationConfigurationDto

/** `liveData` of a document. It has no version. */
internal sealed interface LiveNotificationLiveDataDto

@JsonClass(generateAdapter = true)
internal data class FootballMatchDocumentLiveDataDto(
  val homeTeamScore: Int,
  val awayTeamScore: Int,
  val status: MatchPhase,
  val statusChangedAt: Date,
) : LiveNotificationLiveDataDto

@JsonClass(generateAdapter = true)
internal data class LiveNotificationLifecycleDto(
  val status: String,
)

@JsonClass(generateAdapter = true)
internal data class LiveNotificationStartPolicyDto(
  val scheduledAt: Date,
  /** Null when no pre-match countdown is configured. */
  val countdown: LiveNotificationCountdownDto?,
)

@JsonClass(generateAdapter = true)
internal data class LiveNotificationCountdownDto(
  val message: String,
)

// Parts both formats share

@JsonClass(generateAdapter = true)
internal data class FootballMatchContentDto(
  val title: String,
  val homeTeamName: String,
  /** Null when the team has no image. */
  val homeTeamImage: String?,
  val awayTeamName: String,
  /** Null when the team has no image. */
  val awayTeamImage: String?,
)

@JsonClass(generateAdapter = true)
internal data class FootballMatchDesignDto(
  val android: FootballMatchAndroidDesignDto,
)

@JsonClass(generateAdapter = true)
internal data class FootballMatchAndroidDesignDto(
  val hasTrackerIcon: Boolean,
  val progressBarColor: LiveActivityColorSetDto,
  /** Null when the break bar has no color of its own. */
  val breakTimeBarColor: LiveActivityColorSetDto?,
)

@JsonClass(generateAdapter = true)
internal data class LiveActivityColorSetDto(
  val lightMode: LiveActivityColorDto,
  val darkMode: LiveActivityColorDto,
)

/** A solid color; Android designs have no gradients. */
@JsonClass(generateAdapter = true)
internal data class LiveActivityColorDto(
  val hex: String,
)

/** Call-to-action button of a configuration. */
internal sealed interface LiveActivityActionDto {
  val name: String
}

@JsonClass(generateAdapter = true)
internal data class UrlActionDto(
  override val name: String,
  val url: String,
) : LiveActivityActionDto

@JsonClass(generateAdapter = true)
internal data class OpenAppActionDto(
  override val name: String,
) : LiveActivityActionDto

@JsonClass(generateAdapter = true)
internal data class CloseActionDto(
  override val name: String,
) : LiveActivityActionDto

@JsonClass(generateAdapter = true)
internal data class LiveActivityTimeoutDto(
  val minutes: Int,
)
