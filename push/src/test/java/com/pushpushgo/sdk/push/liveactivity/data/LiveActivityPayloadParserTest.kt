package com.pushpushgo.sdk.push.liveactivity.data

import com.pushpushgo.sdk.push.liveactivity.LiveActivityTemplate
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

  @Test
  fun `colors may be a hex string, a hex object or a gradient`() {
    val design =
      assertNotNull(
        configurationWith(
          """
          "design":{"android":{
            "progressBarColor":{"lightMode":{"type":"GRADIENT","fromHex":"#111111","toHex":"#222222"}},
            "breakTimeBarColor":{"lightMode":{"type":"BASIC","hex":"#333333"},"darkMode":"#444444"}
          }}
          """,
        ).design,
      )

    assertFalse(design.hasTrackerIcon)
    // A gradient resolves to its first color, and a missing dark color falls back to the light one.
    assertEquals(LiveActivityColorSet(LiveActivityColor("#111111"), LiveActivityColor("#111111")), design.progressBarColor)
    assertEquals(LiveActivityColorSet(LiveActivityColor("#333333"), LiveActivityColor("#444444")), design.breakTimeBarColor)

    val withoutLightColor = configurationWith(""""design":{"android":{"progressBarColor":{"darkMode":"#555555"}}}""")
    assertNull(assertNotNull(withoutLightColor.design).progressBarColor)
  }

  @Test
  fun `statusLabels may be an array ordered like the match phases`() {
    val config = configurationWith(""""statusLabels":["Before","First",""]""")

    assertEquals("Before", config.label(MatchPhase.PRE_MATCH))
    assertEquals("First", config.label(MatchPhase.FIRST_HALF))
    assertEquals(MatchPhase.FIRST_HALF_ADDED_TIME.displayText, config.label(MatchPhase.FIRST_HALF_ADDED_TIME))
  }

  @Test
  fun `actions come from actionSet before actions and invalid ones are dropped`() {
    val config =
      configurationWith(
        """
        "actionSet":[
          {"type":"redirect","name":"Go","url":"https://y"},
          {"type":"BOGUS","name":"Unknown"},
          {"type":"OPEN_APP","name":" "},
          {"type":"OPEN_APP","name":"Open","url":""},
          "junk"
        ],
        "actions":[{"type":"CLOSE","name":"Close"}]
        """,
      )

    assertEquals(
      listOf(
        LiveActivityAction(LiveActivityActionType.REDIRECT, "Go", "https://y"),
        LiveActivityAction(LiveActivityActionType.OPEN_APP, "Open", null),
      ),
      config.actions,
    )
  }

  @Test
  fun `configuration needs a content object`() {
    assertNull(LiveActivityPayloadParser.parseConfiguration("""{"url":"https://match/1"}"""))
    assertNull(LiveActivityPayloadParser.parseConfiguration("""{"content":"Premier League"}"""))
    assertNull(LiveActivityPayloadParser.parseConfiguration("[]"))

    assertEquals(
      FootballMatchConfiguration(
        content = content(),
        design = null,
        statusLabels = emptyMap(),
        actions = emptyList(),
        timeoutMinutes = null,
        url = null,
      ),
      LiveActivityPayloadParser.parseConfiguration("""{"content":{}}"""),
    )
  }

  @Test
  fun `values of an unexpected type read as missing`() {
    val config =
      LiveActivityPayloadParser.parseConfiguration(
        """
        {
          "content":{"title":7,"homeTeamName":"Arsenal","awayTeamName":["Chelsea"]},
          "design":{"android":{"hasTrackerIcon":"yes","progressBarColor":"#123456","breakTimeBarColor":{"lightMode":5}}},
          "statusLabels":"oops",
          "actions":{"type":"OPEN_APP","name":"Open"},
          "timeout":"soon",
          "url":42
        }
        """,
      )

    assertEquals(
      FootballMatchConfiguration(
        content = content(homeTeamName = "Arsenal"),
        design = FootballMatchAndroidDesign(hasTrackerIcon = false, progressBarColor = null, breakTimeBarColor = null),
        statusLabels = emptyMap(),
        actions = emptyList(),
        timeoutMinutes = null,
        url = null,
      ),
      config,
    )
  }

  @Test
  fun `live data numbers may be strings or decimals`() {
    val liveData =
      assertNotNull(
        LiveActivityPayloadParser.parseLiveData(
          """{"homeTeamScore":"3","awayTeamScore":1.0,"status":"first_half","version":"7.0"}""",
        ),
      )

    assertEquals(3, liveData.homeTeamScore)
    assertEquals(1, liveData.awayTeamScore)
    assertEquals(MatchPhase.FIRST_HALF, liveData.status)
    assertEquals(7, liveData.liveDataVersion)
    assertEquals(0, assertNotNull(LiveActivityPayloadParser.parseLiveData("""{"homeTeamScore":"abc"}""")).homeTeamScore)
    assertNull(LiveActivityPayloadParser.parseLiveData("[]"))
  }

  @Test
  fun `timestamps may be ISO-8601, epoch seconds or epoch millis`() {
    listOf(
      "\"2024-03-02T15:00:00Z\"",
      "\"2024-03-02T16:00:00+01:00\"",
      "1709391600",
      "1709391600000",
      "\"1709391600\"",
      "\"1709391600000\"",
    ).forEach { assertEquals(1_709_391_600_000L, statusChangedAt(it), it) }

    listOf("\"\"", "\"soon\"", "true", "{}").forEach { assertNull(statusChangedAt(it), it) }
  }

  @Test
  fun `hot message id falls back to the text and the timestamp is optional`() {
    val fallbackId = "Hi".hashCode().toString()

    assertEquals(HotMessage(fallbackId, "Hi", null), LiveActivityPayloadParser.parseHotMessage("""{"text":"Hi"}"""))
    assertEquals(
      HotMessage(fallbackId, "Hi", 1_709_391_600_000L),
      LiveActivityPayloadParser.parseHotMessage("""{"id":" ","text":"Hi","timestamp":"2024-03-02T15:00:00Z"}"""),
    )
    assertNull(LiveActivityPayloadParser.parseHotMessage("""{"id":"x","text":"  "}"""))
    assertNull(LiveActivityPayloadParser.parseHotMessage("not json"))
  }

  @Test
  fun `parseStartPolicy reads the pre-match countdown of a document`() {
    assertEquals(
      CountdownInfo(message = "Kick-off soon", endAtMs = 1_709_391_600_000L),
      LiveActivityPayloadParser.parseStartPolicy(document()),
    )
    assertEquals(
      CountdownInfo(message = null, endAtMs = 1_709_391_600_000L),
      LiveActivityPayloadParser.parseStartPolicy("""{"startPolicy":{"scheduledAt":1709391600,"countdown":{"message":""}}}"""),
    )
    assertNull(LiveActivityPayloadParser.parseStartPolicy("""{"startPolicy":{"scheduledAt":"2024-03-02T15:00:00Z"}}"""))
    assertNull(LiveActivityPayloadParser.parseStartPolicy("""{"startPolicy":{"countdown":{"message":"Soon"}}}"""))
    assertNull(LiveActivityPayloadParser.parseStartPolicy("""{"id":"match-001"}"""))
  }

  @Test
  fun `buildCatchUpEnvelope turns an ongoing document into a start push`() {
    val envelope = assertNotNull(LiveActivityPayloadParser.buildCatchUpEnvelope(document()))

    assertEquals(setOf("type", "event", "liveNotificationId", "template", "configuration", "liveData"), envelope.keys)
    val push = assertNotNull(LiveActivityPayloadParser.parsePush(envelope))
    assertEquals("match-001", push.id)
    assertEquals(LiveActivityEvent.START, push.event)
    assertEquals(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, push.template)
    assertEquals(
      LiveActivityPayloadParser.parseState(push.template, CONFIG_JSON, LIVE_DATA_JSON),
      LiveActivityPayloadParser.parseState(push.template, push.configurationJson, push.liveDataJson),
    )
  }

  @Test
  fun `buildCatchUpEnvelope skips documents that are not ongoing or not complete`() {
    assertNotNull(LiveActivityPayloadParser.buildCatchUpEnvelope(document(lifecycleStatus = "\"ongoing\"")))

    listOf(
      document(lifecycleStatus = "\"PENDING\""),
      document(lifecycleStatus = "null"),
      document(id = "\" \""),
      document(id = "null"),
      document(configuration = "\"Premier League\""),
      document(liveData = "null"),
      "not json",
    ).forEach { assertNull(LiveActivityPayloadParser.buildCatchUpEnvelope(it), it) }
  }

  @Test
  fun `buildCatchUpEnvelope leaves out a missing template so the push is ignored`() {
    val envelope = assertNotNull(LiveActivityPayloadParser.buildCatchUpEnvelope(document(template = "null")))

    assertFalse("template" in envelope)
    assertNull(LiveActivityPayloadParser.parsePush(envelope))
  }

  @Test
  fun `blocks saved with decimal numbers still parse`() {
    val state =
      assertIs<FootballMatchState>(
        LiveActivityPayloadParser.parseState(
          LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
          configurationJson = """{"content":{"title":"T"},"timeout":{"minutes":120.0}}""",
          liveDataJson = """{"homeTeamScore":2.0,"awayTeamScore":1.0,"status":"SECOND_HALF","version":3.0}""",
        ),
      )

    assertEquals(120, state.configuration.timeoutMinutes)
    assertEquals("2:1", state.liveData.scoreText)
    assertEquals(3, state.liveData.liveDataVersion)
  }

  @Test
  fun `mergeState keeps the current block when the new one is invalid`() {
    val current =
      assertNotNull(LiveActivityPayloadParser.parseState(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, CONFIG_JSON, LIVE_DATA_JSON))

    assertEquals(current, LiveActivityPayloadParser.mergeState(current, configurationJson = "not json", liveDataJson = "[]"))
  }

  /** Parses a configuration made of a valid `content` block and the given extra [fields]. */
  private fun configurationWith(fields: String): FootballMatchConfiguration =
    assertNotNull(
      LiveActivityPayloadParser.parseConfiguration("""{"content":{"title":"T","homeTeamName":"H","awayTeamName":"A"},$fields}"""),
    )

  private fun content(homeTeamName: String = ""): FootballMatchContent =
    FootballMatchContent(title = "", homeTeamName = homeTeamName, homeTeamImage = null, awayTeamName = "", awayTeamImage = null)

  private fun statusChangedAt(json: String): Long? =
    assertNotNull(LiveActivityPayloadParser.parseLiveData("""{"statusChangedAt":$json}""")).statusChangedAt

  /** A live notification document as returned by `GET /core/v1/android/projects/{project}/live-notifications/{id}`. */
  private fun document(
    id: String = "\"match-001\"",
    template: String = "\"FOOTBALL_MATCH_TRACKING\"",
    configuration: String = CONFIG_JSON,
    liveData: String = LIVE_DATA_JSON,
    lifecycleStatus: String = "\"ONGOING\"",
  ): String =
    """
    {
      "id":$id,
      "projectId":"proj-1",
      "template":$template,
      "configuration":$configuration,
      "liveData":$liveData,
      "lifecycle":{"status":$lifecycleStatus},
      "startPolicy":{"scheduledAt":"2024-03-02T15:00:00Z","countdown":{"message":"Kick-off soon","seconds":600}},
      "broadcastChannels":[{"type":"APNS","channelId":"channel-1"}]
    }
    """

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
