package com.pushpushgo.sdk.push.liveactivity

import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchConfiguration
import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchContent
import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchLiveData
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityEvent
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPush
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityStatus
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityTemplate
import com.pushpushgo.sdk.push.liveactivity.data.MatchPhase
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class LiveActivityManagerTest {

  @MockK(relaxed = true)
  lateinit var persistence: LiveActivityPersistence

  private lateinit var manager: LiveActivityManager

  @Before
  fun setup() {
    MockKAnnotations.init(this)
    every { persistence.getNotificationId(any()) } returns -1
    every { persistence.getCreatedAt(any()) } returns 0L
    every { persistence.rebuild(any()) } returns null
    manager = LiveActivityManager(persistence)
  }

  @Test
  fun `startActivity stores and returns activity`() {
    val activity = manager.startActivity(startPush())

    assertNotNull(activity)
    assertEquals("test-la-1", activity!!.id)
    assertEquals(LiveActivityStatus.ACTIVE, activity.status)
    assertEquals(2, activity.liveData.homeTeamScore)
    assertEquals("Arsenal", activity.configuration.content.homeTeamName)
  }

  @Test
  fun `startActivity returns null without configuration`() {
    val push = startPush().copy(configuration = null, configurationJson = null)
    assertNull(manager.startActivity(push))
  }

  @Test
  fun `updateActivity merges new live data onto existing state`() {
    manager.startActivity(startPush())

    val updated = manager.updateActivity(
      updatePush(liveData = liveData(homeScore = 3, status = MatchPhase.SECOND_HALF)),
    )

    assertNotNull(updated)
    assertEquals(3, updated!!.liveData.homeTeamScore)
    assertEquals(MatchPhase.SECOND_HALF, updated.liveData.status)
    // configuration preserved from start when update omits it
    assertEquals("Premier League", updated.configuration.content.title)
  }

  @Test
  fun `updateActivity returns null for unknown activity`() {
    assertNull(manager.updateActivity(updatePush()))
  }

  @Test
  fun `updateActivity returns null for ended activity`() {
    manager.startActivity(startPush())
    manager.endActivity(endPush())
    assertNull(manager.updateActivity(updatePush()))
  }

  @Test
  fun `endActivity marks status ended`() {
    manager.startActivity(startPush())
    val ended = manager.endActivity(endPush())

    assertNotNull(ended)
    assertEquals(LiveActivityStatus.ENDED, ended!!.status)
  }

  // Helpers

  private fun configuration() = FootballMatchConfiguration(
    template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
    content = FootballMatchContent("Premier League", "Arsenal", null, "Chelsea", null),
    design = null,
    statusLabels = mapOf("FIRST_HALF" to "1st Half"),
    actions = emptyList(),
    timeoutMinutes = 120,
    url = "https://match/1",
  )

  private fun liveData(
    homeScore: Int = 2,
    awayScore: Int = 1,
    status: MatchPhase = MatchPhase.FIRST_HALF,
  ) = FootballMatchLiveData(homeScore, awayScore, status, statusChangedAt = null)

  private fun startPush() = LiveActivityPush(
    id = "test-la-1",
    projectId = "proj-1",
    subscriberId = "sub-1",
    template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
    event = LiveActivityEvent.START,
    configuration = configuration(),
    configurationJson = "{}",
    liveData = liveData(),
    liveDataJson = "{}",
    hotMessage = null,
  )

  private fun updatePush(liveData: FootballMatchLiveData = liveData()) = LiveActivityPush(
    id = "test-la-1",
    projectId = "proj-1",
    subscriberId = "sub-1",
    template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
    event = LiveActivityEvent.UPDATE,
    configuration = null,
    configurationJson = null,
    liveData = liveData,
    liveDataJson = "{}",
    hotMessage = null,
  )

  private fun endPush() = updatePush().copy(event = LiveActivityEvent.END)
}