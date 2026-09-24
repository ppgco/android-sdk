package com.pushpushgo.sdk.push.work

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.testConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class UploadWorkerTest {
  private val config = testConfig()

  private lateinit var apiService: ApiService

  @Before
  fun setUp() {
    apiService = mockk()
    mockkObject(ApiService.Companion)
    every { ApiService.fromConfig(any()) } returns apiService
    coEvery { apiService.sendEvent(any(), any(), any()) } returns Response.success(null)
  }

  @After
  fun tearDown() {
    unmockkObject(ApiService.Companion)
  }

  @Test
  fun `event work sends the persisted event payload`() =
    runBlocking {
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

      assertTrue(result is ListenableWorker.Result.Success)
      coVerify(exactly = 1) {
        apiService.sendEvent(
          token = config.apiKey,
          projectId = config.projectId,
          event =
            match {
              it.type == EventType.CLICKED.value &&
                it.payload.button == 2 &&
                it.payload.campaign == "campaign" &&
                it.payload.subscriber == "subscriberId"
            },
        )
      }
    }
}
