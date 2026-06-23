package com.pushpushgo.sdk.push.work

import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.network.ApiRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class UploadDelegateTest {
  private val apiRepository = mockk<ApiRepository>(relaxed = true)
  private val delegate = UploadDelegate(apiRepository)

  @Test
  fun `sendEvent forwards mapped fields to the repository (ISSUE-01)`() =
    runBlocking {
      delegate.sendEvent(
        type = EventType.CLICKED,
        buttonId = 2,
        campaign = "camp",
        projectId = "proj",
        subscriberId = "sub",
      )

      coVerify {
        apiRepository.sendEvent(
          type = EventType.CLICKED,
          buttonId = 2,
          campaign = "camp",
          project = "proj",
          subscriber = "sub",
        )
      }
    }
}
