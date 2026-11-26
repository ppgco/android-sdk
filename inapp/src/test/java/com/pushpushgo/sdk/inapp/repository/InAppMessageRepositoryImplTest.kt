package com.pushpushgo.sdk.inapp.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.inapp.model.InAppMessage
import com.pushpushgo.sdk.inapp.model.network.InAppMessagesResponse
import com.pushpushgo.sdk.inapp.network.InAppListGetApi
import com.pushpushgo.sdk.inapp.persistence.InAppMessagePersistence
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.IOException

@RunWith(AndroidJUnit4::class)
internal class InAppMessageRepositoryImplTest {
  private val cachedMessages = listOf(mockk<InAppMessage>())

  private val persistence =
    mockk<InAppMessagePersistence>(relaxed = true) {
      every { getStoredETag() } returns "etag"
      every { getCachedMessages() } returns cachedMessages
    }

  private val api = mockk<InAppListGetApi>()

  private val repository = InAppMessageRepositoryImpl(api, projectId = "project", apiKey = "key", persistence = persistence)

  @Test
  fun `revoked key or feature clears the cached messages`() =
    runTest {
      for (code in listOf(401, 403)) {
        respondWith(code)

        assertTrue("HTTP $code", repository.fetchMessages().isEmpty())
      }

      verify(exactly = 2) { persistence.clearCache() }
    }

  @Test
  fun `server error keeps showing the cached messages`() =
    runTest {
      respondWith(500)

      assertEquals(cachedMessages, repository.fetchMessages())
      verify(exactly = 0) { persistence.clearCache() }
    }

  @Test
  fun `network error keeps showing the cached messages`() =
    runTest {
      coEvery { api.getInAppMessages(any(), any(), any(), any(), any(), any(), any()) } throws IOException("offline")

      assertEquals(cachedMessages, repository.fetchMessages())
      verify(exactly = 0) { persistence.clearCache() }
    }

  private fun respondWith(code: Int) {
    coEvery { api.getInAppMessages(any(), any(), any(), any(), any(), any(), any()) } returns
      Response.error<InAppMessagesResponse>(code, """{"message":"Illegal access to use-case"}""".toResponseBody())
  }
}
