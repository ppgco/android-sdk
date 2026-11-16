package com.pushpushgo.sdk.push.liveactivity

import com.pushpushgo.sdk.push.liveactivity.data.MatchPhase

/**
 * Live Activity payloads of one football match in both formats the backend sends:
 * the blocks of a push and the live notification document.
 */
internal object LiveActivityPayloads {
  const val KICK_OFF = "2024-03-02T15:00:00Z"
  const val KICK_OFF_MS = 1_709_391_600_000L

  /** Label of every match phase, keyed by the phase. */
  val STATUS_LABELS: Map<String, String> = MatchPhase.entries.associate { it.value to "${it.value} label" }

  /** `statusLabels` of a push: an array in the order of the phases. */
  private val STATUS_LABELS_ARRAY = STATUS_LABELS.values.joinToString(",", "[", "]") { "\"$it\"" }

  /** `statusLabels` of a document: an object keyed by the phase. */
  private val STATUS_LABELS_OBJECT = STATUS_LABELS.entries.joinToString(",", "{", "}") { (phase, label) -> "\"$phase\":\"$label\"" }

  /** iOS design of an action button in a push; Android doesn't read it. */
  private const val PUSH_ACTION_DESIGN =
    """{"ios":{"alignment":"STRETCH","borderRadius":100,"appearance":{""" +
      """"lightMode":{"textColor":{"hex":"#ffffff"},"backgroundColor":{"hex":"#10897D"},"border":null},""" +
      """"darkMode":{"textColor":{"hex":"#10897D"},"backgroundColor":null,"border":{"color":{"hex":"#10897D"},"width":1}}}}}"""

  /** iOS design of an action button in a document; Android doesn't read it. */
  private const val DOCUMENT_ACTION_DESIGN =
    """{"ios":{"alignment":"LEFT","borderRadius":8,"appearance":{""" +
      """"lightMode":{"textColor":{"type":"BASIC","hex":"#FFFFFF"},"backgroundColor":null,"border":null},""" +
      """"darkMode":{"textColor":{"type":"BASIC","hex":"#FFFFFF"},"backgroundColor":{"type":"BASIC","hex":"#000000"},""" +
      """"border":{"color":{"type":"BASIC","hex":"#333333"},"width":1}}}}}"""

  /** iOS design of a football match in a document; Android doesn't read it. */
  private const val DOCUMENT_IOS_DESIGN =
    """{"statusBackground":{"lightMode":{"type":"GRADIENT","fromHex":"#111111","toHex":"#222222","direction":"TOP_TO_BOTTOM"},""" +
      """"darkMode":{"type":"BASIC","hex":"#000000"}}}"""

  /** `configuration` block of a `start` push, with the nullable fields set to null. */
  val CONFIGURATION: String =
    """
    {
      "template":"FOOTBALL_MATCH_TRACKING",
      "content":{
        "title":"Premier League",
        "homeTeamName":"Arsenal",
        "homeTeamImage":"https://a.png",
        "homeTeamImageOwner":"owner-1",
        "awayTeamName":"Chelsea",
        "awayTeamImage":null,
        "awayTeamImageOwner":"owner-2"
      },
      "design":{
        "android":{
          "hasTrackerIcon":true,
          "progressBarColor":{"lightMode":{"hex":"#4CAF50"},"darkMode":{"hex":"#2E7D32"}},
          "breakTimeBarColor":null
        }
      },
      "statusLabels":$STATUS_LABELS_ARRAY,
      "timeout":{"minutes":120},
      "actionSet":[
        {"type":"OPEN_APP","name":"Open","design":$PUSH_ACTION_DESIGN},
        {"type":"URL","name":"Stats","url":"https://x","design":$PUSH_ACTION_DESIGN},
        {"type":"CLOSE","name":"Dismiss","design":$PUSH_ACTION_DESIGN}
      ]
    }
    """.trimIndent()

  /** `hotMessage` block of an `update` push. */
  const val HOT_MESSAGE = """{"id":"h1","text":"GOAL!","expiresAt":"2024-03-02T15:00:00Z"}"""

  /** `liveData` block of a push. */
  fun liveData(
    homeScore: Int = 2,
    awayScore: Int = 1,
    status: String = MatchPhase.SECOND_HALF.value,
    statusChangedAt: String = "\"$KICK_OFF\"",
    version: Int = 7,
  ): String =
    """{"template":"FOOTBALL_MATCH_TRACKING","homeTeamScore":$homeScore,"awayTeamScore":$awayScore,"status":"$status",""" +
      """"version":$version,"statusChangedAt":$statusChangedAt}"""

  /** Data of a push as FCM delivers it. Only `start` carries a [configuration]. */
  fun push(
    event: String,
    configuration: String? = null,
    liveData: String? = liveData(),
    hotMessage: String? = null,
  ): Map<String, String> =
    buildMap {
      put("type", "live_notification")
      put("event", event)
      put("liveNotificationId", "match-001")
      put("version", "8")
      put("liveDataVersion", "7")
      put("statusChangedAt", KICK_OFF)
      configuration?.let { put("configuration", it) }
      liveData?.let { put("liveData", it) }
      hotMessage?.let { put("hotMessage", it) }
    }

  /**
   * `configuration` of a document. The backend is going to stop sending the iOS
   * fields to Android, so [ios] leaves them out.
   */
  fun documentConfiguration(ios: Boolean = true): String {
    val actionDesign = if (ios) ",\"design\":$DOCUMENT_ACTION_DESIGN" else ""
    return """
      {
        "type":"FOOTBALL_MATCH_TRACKING",
        "url":"https://match/1",
        "content":{
          "title":"Premier League",
          "homeTeamName":"Arsenal",
          "homeTeamImage":"https://a.png",
          "awayTeamName":"Chelsea",
          "awayTeamImage":null
        },
        "design":{
          "android":{
            "hasTrackerIcon":true,
            "progressBarColor":{"lightMode":{"type":"BASIC","hex":"#4CAF50"},"darkMode":{"type":"BASIC","hex":"#2E7D32"}},
            "breakTimeBarColor":null
          }${if (ios) ",\"ios\":$DOCUMENT_IOS_DESIGN" else ""}
        },
        "statusLabels":$STATUS_LABELS_OBJECT,
        "actions":[
          {"type":"OPEN_APP","name":"Open"$actionDesign},
          {"type":"URL","name":"Stats"$actionDesign,"url":"https://x"},
          {"type":"CLOSE","name":"Dismiss"$actionDesign}
        ],
        "timeout":{"minutes":120}
      }
      """.trimIndent()
  }

  /** `liveData` of a document. It has no version. */
  const val DOCUMENT_LIVE_DATA =
    """{"type":"FOOTBALL_MATCH_TRACKING","homeTeamScore":2,"awayTeamScore":1,"status":"SECOND_HALF",""" +
      """"statusChangedAt":"2024-03-02T15:00:00Z"}"""

  /** A live notification document as `GET /core/v1/android/projects/{project}/live-notifications/{id}` returns it. */
  fun document(
    configuration: String = documentConfiguration(),
    liveData: String = DOCUMENT_LIVE_DATA,
    lifecycleStatus: String = "ONGOING",
    countdown: String = """{"message":"Kick-off soon","seconds":600}""",
  ): String =
    """
    {
      "id":"match-001",
      "projectId":"proj-1",
      "template":"FOOTBALL_MATCH_TRACKING",
      "configuration":$configuration,
      "liveData":$liveData,
      "lifecycle":{"status":"$lifecycleStatus"},
      "startPolicy":{"scheduledAt":"$KICK_OFF","countdown":$countdown},
      "broadcastChannels":[{"type":"APNS","channelId":"channel-1"}]
    }
    """.trimIndent()
}
