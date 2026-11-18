package com.pushpushgo.sdk.push.liveactivity.data

import com.pushpushgo.sdk.push.liveactivity.LiveActivityTemplate
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import java.util.Date

/** Moshi for Live Activity payloads: picks the DTO subtypes and reads timestamps as ISO-8601. */
internal object LiveActivityJson {
  /** Key that names the template of a push block. */
  private const val TEMPLATE = "template"

  /** Key that names the template of a document block, and the type of an action. */
  private const val TYPE = "type"

  private val FOOTBALL_MATCH = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING.value

  val moshi: Moshi =
    Moshi
      .Builder()
      .add(
        PolymorphicJsonAdapterFactory
          .of(LiveActivityConfigurationDto::class.java, TEMPLATE)
          .withSubtype(FootballMatchConfigurationDto::class.java, FOOTBALL_MATCH),
      ).add(
        PolymorphicJsonAdapterFactory
          .of(LiveActivityLiveDataDto::class.java, TEMPLATE)
          .withSubtype(FootballMatchLiveDataDto::class.java, FOOTBALL_MATCH),
      ).add(
        PolymorphicJsonAdapterFactory
          .of(LiveNotificationConfigurationDto::class.java, TYPE)
          .withSubtype(FootballMatchDocumentConfigurationDto::class.java, FOOTBALL_MATCH),
      ).add(
        PolymorphicJsonAdapterFactory
          .of(LiveNotificationLiveDataDto::class.java, TYPE)
          .withSubtype(FootballMatchDocumentLiveDataDto::class.java, FOOTBALL_MATCH),
      ).add(
        // An action type added after this SDK version is skipped instead of failing the configuration.
        PolymorphicJsonAdapterFactory
          .of(LiveActivityActionDto::class.java, TYPE)
          .withSubtype(UrlActionDto::class.java, "URL")
          .withSubtype(OpenAppActionDto::class.java, "OPEN_APP")
          .withSubtype(CloseActionDto::class.java, "CLOSE")
          .withDefaultValue(null),
      )
      // A match phase added after this SDK version reads as OTHER.
      .add(MatchPhase::class.java, EnumJsonAdapter.create(MatchPhase::class.java).withUnknownFallback(MatchPhase.OTHER))
      .add(Date::class.java, Rfc3339DateJsonAdapter())
      .build()
}
