package com.pushpushgo.sdk.push.work

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.otherProjectTestConfig
import com.pushpushgo.sdk.push.testConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class ProjectCleanupWorkerTest {
  private val previousProject = otherProjectTestConfig()

  private lateinit var apiService: ApiService
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    preferences = SharedPreferencesHelper(getApplicationContext())
    preferences.clearProjectData()
    preferences.projectOwner = testConfig()

    apiService = mockk()
    mockkObject(ApiService.Companion)
    every { ApiService.fromConfig(any()) } returns apiService
    coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)
    coEvery { apiService.unsubscribeLiveActivity(any(), any()) } returns Response.success(null)
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    unmockkObject(ApiService.Companion)
  }

  @Test
  fun `removes subscriber and live activity subscriptions with the previous project credentials`() =
    runBlocking {
      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      coVerify(exactly = 1) {
        apiService.unregisterSubscriber(previousProject.apiKey, previousProject.projectId, "old-sub")
      }
      coVerify(exactly = 1) {
        apiService.unsubscribeLiveActivity(
          match { it.endsWith("/projects/${previousProject.projectId}/live-notifications/live-1/subscribers/live-sub-1") },
          previousProject.apiKey,
        )
      }
    }

  @Test
  fun `subscriber that is already gone counts as removed`() =
    runBlocking {
      val alreadyGone =
        listOf(
          PushPushException("Subscriber not exists", 404),
          PushPushException("Cannot perform operation on inactive subscriber", 400),
          PushPushException("Subscriber not belongs to given project", 403),
        )

      alreadyGone.forEach { error ->
        coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws error

        assertEquals(error.message, ListenableWorker.Result.success(), worker().doWork())
      }
    }

  @Test
  fun `transient failures are retried`() =
    runBlocking {
      listOf(IOException("offline"), PushPushException("Server error", 503)).forEach { error ->
        coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws error

        assertEquals(error.message, ListenableWorker.Result.retry(), worker().doWork())
      }
    }

  @Test
  fun `rejected credentials fail the cleanup`() =
    runBlocking {
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws PushPushException("Unauthorized", 401)

      assertEquals(ListenableWorker.Result.failure(), worker().doWork())
    }

  @Test
  fun `gives up after the last attempt`() =
    runBlocking {
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

      assertEquals(ListenableWorker.Result.failure(), worker(runAttemptCount = 29).doWork())
    }

  @Test
  fun `leaves subscriptions the SDK uses again after returning to the project`() =
    runBlocking {
      // Registering with the same push token returns the still active subscriber
      preferences.projectOwner = previousProject
      preferences.subscriberId = "old-sub"
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
      coVerify(exactly = 0) { apiService.unsubscribeLiveActivity(any(), any()) }
    }

  private fun worker(runAttemptCount: Int = 0) =
    TestListenableWorkerBuilder<ProjectCleanupWorker>(
      context = getApplicationContext(),
      inputData =
        workDataOf(
          ProjectCleanupWorker.PROJECT_ID to previousProject.projectId,
          ProjectCleanupWorker.API_KEY to previousProject.apiKey,
          ProjectCleanupWorker.API_URL to previousProject.apiUrl,
          ProjectCleanupWorker.SUBSCRIBER_ID to "old-sub",
          ProjectCleanupWorker.LIVE_ACTIVITY_IDS to arrayOf("live-1"),
          ProjectCleanupWorker.LIVE_ACTIVITY_SUBSCRIBER_IDS to arrayOf("live-sub-1"),
        ),
      runAttemptCount = runAttemptCount,
    ).build()
}
