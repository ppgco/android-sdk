package com.pushpushgo.sdk.push.liveactivity.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads.CONFIGURATION
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads.HOT_MESSAGE
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads.KICK_OFF_MS
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads.document
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads.documentConfiguration
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads.liveData
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPayloads.push
import com.pushpushgo.sdk.push.liveactivity.LiveActivityTemplate
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Runs on Robolectric because a rejected payload is logged.
@RunWith(AndroidJUnit4::class)
internal class LiveActivityPayloadParserTest {
  private val template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING

  /** What [CONFIGURATION] parses to. A push carries no deep link. */
  private val configuration =
    FootballMatchConfiguration(
      content =
        FootballMatchContent(
          title = "Premier League",
          homeTeamName = "Arsenal",
          homeTeamImage = "https://a.png",
          awayTeamName = "Chelsea",
          awayTeamImage = null,
        ),
      design =
        FootballMatchAndroidDesign(
          hasTrackerIcon = true,
          progressBarColor = LiveActivityColorSet(LiveActivityColor("#4CAF50"), LiveActivityColor("#2E7D32")),
          breakTimeBarColor = null,
        ),
      statusLabels = LiveActivityPayloads.STATUS_LABELS,
      actions =
        listOf(
          LiveActivityAction(LiveActivityActionType.OPEN_APP, "Open", null),
          LiveActivityAction(LiveActivityActionType.URL, "Stats", "https://x"),
          LiveActivityAction(LiveActivityActionType.CLOSE, "Dismiss", null),
        ),
      timeoutMinutes = 120,
      url = null,
    )

  /** What [liveData] parses to. */
  private val liveData =
    FootballMatchLiveData(
      homeTeamScore = 2,
      awayTeamScore = 1,
      status = MatchPhase.SECOND_HALF,
      statusChangedAt = KICK_OFF_MS,
      liveDataVersion = 7,
    )

  @Test
  fun `isLiveActivityPush recognises only live notification pushes`() {
    assertTrue(LiveActivityPayloadParser.isLiveActivityPush(mapOf("type" to "live_notification")))
    assertFalse(LiveActivityPayloadParser.isLiveActivityPush(mapOf("type" to "notification")))
    assertFalse(LiveActivityPayloadParser.isLiveActivityPush(emptyMap()))
  }

  @Test
  fun `parsePush reads a start push and takes the template from its blocks`() {
    val push = assertNotNull(LiveActivityPayloadParser.parsePush(push("start", configuration = CONFIGURATION)))

    assertEquals("match-001", push.id)
    assertEquals(LiveActivityEvent.START, push.event)
    assertEquals(template, push.template)
    assertEquals("", push.projectId)
    assertEquals("", push.subscriberId)
    assertNull(push.hotMessage)
    assertEquals(
      FootballMatchState(configuration, liveData),
      LiveActivityPayloadParser.parseState(template, push.configurationJson, push.liveDataJson),
    )
  }

  @Test
  fun `parsePush reads an update push with a hot message`() {
    val push = assertNotNull(LiveActivityPayloadParser.parsePush(push("update", hotMessage = HOT_MESSAGE)))

    assertEquals(LiveActivityEvent.UPDATE, push.event)
    assertEquals(template, push.template)
    assertNull(push.configurationJson)
    assertEquals(liveData(), push.liveDataJson)
    assertEquals(HotMessage(id = "h1", text = "GOAL!", expiresAtMs = KICK_OFF_MS), push.hotMessage)
  }

  @Test
  fun `parsePush reads an end push, which has no blocks and so no template`() {
    val push = assertNotNull(LiveActivityPayloadParser.parsePush(push("end", liveData = null)))

    assertEquals(LiveActivityEvent.END, push.event)
    assertNull(push.template)
    assertNull(push.liveDataJson)
  }

  @Test
  fun `parsePush returns null without liveNotificationId`() {
    val data = push("start", configuration = CONFIGURATION) - "liveNotificationId"

    assertNull(LiveActivityPayloadParser.parsePush(data))
  }

  @Test
  fun `parsePush returns null for unknown event`() {
    assertNull(LiveActivityPayloadParser.parsePush(push("bogus")))
  }

  @Test
  fun `parsePush ignores blocks of a template this SDK version does not support`() {
    val future = "\"template\":\"SOME_FUTURE_TEMPLATE\""
    val data =
      push(
        "start",
        configuration = CONFIGURATION.with(TEMPLATE, future),
        liveData = liveData().with(TEMPLATE, future),
      )

    assertEquals("SOME_FUTURE_TEMPLATE", LiveActivityPayloadParser.templateName(data))
    assertNull(LiveActivityPayloadParser.parsePush(data))
  }

  @Test
  fun `parsePush ignores blocks that do not name a template`() {
    val data = push("update", liveData = liveData().with("$TEMPLATE,", ""))

    assertNull(LiveActivityPayloadParser.templateName(data))
    assertNull(LiveActivityPayloadParser.parsePush(data))
  }

  @Test
  fun `parseState needs both valid blocks`() {
    assertNull(LiveActivityPayloadParser.parseState(template, null, liveData()))
    assertNull(LiveActivityPayloadParser.parseState(template, CONFIGURATION, null))
    assertNull(LiveActivityPayloadParser.parseState(template, "not json", liveData()))
    assertEquals(FootballMatchState(configuration, liveData), LiveActivityPayloadParser.parseState(template, CONFIGURATION, liveData()))
  }

  @Test
  fun `mergeState keeps missing blocks and pre-match countdown`() {
    val current = FootballMatchState(configuration, liveData, countdownMessage = "Kick-off soon", countdownEndAtMs = 1_000L)

    val merged = LiveActivityPayloadParser.mergeState(current, configurationJson = null, liveDataJson = liveData(homeScore = 5))

    assertEquals(current.copy(liveData = liveData.copy(homeTeamScore = 5)), merged)
  }

  @Test
  fun `mergeState keeps the current block when the new one breaks the contract`() {
    val current = FootballMatchState(configuration, liveData)

    val merged =
      LiveActivityPayloadParser.mergeState(
        current,
        configurationJson = CONFIGURATION.with("\"timeout\":{\"minutes\":120},", ""),
        liveDataJson = liveData(homeScore = 5, statusChangedAt = "$KICK_OFF_MS"),
      )

    assertEquals(current, merged)
  }

  @Test
  fun `parseConfiguration reads the block of a push`() {
    assertEquals(configuration, LiveActivityPayloadParser.parseConfiguration(CONFIGURATION))
  }

  @Test
  fun `parseConfiguration reads the nullable fields when they are set`() {
    val json =
      CONFIGURATION
        .with("\"awayTeamImage\":null", "\"awayTeamImage\":\"https://c.png\"")
        .with(
          "\"breakTimeBarColor\":null",
          "\"breakTimeBarColor\":{\"lightMode\":{\"hex\":\"#FFC107\"},\"darkMode\":{\"hex\":\"#FFA000\"}}",
        ).with("\"timeout\":{\"minutes\":120},", "\"timeout\":{\"minutes\":120},\"url\":\"https://match/1\",")

    val parsed = assertNotNull(LiveActivityPayloadParser.parseConfiguration(json))

    assertEquals("https://c.png", parsed.content.awayTeamImage)
    assertEquals(LiveActivityColorSet(LiveActivityColor("#FFC107"), LiveActivityColor("#FFA000")), parsed.design?.breakTimeBarColor)
    assertEquals("https://match/1", parsed.url)
  }

  @Test
  fun `actions of an unknown type are skipped`() {
    val json = CONFIGURATION.with("{\"type\":\"OPEN_APP\",\"name\":\"Open\",", "{\"type\":\"SHARE\",\"name\":\"Share\",")

    assertEquals(configuration.actions.drop(1), assertNotNull(LiveActivityPayloadParser.parseConfiguration(json)).actions)
  }

  @Test
  fun `a configuration that breaks the contract is rejected`() {
    listOf(
      CONFIGURATION.with("\"title\":\"Premier League\",", ""),
      CONFIGURATION.with("\"timeout\":{\"minutes\":120}", "\"timeout\":null"),
      CONFIGURATION.with("$TEMPLATE,", ""),
      CONFIGURATION.with(TEMPLATE, "\"template\":\"SOME_FUTURE_TEMPLATE\""),
      CONFIGURATION.with(TEMPLATE, "\"type\":\"FOOTBALL_MATCH_TRACKING\""),
      CONFIGURATION.with("\"statusLabels\":[", "\"statusLabels\":{},\"labels\":["),
      CONFIGURATION.with("\"actionSet\":[", "\"actions\":["),
      CONFIGURATION.with("\"url\":\"https://x\",", ""),
      CONFIGURATION.with("{\"hex\":\"#4CAF50\"}", "\"#4CAF50\""),
      "[]",
      "not json",
    ).forEach { assertNull(LiveActivityPayloadParser.parseConfiguration(it), it) }
  }

  @Test
  fun `parseLiveData reads the block of a push`() {
    assertEquals(liveData, LiveActivityPayloadParser.parseLiveData(liveData()))
  }

  @Test
  fun `parseLiveData maps an unknown status to OTHER`() {
    val parsed = assertNotNull(LiveActivityPayloadParser.parseLiveData(liveData(status = "VAR_REVIEW")))

    assertEquals(MatchPhase.OTHER, parsed.status)
  }

  @Test
  fun `timestamps are ISO-8601`() {
    listOf("\"2024-03-02T15:00:00Z\"", "\"2024-03-02T15:00:00.000Z\"", "\"2024-03-02T16:00:00+01:00\"").forEach {
      assertEquals(KICK_OFF_MS, LiveActivityPayloadParser.parseLiveData(liveData(statusChangedAt = it))?.statusChangedAt, it)
    }

    listOf("1709391600", "1709391600000", "\"1709391600\"", "\"\"", "null").forEach {
      assertNull(LiveActivityPayloadParser.parseLiveData(liveData(statusChangedAt = it)), it)
    }
  }

  @Test
  fun `live data that breaks the contract is rejected`() {
    listOf(
      liveData().with("\"homeTeamScore\":2,", ""),
      liveData().with("\"status\":\"SECOND_HALF\",", ""),
      liveData().with("\"version\":7,", ""),
      liveData().with(",\"statusChangedAt\":\"2024-03-02T15:00:00Z\"", ""),
      liveData().with("$TEMPLATE,", ""),
      liveData().with(TEMPLATE, "\"template\":\"SOME_FUTURE_TEMPLATE\""),
      "[]",
    ).forEach { assertNull(LiveActivityPayloadParser.parseLiveData(it), it) }
  }

  @Test
  fun `parseHotMessage needs an id, a text and an ISO expiry`() {
    assertEquals(HotMessage(id = "h1", text = "GOAL!", expiresAtMs = KICK_OFF_MS), LiveActivityPayloadParser.parseHotMessage(HOT_MESSAGE))

    listOf(
      HOT_MESSAGE.with("\"id\":\"h1\",", ""),
      HOT_MESSAGE.with("\"text\":\"GOAL!\",", ""),
      HOT_MESSAGE.with("\"text\":\"GOAL!\"", "\"text\":\"  \""),
      HOT_MESSAGE.with(",\"expiresAt\":\"2024-03-02T15:00:00Z\"", ""),
      HOT_MESSAGE.with("\"expiresAt\"", "\"timestamp\""),
      HOT_MESSAGE.with("\"2024-03-02T15:00:00Z\"", "1709391600"),
      "not json",
    ).forEach { assertNull(LiveActivityPayloadParser.parseHotMessage(it), it) }
  }

  @Test
  fun `parseStartPolicy reads the pre-match countdown of a document`() {
    assertEquals(CountdownInfo(message = "Kick-off soon", endAtMs = KICK_OFF_MS), LiveActivityPayloadParser.parseStartPolicy(document()))
    assertEquals(
      CountdownInfo(message = null, endAtMs = KICK_OFF_MS),
      LiveActivityPayloadParser.parseStartPolicy(document(countdown = """{"message":"","seconds":600}""")),
    )
    assertNull(LiveActivityPayloadParser.parseStartPolicy(document(countdown = "null")))
    assertNull(LiveActivityPayloadParser.parseStartPolicy("""{"id":"match-001"}"""))
  }

  @Test
  fun `buildCatchUpEnvelope turns an ongoing document into a start push`() {
    val envelope = assertNotNull(LiveActivityPayloadParser.buildCatchUpEnvelope(document()))

    assertEquals(setOf("type", "event", "liveNotificationId", "configuration", "liveData"), envelope.keys)
    val push = assertNotNull(LiveActivityPayloadParser.parsePush(envelope))
    assertEquals("match-001", push.id)
    assertEquals(LiveActivityEvent.START, push.event)
    assertEquals(template, push.template)
    assertNull(push.hotMessage)
    // The document adds the deep link and has no live data version.
    assertEquals(
      FootballMatchState(configuration.copy(url = "https://match/1"), liveData.copy(liveDataVersion = 0)),
      LiveActivityPayloadParser.parseState(template, push.configurationJson, push.liveDataJson),
    )
  }

  @Test
  fun `buildCatchUpEnvelope reads a document with or without the iOS fields`() {
    assertEquals(
      LiveActivityPayloadParser.buildCatchUpEnvelope(document()),
      assertNotNull(LiveActivityPayloadParser.buildCatchUpEnvelope(document(configuration = documentConfiguration(ios = false)))),
    )
  }

  @Test
  fun `buildCatchUpEnvelope skips documents that are not ongoing or break the contract`() {
    val type = "\"type\":\"FOOTBALL_MATCH_TRACKING\""

    listOf(
      document(lifecycleStatus = "PENDING"),
      document(lifecycleStatus = "STOPPED"),
      document().with("\"id\":\"match-001\",", ""),
      document().with("\"lifecycle\":{\"status\":\"ONGOING\"},", ""),
      document(configuration = documentConfiguration().with(type, "\"type\":\"SOME_FUTURE_TEMPLATE\"")),
      document(configuration = documentConfiguration().with("\"url\":\"https://match/1\",", "")),
      document(configuration = CONFIGURATION),
      document(liveData = "null"),
      "{}",
      "not json",
    ).forEach { assertNull(LiveActivityPayloadParser.buildCatchUpEnvelope(it), it) }
  }

  /** Replaces [old], which the payload has to contain, with [new]. */
  private fun String.with(
    old: String,
    new: String,
  ): String {
    check(contains(old)) { "the payload has no $old" }
    return replace(old, new)
  }

  private companion object {
    const val TEMPLATE = "\"template\":\"FOOTBALL_MATCH_TRACKING\""
  }
}
