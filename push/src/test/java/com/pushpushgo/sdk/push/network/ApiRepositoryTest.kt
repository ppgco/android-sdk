package com.pushpushgo.sdk.push.network

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.testConfig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class ApiRepositoryTest {
  private val config = testConfig()

  private lateinit var apiService: FakeApiService
  private lateinit var prefs: SharedPreferencesHelper
  private lateinit var repository: ApiRepository

  @Before
  fun setUp() {
    apiService = FakeApiService()
    prefs = SharedPreferencesHelper(getApplicationContext(), prefsName = "api_repo_test")
    prefs.clearProjectData()
    repository = ApiRepository(apiService, prefs, config) { "push-token" }
  }

  @After
  fun tearDown() {
    prefs.clearProjectData()
  }

  @Test
  fun `registerToken persists the registered token and subscriber id`() =
    runTest {
      repository.registerToken("token-xyz")

      assertEquals("token-xyz", prefs.lastToken)
      assertEquals(FakeApiService.SUBSCRIBER_ID, prefs.subscriberId)
    }

  @Test
  fun `unregisterSubscriber treats missing, inactive and foreign subscribers as already unregistered`() =
    runTest {
      apiService.exception = PushPushException("Subscriber not exists", 404)
      prefs.subscriberId = "missing-subscriber"
      repository.unregisterSubscriber()
      assertNull(prefs.subscriberId)

      apiService.exception = PushPushException("Cannot perform operation on inactive subscriber", 400)
      prefs.subscriberId = "inactive-subscriber"
      repository.unregisterSubscriber()
      assertNull(prefs.subscriberId)

      apiService.exception = PushPushException("Subscriber not belongs to given project", 403)
      prefs.subscriberId = "foreign-subscriber"
      repository.unregisterSubscriber()
      assertNull(prefs.subscriberId)
    }

  @Test
  fun `unregisterSubscriber propagates other API errors and preserves subscriber`() =
    runTest {
      prefs.subscriberId = "subscriber"
      val expected = PushPushException("Unexpected error", 400)
      apiService.exception = expected

      val actual = assertFailsWith<PushPushException> { repository.unregisterSubscriber() }

      assertSame(expected, actual)
      assertEquals("subscriber", prefs.subscriberId)
    }

  @Test
  fun `subscribeToLiveActivity sends the platform push token without a push registration`() =
    runTest {
      repository.subscribeToLiveActivity("live-1")

      assertEquals(listOf("push-token"), apiService.liveActivitySubscribeRequests.map { it.endpoint.pushToken })
      assertNull(prefs.subscriberId)
      assertNull(prefs.lastToken)
    }

  @Test
  fun `live activity calls use the android endpoints`() =
    runTest {
      repository.subscribeToLiveActivity("live-1")
      repository.fetchLiveActivity("live-1")
      repository.unsubscribeFromLiveActivity("live-1", "live-sub-1")

      val liveNotificationUrl = "${config.apiUrl}/core/v1/android/projects/${config.projectId}/live-notifications/live-1"
      assertEquals(listOf("$liveNotificationUrl/subscribers"), apiService.liveActivitySubscribeUrls)
      assertEquals(listOf(liveNotificationUrl), apiService.fetchedLiveActivityUrls)
      assertEquals(listOf("$liveNotificationUrl/subscribers/live-sub-1"), apiService.unsubscribedLiveActivityUrls)
    }

  @Test
  fun `unsubscribeFromLiveActivity treats missing live notification or subscriber as success`() =
    runTest {
      apiService.exception = PushPushException("Live notification not found", 400)
      repository.unsubscribeFromLiveActivity("live-1", "live-activity-subscriber")

      apiService.exception = PushPushException("Live notification subscriber not found", 400)
      repository.unsubscribeFromLiveActivity("live-1", "live-activity-subscriber")
    }

  @Test
  fun `unsubscribeFromLiveActivity propagates other errors`() =
    runTest {
      val expected = PushPushException("Unexpected error", 400)
      apiService.exception = expected

      val actual = runCatching { repository.unsubscribeFromLiveActivity("live-1", "live-sub-1") }.exceptionOrNull()

      assertSame(expected, actual)
    }
}
