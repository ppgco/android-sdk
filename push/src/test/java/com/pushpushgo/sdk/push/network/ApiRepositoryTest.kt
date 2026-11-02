package com.pushpushgo.sdk.push.network

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeResponse
import com.pushpushgo.sdk.push.network.data.TokenResponse
import com.pushpushgo.sdk.push.testConfig
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class ApiRepositoryTest {
  private val config = testConfig()

  private lateinit var apiService: ApiService
  private lateinit var prefs: SharedPreferencesHelper
  private lateinit var repository: ApiRepository

  @Before
  fun setUp() {
    apiService = mockk()
    prefs = SharedPreferencesHelper(getApplicationContext(), prefsName = "api_repo_test")
    prefs.clearProjectData()
    repository = ApiRepository(getApplicationContext(), apiService, prefs, config)
  }

  @After
  fun tearDown() {
    prefs.clearProjectData()
  }

  @Test
  fun `registerToken persists the registered token and subscriber id (ISSUE-03)`() =
    runBlocking {
      coEvery { apiService.registerSubscriber(any(), any(), any()) } returns TokenResponse(id = "sub-1")

      repository.registerToken("token-xyz")

      assertEquals("token-xyz", prefs.lastToken)
      assertEquals("sub-1", prefs.subscriberId)
    }

  @Test
  fun `unregisterSubscriber treats missing subscriber as already unregistered`() =
    runBlocking {
      prefs.subscriberId = "sub-1"
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws
        PushPushException("Subscriber not exists", 404)

      repository.unregisterSubscriber()

      assertNull(prefs.subscriberId)
    }

  @Test
  fun `unregisterSubscriber treats inactive subscriber as already unregistered`() =
    runBlocking {
      prefs.subscriberId = "sub-1"
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws
        PushPushException("Cannot perform operation on inactive subscriber", 400)

      repository.unregisterSubscriber()

      assertNull(prefs.subscriberId)
    }

  @Test
  fun `unregisterSubscriber treats subscriber of another project as already unregistered`() =
    runBlocking {
      prefs.subscriberId = "sub-1"
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws
        PushPushException("Subscriber not belongs to given project", 403)

      repository.unregisterSubscriber()

      assertNull(prefs.subscriberId)
    }

  @Test
  fun `deleteSubscriber removes the given subscriber and leaves local state alone`() =
    runBlocking {
      prefs.subscriberId = "current-sub"
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns retrofit2.Response.success(null)

      repository.deleteSubscriber("other-sub")

      coVerify(exactly = 1) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "other-sub") }
      assertEquals("current-sub", prefs.subscriberId)
    }

  @Test
  fun `unregisterSubscriber propagates other API errors and preserves subscriber`() =
    runBlocking {
      prefs.subscriberId = "sub-1"
      val expected = PushPushException("Unexpected error", 400)
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws expected

      val actual = runCatching { repository.unregisterSubscriber() }.exceptionOrNull()

      assertSame(expected, actual)
      assertEquals("sub-1", prefs.subscriberId)
    }

  @Test
  fun `subscribeToLiveActivity sends the platform push token without a push registration`() =
    runBlocking {
      mockkStatic(PUSH_TOKEN_UTILS)
      try {
        coEvery { getPlatformPushToken(any()) } returns "push-token"
        coEvery { apiService.subscribeLiveActivity(any(), any(), any()) } returns
          LiveActivitySubscribeResponse(id = "live-sub-1")

        val liveActivitySubscriberId = repository.subscribeToLiveActivity("live-1")

        assertNull(prefs.lastToken)
        assertNull(prefs.subscriberId)
        assertEquals("live-sub-1", liveActivitySubscriberId)
        coVerify(exactly = 1) {
          apiService.subscribeLiveActivity(any(), any(), match { it.endpoint.pushToken == "push-token" })
        }
      } finally {
        unmockkStatic(PUSH_TOKEN_UTILS)
      }
    }

  @Test
  fun `unsubscribeFromLiveActivity treats missing live notification as success`() =
    runBlocking {
      coEvery { apiService.unsubscribeLiveActivity(any(), any()) } throws
        PushPushException("Live notification not found", 400)

      repository.unsubscribeFromLiveActivity("live-1", "live-sub-1")
    }

  @Test
  fun `unsubscribeFromLiveActivity treats missing live notification subscriber as success`() =
    runBlocking {
      coEvery { apiService.unsubscribeLiveActivity(any(), any()) } throws
        PushPushException("Live notification subscriber not found", 400)

      repository.unsubscribeFromLiveActivity("live-1", "live-sub-1")
    }

  @Test
  fun `unsubscribeFromLiveActivity propagates other errors`() =
    runBlocking {
      val expected = PushPushException("Unexpected error", 400)

      coEvery { apiService.unsubscribeLiveActivity(any(), any()) } throws expected

      val actual = runCatching { repository.unsubscribeFromLiveActivity("live-1", "live-sub-1") }.exceptionOrNull()

      assertSame(expected, actual)
    }

  private companion object {
    const val PUSH_TOKEN_UTILS = "com.pushpushgo.sdk.push.utils.PushTokenUtilsKt"
  }
}
