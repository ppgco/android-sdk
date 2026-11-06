package com.pushpushgo.sdk.push.liveactivity.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class LiveActivityTemplateTest {
  @Test
  fun `fromValue resolves supported templates case-insensitively`() {
    assertEquals(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, LiveActivityTemplate.fromValue("FOOTBALL_MATCH_TRACKING"))
    assertEquals(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, LiveActivityTemplate.fromValue("football_match_tracking"))
    assertNull(LiveActivityTemplate.fromValue("SOME_FUTURE_TEMPLATE"))
    assertNull(LiveActivityTemplate.fromValue(null))
  }

  @Test
  fun `templates compare by wire value`() {
    val template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING

    assertEquals(template, LiveActivityTemplate.fromValue("FOOTBALL_MATCH_TRACKING"))
    assertEquals(template.hashCode(), LiveActivityTemplate.fromValue("FOOTBALL_MATCH_TRACKING").hashCode())
    assertEquals("FOOTBALL_MATCH_TRACKING", template.toString())
  }

  @Test
  fun `every supported template has a state parser`() {
    // Guards against adding a template constant without wiring its parser
    // (the template is not an enum, so the compiler can't check it).
    val samples = mapOf(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING to ("""{"content":{}}""" to "{}"))

    assertEquals(LiveActivityTemplate.supported.toSet(), samples.keys)
    for ((template, blocks) in samples) {
      assertNotNull(LiveActivityPayloadParser.parseState(template, blocks.first, blocks.second), "no parser for $template")
    }
  }
}
