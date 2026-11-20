package com.pushpushgo.sdk.push.liveactivity.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MatchPhaseTest {
  @Test
  fun `isPlaying covers active phases including added and extra time`() {
    assertTrue(MatchPhase.FIRST_HALF.isPlaying)
    assertTrue(MatchPhase.SECOND_HALF_ADDED_TIME.isPlaying)
    assertTrue(MatchPhase.EXTRA_TIME_FIRST_HALF.isPlaying)
    assertTrue(MatchPhase.PENALTY_SHOOTOUT.isPlaying)
    assertFalse(MatchPhase.HALF_TIME_BREAK.isPlaying)
    assertFalse(MatchPhase.PRE_MATCH.isPlaying)
    assertFalse(MatchPhase.FULL_TIME.isPlaying)
  }

  @Test
  fun `isBreak and isFinished classify correctly`() {
    assertTrue(MatchPhase.HALF_TIME_BREAK.isBreak)
    assertTrue(MatchPhase.EXTRA_TIME_BREAK.isBreak)
    assertFalse(MatchPhase.SECOND_HALF.isBreak)

    assertTrue(MatchPhase.FULL_TIME.isFinished)
    assertTrue(MatchPhase.MATCH_ENDED.isFinished)
    assertFalse(MatchPhase.SECOND_HALF.isFinished)
  }

  @Test
  fun `baseMinute anchors progress per phase`() {
    assertEquals(0, MatchPhase.FIRST_HALF.baseMinute)
    assertEquals(45, MatchPhase.SECOND_HALF.baseMinute)
    assertEquals(90, MatchPhase.FULL_TIME.baseMinute)
    assertEquals(120, MatchPhase.PENALTY_SHOOTOUT.baseMinute)
  }
}
