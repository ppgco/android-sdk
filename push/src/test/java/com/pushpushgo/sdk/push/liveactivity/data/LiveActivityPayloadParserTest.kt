package com.pushpushgo.sdk.push.liveactivity.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class LiveActivityPayloadParserTest {
  @Test
  fun `isLiveActivityPush recognises only live notification pushes`() {
    assertTrue(LiveActivityPayloadParser.isLiveActivityPush(mapOf("type" to "live_notification")))
    assertFalse(LiveActivityPayloadParser.isLiveActivityPush(mapOf("type" to "notification")))
    assertFalse(LiveActivityPayloadParser.isLiveActivityPush(emptyMap()))
  }

  @Test
  fun `parsePush parses full start envelope`() {
    val push = assertNotNull(LiveActivityPayloadParser.parsePush(buildStartEnvelope()))

    assertEquals("match-001", push.id)
    assertEquals("proj-1", push.projectId)
    assertEquals("sub-1", push.subscriberId)
    assertEquals(LiveActivityEvent.START, push.event)
    assertEquals(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, push.template)

    val state = assertIs<FootballMatchState>(LiveActivityPayloadParser.parseState(push.template, push.configurationJson, push.liveDataJson))
    val config = state.configuration
    assertEquals("Premier League", config.content.title)
    assertEquals("Arsenal", config.content.homeTeamName)
    assertEquals("Chelsea", config.content.awayTeamName)
    assertNull(config.content.awayTeamImage) // empty string normalized to null
    assertEquals("https://match/1", config.url)
    assertEquals(120, config.timeoutMinutes)

    val liveData = state.liveData
    assertEquals(2, liveData.homeTeamScore)
    assertEquals(1, liveData.awayTeamScore)
    assertEquals(MatchPhase.SECOND_HALF, liveData.status)
    assertNotNull(liveData.statusChangedAt)

    val hot = assertNotNull(push.hotMessage)
    assertEquals("h1", hot.id)
    assertEquals("GOAL!", hot.text)
    assertEquals(1_709_398_800_000L, hot.expiresAtMs) // epoch seconds -> millis
  }

  @Test
  fun `parsePush keeps a push without configuration`() {
    val data = buildStartEnvelope().toMutableMap().apply { remove("configuration") }
    val push = assertNotNull(LiveActivityPayloadParser.parsePush(data))

    assertNull(push.configurationJson)
    assertEquals(LIVE_DATA_JSON, push.liveDataJson)
  }

  @Test
  fun `parseState needs both valid blocks`() {
    val template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING

    assertNull(LiveActivityPayloadParser.parseState(template, null, LIVE_DATA_JSON))
    assertNull(LiveActivityPayloadParser.parseState(template, CONFIG_JSON, null))
    assertNull(LiveActivityPayloadParser.parseState(template, "not json", LIVE_DATA_JSON))
    assertNotNull(LiveActivityPayloadParser.parseState(template, CONFIG_JSON, LIVE_DATA_JSON))
  }

  @Test
  fun `mergeState keeps missing blocks and pre-match countdown`() {
    val current =
      assertIs<FootballMatchState>(
        LiveActivityPayloadParser.parseState(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, CONFIG_JSON, LIVE_DATA_JSON),
      ).copy(countdownMessage = "Kick-off soon", countdownEndAtMs = 1_000L)

    val merged =
      assertIs<FootballMatchState>(
        LiveActivityPayloadParser.mergeState(
          current,
          configurationJson = null,
          liveDataJson = """{"homeTeamScore":5,"awayTeamScore":1,"status":"SECOND_HALF"}""",
        ),
      )

    assertEquals(5, merged.liveData.homeTeamScore)
    assertEquals("Premier League", merged.configuration.content.title)
    assertEquals("Kick-off soon", merged.countdownMessage)
    assertEquals(1_000L, merged.countdownEndAtMs)
  }

  @Test
  fun `parsePush returns null without liveNotificationId`() {
    val data = buildStartEnvelope().toMutableMap().apply { remove("liveNotificationId") }

    assertNull(LiveActivityPayloadParser.parsePush(data))
  }

  @Test
  fun `parsePush returns null for unknown event`() {
    val data = buildStartEnvelope().toMutableMap().apply { this["event"] = "bogus" }

    assertNull(LiveActivityPayloadParser.parsePush(data))
  }

  @Test
  fun `parsePush ignores a push without template`() {
    val data = buildStartEnvelope().toMutableMap().apply { remove("template") }

    assertNull(LiveActivityPayloadParser.parsePush(data))
  }

  @Test
  fun `parsePush ignores unsupported template instead of falling back to football`() {
    val data = buildStartEnvelope().toMutableMap().apply { this["template"] = "SOME_FUTURE_TEMPLATE" }

    assertNull(LiveActivityPayloadParser.parsePush(data))
  }

  @Test
  fun `parseConfiguration parses statusLabels actions and android colors`() {
    val config = assertNotNull(LiveActivityPayloadParser.parseConfiguration(CONFIG_JSON))

    assertEquals("1st Half", config.label(MatchPhase.FIRST_HALF))
    assertEquals("Match", config.label(MatchPhase.PENALTY_SHOOTOUT)) // OTHER fallback

    assertEquals(3, config.actions.size)
    assertEquals(LiveActivityActionType.OPEN_APP, config.actions[0].type)
    assertEquals(LiveActivityActionType.URL, config.actions[1].type)
    assertEquals("https://x", config.actions[1].url)
    assertEquals(LiveActivityActionType.CLOSE, config.actions[2].type)

    val design = assertNotNull(config.design)
    assertTrue(design.hasTrackerIcon)
    val progressBarColor = assertNotNull(design.progressBarColor)
    // lightMode is a plain string, darkMode is an object {hex} — both supported
    assertEquals("#4CAF50", progressBarColor.lightMode.hex)
    assertEquals("#2E7D32", progressBarColor.darkMode.hex)
  }

  @Test
  fun `parseLiveData maps unknown status to OTHER`() {
    val liveData =
      assertNotNull(
        LiveActivityPayloadParser.parseLiveData(
          """{"homeTeamScore":0,"awayTeamScore":0,"status":"WHO_KNOWS"}""",
        ),
      )

    assertEquals(MatchPhase.OTHER, liveData.status)
    assertEquals("0:0", liveData.scoreText)
    assertNull(liveData.statusChangedAt)
  }

  @Test
  fun `parseHotMessage returns null without text`() {
    assertNull(LiveActivityPayloadParser.parseHotMessage("""{"id":"x"}"""))
  }

  private fun buildStartEnvelope(): Map<String, String> =
    mapOf(
      "type" to "live_notification",
      "liveNotificationId" to "match-001",
      "event" to "start",
      "template" to "FOOTBALL_MATCH_TRACKING",
      "project" to "proj-1",
      "subscriber" to "sub-1",
      "configuration" to CONFIG_JSON,
      "liveData" to LIVE_DATA_JSON,
      "hotMessage" to HOT_MESSAGE_JSON,
    )

  companion object {
    private val CONFIG_JSON =
      """
      {
        "type":"FOOTBALL_MATCH_TRACKING",
        "content":{
          "title":"Premier League",
          "homeTeamName":"Arsenal",
          "homeTeamImage":"https://a.png",
          "awayTeamName":"Chelsea",
          "awayTeamImage":""
        },
        "design":{
          "android":{
            "hasTrackerIcon":true,
            "progressBarColor":{"lightMode":"#4CAF50","darkMode":{"hex":"#2E7D32"}},
            "breakTimeBarColor":{"lightMode":"#FFC107","darkMode":"#FFA000"}
          }
        },
        "statusLabels":{"FIRST_HALF":"1st Half","OTHER":"Match"},
        "actions":[
          {"type":"OPEN_APP","name":"Open"},
          {"type":"URL","name":"Stats","url":"https://x"},
          {"type":"CLOSE","name":"Dismiss"}
        ],
        "timeout":{"minutes":120},
        "url":"https://match/1"
      }
      """.trimIndent()

    private val LIVE_DATA_JSON =
      """
      {"type":"FOOTBALL_MATCH_TRACKING","homeTeamScore":2,"awayTeamScore":1,"status":"SECOND_HALF","statusChangedAt":"2024-03-02T15:00:00Z"}
      """.trimIndent()

    private val HOT_MESSAGE_JSON = """{"id":"h1","text":"GOAL!","timestamp":1709398800}"""
  }
}
