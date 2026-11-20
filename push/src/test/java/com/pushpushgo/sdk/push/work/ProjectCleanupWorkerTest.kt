package com.pushpushgo.sdk.push.work

import android.Manifest
import android.app.Application
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
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ProjectCleanupWorkerTest {
  private val previousProject = otherProjectTestConfig()

  private val apiService = FakeApiService()
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    preferences = SharedPreferencesHelper(getApplicationContext())
    preferences.clearProjectData()
    preferences.projectOwner = testConfig()

    mockkObject(DefaultApiServiceFactory)
    every { DefaultApiServiceFactory.create(previousProject) } returns apiService
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    unmockkObject(DefaultApiServiceFactory)
  }

  @Test
  fun `removes subscriber and live activity subscriptions with the previous project credentials`() =
    runTest {
      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      assertEquals(listOf("old-sub"), apiService.unregisteredSubscriberIds)
      val projectUrl = "${previousProject.apiUrl}/core/v1/android/projects/${previousProject.projectId}"
      assertEquals(
        listOf("$projectUrl/live-notifications/live-1/subscribers/live-sub-1"),
        apiService.unsubscribedLiveActivityUrls,
      )
    }

  @Test
  fun `transient failures are retried`() =
    runTest {
      apiService.exception = IOException("offline")

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.retry(), result)
    }

  @Test
  fun `rejected credentials fail the cleanup`() =
    runTest {
      apiService.exception = PushPushException("Unauthorized", 401)

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.failure(), result)
    }

  @Test
  fun `gives up after the last attempt`() =
    runTest {
      apiService.exception = IOException("offline")

      val result = worker(runAttemptCount = 29).doWork()

      assertEquals(ListenableWorker.Result.failure(), result)
    }

  @Test
  fun `leaves subscriptions the SDK uses again after returning to the project`() =
    runTest {
      preferences.projectOwner = previousProject
      preferences.subscriberId = "old-sub"
      preferences.subscriptionRequested = true
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      shadowOf(getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      assertTrue(apiService.unregisteredSubscriberIds.isEmpty())
      assertTrue(apiService.unsubscribedLiveActivityUrls.isEmpty())
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
