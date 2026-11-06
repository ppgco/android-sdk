package com.pushpushgo.sdk.push.liveactivity

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchState
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityEvent
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPush
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityStatus
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityTemplate
import com.pushpushgo.sdk.push.liveactivity.data.MatchPhase
import com.pushpushgo.sdk.push.liveactivity.data.TrackedLiveActivity
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
internal class LiveActivityManagerTest {
  private lateinit var persistence: LiveActivityPersistence

  private lateinit var manager: LiveActivityManager

  @Before
  fun setup() {
    persistence = LiveActivityPersistence(getApplicationContext())
    manager = LiveActivityManager(persistence)
  }

  @After
  fun tearDown() {
    persistence.clearAll()
  }

  @Test
  fun `startActivity stores and returns activity`() {
    val activity = assertNotNull(manager.startActivity(startPush()))

    assertEquals("test-la-1", activity.id)
    assertEquals(LiveActivityStatus.ACTIVE, activity.status)
    assertEquals(2, activity.football.liveData.homeTeamScore)
    assertEquals("Arsenal", activity.football.configuration.content.homeTeamName)
  }

  @Test
  fun `startActivity returns null without configuration`() {
    assertNull(manager.startActivity(startPush().copy(configurationJson = null)))
  }

  @Test
  fun `updateActivity merges new live data onto existing state`() {
    manager.startActivity(startPush())

    val updated = assertNotNull(manager.updateActivity(updatePush(liveDataJson(homeScore = 3, status = MatchPhase.SECOND_HALF))))

    assertEquals(3, updated.football.liveData.homeTeamScore)
    assertEquals(MatchPhase.SECOND_HALF, updated.football.liveData.status)
    assertEquals("Premier League", updated.football.configuration.content.title)
  }

  @Test
  fun `updateActivity keeps pre-match countdown attached to the state`() {
    manager.startActivity(startPush())
    manager.setCountdown("test-la-1", "Kick-off soon", 1_000L)

    val updated = assertNotNull(manager.updateActivity(updatePush(liveDataJson(homeScore = 1))))

    assertEquals(1, updated.football.liveData.homeTeamScore)
    assertEquals("Kick-off soon", updated.football.countdownMessage)
    assertEquals(1_000L, updated.football.countdownEndAtMs)
  }

  @Test
  fun `setCountdown attaches countdown to football state and persists it`() {
    manager.startActivity(startPush())

    val activity = assertNotNull(manager.setCountdown("test-la-1", "Kick-off soon", 1_000L))
    val restartedManager = LiveActivityManager(persistence)
    restartedManager.restoreFromPersistence()

    assertEquals("Kick-off soon", activity.football.countdownMessage)
    assertEquals(1_000L, activity.football.countdownEndAtMs)
    val restored = assertNotNull(restartedManager.getActivity("test-la-1"))
    assertEquals("Kick-off soon", restored.football.countdownMessage)
    assertEquals(1_000L, restored.football.countdownEndAtMs)
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

    val ended = assertNotNull(manager.endActivity(endPush()))

    assertEquals(LiveActivityStatus.ENDED, ended.status)
  }

  @Test
  fun `started activity is restored by a new manager`() {
    val started = assertNotNull(manager.startActivity(startPush()))
    val restartedManager = LiveActivityManager(persistence)

    restartedManager.restoreFromPersistence()

    val restored = assertNotNull(restartedManager.getActivity("test-la-1"))
    assertEquals(LiveActivityStatus.ACTIVE, restored.status)
    assertEquals(started.football.configuration, restored.football.configuration)
    assertEquals(started.football.liveData, restored.football.liveData)
  }

  @Test
  fun `restarting an activity keeps its notification id`() {
    manager.startActivity(startPush())
    val notificationId = manager.getNotificationId("test-la-1")

    LiveActivityManager(persistence).startActivity(startPush())

    assertEquals(notificationId, persistence.getNotificationId("test-la-1"))
  }

  @Test
  fun `ended activity is not restored and is cleared from storage`() {
    manager.startActivity(startPush())
    manager.endActivity(endPush())
    val restartedManager = LiveActivityManager(persistence)

    restartedManager.restoreFromPersistence()

    assertNull(restartedManager.getActivity("test-la-1"))
    assertNull(persistence.rebuild("test-la-1"))
    assertEquals(emptySet(), persistence.getActiveIds())
  }

  @Test
  fun `removed activity is cleared from storage`() {
    manager.startActivity(startPush())

    manager.removeActivity("test-la-1")

    assertNull(persistence.rebuild("test-la-1"))
    assertEquals(emptySet(), persistence.getActiveIds())
  }

  private fun liveDataJson(
    homeScore: Int = 2,
    awayScore: Int = 1,
    status: MatchPhase = MatchPhase.FIRST_HALF,
  ) = """{"homeTeamScore":$homeScore,"awayTeamScore":$awayScore,"status":"${status.value}"}"""

  private fun startPush() =
    LiveActivityPush(
      id = "test-la-1",
      projectId = "proj-1",
      subscriberId = "sub-1",
      template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
      event = LiveActivityEvent.START,
      configurationJson = CONFIGURATION_JSON,
      liveDataJson = liveDataJson(),
      hotMessage = null,
    )

  private fun updatePush(liveDataJson: String = liveDataJson()) =
    LiveActivityPush(
      id = "test-la-1",
      projectId = "proj-1",
      subscriberId = "sub-1",
      template = LiveActivityTemplate.FOOTBALL_MATCH_TRACKING,
      event = LiveActivityEvent.UPDATE,
      configurationJson = null,
      liveDataJson = liveDataJson,
      hotMessage = null,
    )

  private fun endPush() = updatePush().copy(event = LiveActivityEvent.END)

  private val TrackedLiveActivity.football: FootballMatchState
    get() = assertIs(state)

  private companion object {
    const val CONFIGURATION_JSON =
      """{"content":{"title":"Premier League","homeTeamName":"Arsenal","awayTeamName":"Chelsea"},""" +
        """"statusLabels":{"FIRST_HALF":"1st Half"},"timeout":{"minutes":120},"url":"https://match/1"}"""
  }
}
