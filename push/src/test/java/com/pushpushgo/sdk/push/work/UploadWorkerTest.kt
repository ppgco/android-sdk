package com.pushpushgo.sdk.push.work

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.DefaultApiServiceFactory
import com.pushpushgo.sdk.push.network.FakeApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.testConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
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
class UploadWorkerTest {
  private val config = testConfig()

  private val apiService = FakeApiService()

  @Before
  fun setUp() {
    mockkObject(DefaultApiServiceFactory)
    every { DefaultApiServiceFactory.create(config) } returns apiService
  }

  @After
  fun tearDown() {
    SharedPreferencesHelper(getApplicationContext()).clearProjectData()
    unmockkObject(DefaultApiServiceFactory, PushNotifications)
  }

  @Test
  fun `event work sends the persisted event payload`() =
    runTest {
      val worker =
        TestListenableWorkerBuilder<UploadWorker>(
          context = getApplicationContext(),
          inputData =
            workDataOf(
              UploadWorker.TYPE to UploadWorker.EVENT,
              UploadWorker.WORK_PROJECT_ID to config.projectId,
              UploadWorker.WORK_API_KEY to config.apiKey,
              UploadWorker.WORK_API_URL to config.apiUrl,
              UploadWorker.EVENT_TYPE to EventType.CLICKED.name,
              UploadWorker.EVENT_BUTTON_ID to 2,
              UploadWorker.EVENT_CAMPAIGN to "campaign",
              UploadWorker.EVENT_SUBSCRIBER_ID to "subscriberId",
            ),
        ).build()

      val result = worker.doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      val event = apiService.sentEvents.single()
      assertEquals(EventType.CLICKED.value, event.type)
      assertEquals(2, event.payload.button)
      assertEquals("campaign", event.payload.campaign)
      assertEquals("subscriberId", event.payload.subscriber)
    }

  @Test
  fun `token sync skips a subscriber that is no longer active`() =
    runTest {
      SharedPreferencesHelper(getApplicationContext()).subscriberId = "current-subscriber"
      val worker =
        TestListenableWorkerBuilder<UploadWorker>(
          context = getApplicationContext(),
          inputData =
            workDataOf(
              UploadWorker.TYPE to UploadWorker.SYNC_TOKEN,
              UploadWorker.WORK_PROJECT_ID to config.projectId,
              UploadWorker.WORK_API_KEY to config.apiKey,
              UploadWorker.WORK_API_URL to config.apiUrl,
              UploadWorker.SYNC_TOKEN_SUBSCRIBER_ID to "previous-subscriber",
              UploadWorker.SYNC_TOKEN_TOKEN to "new-token",
            ),
        ).build()

      val result = worker.doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      assertTrue(apiService.updatedTokens.isEmpty())
    }

  @Test
  fun `token sync replaces a subscriber the API no longer accepts`() =
    runTest {
      setActiveSubscriber("S")
      grantNotifications()
      apiService.tokenUpdateException = PushPushException("Subscriber not exists", 404)
      mockkObject(PushNotifications)
      coEvery { PushNotifications.replaceInvalidSubscriber(config, "S") } just runs

      val result = tokenSyncWorker(subscriberId = "S").doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      coVerify(exactly = 1) { PushNotifications.replaceInvalidSubscriber(config, "S") }
    }

  @Test
  fun `token sync keeps the subscriber when the API rejects the credentials`() =
    runTest {
      setActiveSubscriber("S")
      grantNotifications()
      apiService.tokenUpdateException = PushPushException("Illegal access to use-case", 403)
      mockkObject(PushNotifications)

      val result = tokenSyncWorker(subscriberId = "S").doWork()

      assertEquals(ListenableWorker.Result.failure(), result)
      coVerify(exactly = 0) { PushNotifications.replaceInvalidSubscriber(any(), any()) }
    }

  @Test
  fun `beacon is discarded when its subscriber is no longer active`() =
    runTest {
      setActiveSubscriber("new-subscriber")

      val result = beaconWorker(subscriberId = "S").doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      assertTrue(apiService.sentBeacons.isEmpty())
    }

  @Test
  fun `beacon is discarded when the subscription is no longer requested`() =
    runTest {
      setActiveSubscriber("S")
      SharedPreferencesHelper(getApplicationContext()).subscriptionRequested = false

      val result = beaconWorker(subscriberId = "S").doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      assertTrue(apiService.sentBeacons.isEmpty())
    }

  @Test
  fun `beacon is retried after a network error`() =
    runTest {
      setActiveSubscriber("S")
      apiService.exception = IOException("offline")

      val result = beaconWorker(subscriberId = "S").doWork()

      assertEquals(ListenableWorker.Result.retry(), result)
    }

  private fun setActiveSubscriber(subscriberId: String) {
    SharedPreferencesHelper(getApplicationContext()).apply {
      projectOwner = config
      this.subscriberId = subscriberId
      subscriptionRequested = true
    }
  }

  private fun grantNotifications() {
    shadowOf(getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun tokenSyncWorker(subscriberId: String): UploadWorker =
    TestListenableWorkerBuilder<UploadWorker>(
      context = getApplicationContext(),
      inputData =
        workDataOf(
          UploadWorker.TYPE to UploadWorker.SYNC_TOKEN,
          UploadWorker.WORK_PROJECT_ID to config.projectId,
          UploadWorker.WORK_API_KEY to config.apiKey,
          UploadWorker.WORK_API_URL to config.apiUrl,
          UploadWorker.SYNC_TOKEN_SUBSCRIBER_ID to subscriberId,
          UploadWorker.SYNC_TOKEN_TOKEN to "new-token",
        ),
    ).build()

  private fun beaconWorker(subscriberId: String): UploadWorker =
    TestListenableWorkerBuilder<UploadWorker>(
      context = getApplicationContext(),
      inputData =
        workDataOf(
          UploadWorker.TYPE to UploadWorker.BEACON,
          UploadWorker.WORK_PROJECT_ID to config.projectId,
          UploadWorker.WORK_API_KEY to config.apiKey,
          UploadWorker.WORK_API_URL to config.apiUrl,
          UploadWorker.BEACON_SUBSCRIBER_ID to subscriberId,
          UploadWorker.BEACON_PAYLOAD to "{}",
        ),
    ).build()
}
