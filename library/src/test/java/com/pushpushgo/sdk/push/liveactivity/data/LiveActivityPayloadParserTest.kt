package com.pushpushgo.sdk.push.liveactivity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class LiveActivityPayloadParserTest {

  @Test
  fun `isLiveActivityPush returns true for live notification push`() {
    assertTrue(LiveActivityPayloadParser.isLiveActivityPush(mapOf("type" to "live_notification")))
  }

  @Test
  fun `isLiveActivityPush returns false for regular push`() {
    assertFalse(LiveActivityPayloadParser.isLiveActivityPush(mapOf("type" to "notification")))
    assertFalse(LiveActivityPayloadParser.isLiveActivityPush(emptyMap()))
  }

  @Test
  fun `parsePush parses full start envelope`() {
    val push = LiveActivityPayloadParser.parsePush(buildStartEnvelope())

    assertNotNull(push)
    assertEquals("match-001", push!!.id)
    assertEquals("proj-1", push.projectId)
    assertEquals("sub-1", push.subscriberId)
    assertEquals(LiveActivityEvent.START, push.event)
    assertEquals(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, push.template)

    val config = push.configuration!!
    assertEquals("Premier League", config.content.title)
    assertEquals("Arsenal", config.content.homeTeamName)
    assertEquals("Chelsea", config.content.awayTeamName)
    assertNull(config.content.awayTeamImage) // empty string normalized to null
    assertEquals("https://match/1", config.url)
    assertEquals(120, config.timeoutMinutes)

    val liveData = push.liveData!!
    assertEquals(2, liveData.homeTeamScore)
    assertEquals(1, liveData.awayTeamScore)
    assertEquals(MatchPhase.SECOND_HALF, liveData.status)
    assertNotNull(liveData.statusChangedAt)

    val hot = push.hotMessage!!
    assertEquals("h1", hot.id)
    assertEquals("GOAL!", hot.text)
    assertEquals(1_709_398_800_000L, hot.expiresAtMs) // epoch seconds -> millis
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
  fun `parseConfiguration parses statusLabels actions and android colors`() {
    val config = LiveActivityPayloadParser.parseConfiguration(CONFIG_JSON)!!

    assertEquals("1st Half", config.label(MatchPhase.FIRST_HALF))
    assertEquals("Match", config.label(MatchPhase.PENALTY_SHOOTOUT)) // OTHER fallback

    assertEquals(3, config.actions.size)
    assertEquals(LiveActivityActionType.OPEN_APP, config.actions[0].type)
    assertEquals(LiveActivityActionType.URL, config.actions[1].type)
    assertEquals("https://x", config.actions[1].url)
    assertEquals(LiveActivityActionType.CLOSE, config.actions[2].type)

    val design = config.design!!
    assertTrue(design.hasTrackerIcon)
    // lightMode is a plain string, darkMode is an object {hex} — both supported
    assertEquals("#4CAF50", design.progressBarColor!!.lightMode.hex)
    assertEquals("#2E7D32", design.progressBarColor!!.darkMode.hex)
  }

  @Test
  fun `parseLiveData maps unknown status to OTHER`() {
    val liveData = LiveActivityPayloadParser.parseLiveData(
      """{"homeTeamScore":0,"awayTeamScore":0,"status":"WHO_KNOWS"}""",
    )!!
    assertEquals(MatchPhase.OTHER, liveData.status)
    assertEquals("0:0", liveData.scoreText)
    assertNull(liveData.statusChangedAt)
  }

  @Test
  fun `parseHotMessage returns null without text`() {
    assertNull(LiveActivityPayloadParser.parseHotMessage("""{"id":"x"}"""))
  }

  private fun buildStartEnvelope(): Map<String, String> = mapOf(
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
    private val CONFIG_JSON = """
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

    private val LIVE_DATA_JSON = """
      {"type":"FOOTBALL_MATCH_TRACKING","homeTeamScore":2,"awayTeamScore":1,"status":"SECOND_HALF","statusChangedAt":"2024-03-02T15:00:00Z"}
    """.trimIndent()

    private val HOT_MESSAGE_JSON = """{"id":"h1","text":"GOAL!","timestamp":1709398800}"""
  }
}
