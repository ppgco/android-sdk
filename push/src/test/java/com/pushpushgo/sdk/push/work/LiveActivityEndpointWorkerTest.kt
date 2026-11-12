package com.pushpushgo.sdk.push.work

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.DefaultApiServiceFactory
import com.pushpushgo.sdk.push.network.FakeApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.otherProjectTestConfig
import com.pushpushgo.sdk.push.testConfig
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LiveActivityEndpointWorkerTest {
  private val config = testConfig()

  private val apiService = FakeApiService()
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    preferences = SharedPreferencesHelper(getApplicationContext())
    preferences.clearProjectData()
    preferences.projectOwner = config
    preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
    preferences.setLiveActivitySubscriberId("live-2", "live-sub-2")

    mockkObject(DefaultApiServiceFactory)
    every { DefaultApiServiceFactory.create(config) } returns apiService
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    unmockkObject(DefaultApiServiceFactory)
  }

  @Test
  fun `points every live activity subscription at the new token`() =
    runTest {
      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      val subscribersUrl = "${config.apiUrl}/core/v1/android/projects/${config.projectId}/live-notifications"
      assertEquals(
        setOf("$subscribersUrl/live-1/subscribers/live-sub-1/endpoint", "$subscribersUrl/live-2/subscribers/live-sub-2/endpoint"),
        apiService.updatedLiveActivityEndpoints.map { it.first }.toSet(),
      )
      assertEquals(listOf("new-token", "new-token"), apiService.updatedLiveActivityEndpoints.map { it.second.endpoint.pushToken })
    }

  @Test
  fun `skips subscriptions that belong to another project now`() =
    runTest {
      preferences.projectOwner = otherProjectTestConfig()

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      assertTrue(apiService.updatedLiveActivityEndpoints.isEmpty())
    }

  @Test
  fun `transient failures are retried`() =
    runTest {
      apiService.exception = IOException("offline")

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.retry(), result)
    }

  @Test
  fun `rejected subscriptions are not retried`() =
    runTest {
      apiService.exception = PushPushException("Live notification subscriber not found", 400)

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
    }

  private fun worker() =
    TestListenableWorkerBuilder<LiveActivityEndpointWorker>(
      context = getApplicationContext(),
      inputData =
        workDataOf(
          LiveActivityEndpointWorker.PROJECT_ID to config.projectId,
          LiveActivityEndpointWorker.API_KEY to config.apiKey,
          LiveActivityEndpointWorker.API_URL to config.apiUrl,
          LiveActivityEndpointWorker.PUSH_TOKEN to "new-token",
        ),
    ).build()
}
