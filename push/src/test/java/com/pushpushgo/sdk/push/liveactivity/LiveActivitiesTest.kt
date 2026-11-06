package com.pushpushgo.sdk.push.liveactivity

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.PushNotificationsCallbacks
import com.pushpushgo.sdk.push.PushNotificationsRuntime
import com.pushpushgo.sdk.push.liveactivity.notification.LiveActivityNotificationChannel
import com.pushpushgo.sdk.push.network.FakeApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.testConfig
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LiveActivitiesTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()

  private val apiService = FakeApiService()
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    LiveActivityPersistence(application).clearAll()
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  @Test
  fun `subscribe registers the device for the live activity and stores its subscriber id`() =
    runTest {
      val liveActivities = createRuntime().liveActivities

      val liveActivitySubscriberId = liveActivities.subscribe("live-1")

      assertEquals(1, apiService.liveActivitySubscribeRequests.size)
      assertEquals(FakeApiService.LIVE_ACTIVITY_SUBSCRIBER_ID, liveActivitySubscriberId)
      assertEquals(FakeApiService.LIVE_ACTIVITY_SUBSCRIBER_ID, preferences.getLiveActivitySubscriberId("live-1"))
    }

  @Test
  fun `subscribe fails without notification permission and does not register the device`() =
    runTest {
      shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
      val liveActivities = createRuntime().liveActivities

      assertFailsWith<IllegalStateException> { liveActivities.subscribe("live-1") }

      assertTrue(apiService.liveActivitySubscribeRequests.isEmpty())
      assertEquals("", preferences.getLiveActivitySubscriberId("live-1"))
    }

  @Test
  fun `subscribe fails when the live activity channel is disabled and does not register the device`() =
    runTest {
      val liveActivities = createRuntime().liveActivities
      NotificationManagerCompat.from(application).createNotificationChannel(
        NotificationChannel(
          LiveActivityNotificationChannel.getChannelId(application),
          "Live Activities",
          NotificationManager.IMPORTANCE_NONE,
        ),
      )

      assertFailsWith<IllegalStateException> { liveActivities.subscribe("live-1") }

      assertTrue(apiService.liveActivitySubscribeRequests.isEmpty())
      assertEquals("", preferences.getLiveActivitySubscriberId("live-1"))
    }

  @Test
  fun `unsubscribe removes the live activity subscriber from the current project`() =
    runTest {
      val liveActivities = createRuntime().liveActivities
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")

      liveActivities.unsubscribe("live-1")

      assertEquals(
        listOf("${config.apiUrl}/core/projects/${config.projectId}/live-notifications/live-1/subscribers/live-sub-1"),
        apiService.unsubscribedLiveActivityUrls,
      )
      assertTrue(preferences.getLiveActivitySubscriberId("live-1").isEmpty())
    }

  @Test
  fun `unsubscribe does nothing when the device is not subscribed`() =
    runTest {
      val liveActivities = createRuntime().liveActivities

      liveActivities.unsubscribe("live-1")

      assertTrue(apiService.unsubscribedLiveActivityUrls.isEmpty())
    }

  private fun TestScope.createRuntime() =
    PushNotificationsRuntime(
      application = application,
      config = config,
      callbacks = PushNotificationsCallbacks(),
      apiServiceFactory = { apiService },
      pushTokenProvider = { "push-token" },
      dispatcher = StandardTestDispatcher(testScheduler),
    )
}
