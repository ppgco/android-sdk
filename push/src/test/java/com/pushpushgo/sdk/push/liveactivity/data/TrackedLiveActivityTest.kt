package com.pushpushgo.sdk.push.liveactivity.data

import com.pushpushgo.sdk.push.liveactivity.LiveActivityTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TrackedLiveActivityTest {
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
      content = FootballMatchContent("Premier League", "Arsenal", null, "Chelsea", null),
      design = null,
      statusLabels = emptyMap(),
      actions = emptyList(),
      timeoutMinutes = null,
      url = "https://match/1",
    )

  private fun liveData(version: Int = 0) =
    FootballMatchLiveData(
      homeTeamScore = 2,
      awayTeamScore = 1,
      status = MatchPhase.FIRST_HALF,
      statusChangedAt = null,
      liveDataVersion = version,
    )
}
