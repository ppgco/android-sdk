package com.pushpushgo.sdk.push.network

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.network.data.TokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class ApiRepositoryTest {
  private val config =
    Config.create(
      projectId = "hm93nzyt5bmczmtjeghy2aph",
      apiKey = "e5d706d7-0ebb-4793-9edc-6bd9eb9aff3a",
    )

  private lateinit var apiService: ApiService
  private lateinit var prefs: SharedPreferencesHelper
  private lateinit var repository: ApiRepository

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(getApplicationContext())
    // logDebug() resolves config through the SDK singleton, so it must exist.
    PushNotifications.initialize(getApplicationContext(), config)
    apiService = mockk(relaxed = true)
    prefs = SharedPreferencesHelper(getApplicationContext(), prefsName = "api_repo_test")
    repository = ApiRepository(getApplicationContext(), apiService, prefs, config)
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
  fun `sendEvent prefers the payload subscriber over local state (ISSUE-09)`() =
    runBlocking {
      prefs.subscriberId = "local-sub"

      repository.sendEvent(
        type = EventType.DELIVERED,
        buttonId = 0,
        campaign = "camp",
        project = "proj",
        subscriber = "payload-sub",
      )

      coVerify {
        apiService.sendEvent(
          token = any(),
          projectId = "proj",
          event = match { it.payload.subscriber == "payload-sub" },
        )
      }
    }

  @Test
  fun `sendEvent falls back to local subscriber when payload subscriber is blank (ISSUE-09)`() =
    runBlocking {
      prefs.subscriberId = "local-sub"

      repository.sendEvent(
        type = EventType.DELIVERED,
        buttonId = 0,
        campaign = "camp",
        project = null,
        subscriber = "   ",
      )

      coVerify {
        apiService.sendEvent(
          token = any(),
          projectId = config.projectId,
          event = match { it.payload.subscriber == "local-sub" },
        )
      }
    }
}
