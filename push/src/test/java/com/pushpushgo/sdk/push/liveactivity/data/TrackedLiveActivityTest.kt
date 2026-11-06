package com.pushpushgo.sdk.push.liveactivity.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class TrackedLiveActivityTest {
  @Test
  fun `FootballMatchUpdate builds initial state only with both blocks`() {
    assertNull(FootballMatchUpdate(configuration = null, liveData = liveData()).toInitialState())
    assertNull(FootballMatchUpdate(configuration = configuration(), liveData = null).toInitialState())

    val state = assertNotNull(FootballMatchUpdate(configuration(), liveData()).toInitialState())
    assertEquals("Arsenal", state.configuration.content.homeTeamName)
    assertEquals(2, state.liveData.homeTeamScore)
  }

  @Test
  fun `FootballMatchUpdate merge keeps missing blocks and pre-match countdown`() {
    val current =
      FootballMatchState(
        configuration = configuration(),
        liveData = liveData(),
        countdownMessage = "Kick-off soon",
        countdownEndAtMs = 1_000L,
      )

    val merged = assertNotNull(FootballMatchUpdate(configuration = null, liveData = liveData(homeScore = 5)).mergeInto(current))

    assertEquals(5, merged.liveData.homeTeamScore)
    assertEquals("Premier League", merged.configuration.content.title)
    assertEquals("Kick-off soon", merged.countdownMessage)
    assertEquals(1_000L, merged.countdownEndAtMs)
  }

  @Test
  fun `FootballMatchState exposes generic fields from configuration and live data`() {
    val action = LiveActivityAction(LiveActivityActionType.OPEN_APP, "Open", null)
    val state = FootballMatchState(configuration().copy(actions = listOf(action)), liveData(version = 7))

    assertEquals(7, state.liveDataVersion)
    assertEquals("https://match/1", state.url)
    assertEquals(listOf(action), state.actions)
  }

  @Test
  fun `toLiveActivity projects the template-agnostic fields onto the public model`() {
    val tracked =
      TrackedLiveActivity(
        id = "la-1",
        projectId = "proj-1",
        subscriberId = "sub-1",
        template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
        status = LiveActivityStatus.ACTIVE,
        state = FootballMatchState(configuration(), liveData(), "Kick-off soon", 1_000L),
        hotMessage = HotMessage("h1", "GOAL!", null),
      )

    val public = tracked.toLiveActivity()

    assertEquals("la-1", public.id)
    assertEquals(LiveActivityTemplate.FOOTBALL_MATCH_TRACKING, public.template)
  }

  private fun configuration() =
    FootballMatchConfiguration(
      template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
      content = FootballMatchContent("Premier League", "Arsenal", null, "Chelsea", null),
      design = null,
      statusLabels = emptyMap(),
      actions = emptyList(),
      timeoutMinutes = null,
      url = "https://match/1",
    )

  private fun liveData(
    homeScore: Int = 2,
    version: Int = 0,
  ) = FootballMatchLiveData(homeScore, 1, MatchPhase.FIRST_HALF, statusChangedAt = null, liveDataVersion = version)
}
