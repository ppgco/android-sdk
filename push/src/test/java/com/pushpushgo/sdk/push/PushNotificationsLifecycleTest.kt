package com.pushpushgo.sdk.push

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class PushNotificationsLifecycleTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()
  private val otherConfig = otherProjectTestConfig()

  private lateinit var apiService: ApiService
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    LiveActivityPersistence(application).clearAll()

    apiService = mockk(relaxed = true)
    mockkObject(ApiService.Companion)
    every { ApiService.fromConfig(any()) } returns apiService
    coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)

    PushNotifications.initialize(application, config)
  }

  @After
  fun tearDown() {
    PushNotifications.setNotificationClickHandler(null)
    PushNotifications.setInvalidProjectIdHandler(null)
    PushNotifications.setErrorCallback(null)

    if (PushNotifications.isInitialized()) {
      PushNotifications.sharedPreferencesHelper.isSubscribed = false
      runBlocking { PushNotifications.deinitialize() }
    }

    WorkManagerTestInitHelper.closeWorkDatabase()
    unmockkObject(ApiService.Companion)
  }

  @Test
  fun `deinitialize unsubscribes clears project data and releases runtime`() =
    runBlocking {
      setSubscribed()
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      LiveActivityPersistence(application).addActiveId("live-1")

      PushNotifications.deinitialize()

      coVerify(exactly = 1) {
        apiService.unsubscribeLiveActivity(match { "/live-1/subscribers/live-sub-1" in it }, any())
      }
      coVerify(exactly = 1) { apiService.unregisterSubscriber(any(), config.projectId, "sub-123") }
      assertFalse(PushNotifications.isInitialized())
      assertNull(preferences.subscriberId)
      assertTrue(LiveActivityPersistence(application).getActiveIds().isEmpty())
    }

  @Test
  fun `deinitialize preserves state and runtime when unsubscribe fails`() =
    runBlocking {
      setSubscribed()
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

      val failure = runCatching { PushNotifications.deinitialize() }.exceptionOrNull()

      assertTrue(failure is IOException)
      assertTrue(PushNotifications.isInitialized())
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals("", preferences.getLiveActivitySubscriberId("live-1"))
    }

  @Test
  fun `deinitialize skips unregister and clears stale subscriber when not subscribed`() =
    runBlocking {
      preferences.subscriberId = "stale-sub-123"
      preferences.isSubscribed = false

      PushNotifications.deinitialize()

      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
      assertFalse(PushNotifications.isInitialized())
      assertNull(preferences.subscriberId)
    }

  @Test
  fun `cached live activities facade rejects calls after deinitialize`() =
    runBlocking {
      preferences.isSubscribed = false

      val liveActivities = PushNotifications.liveActivities

      PushNotifications.deinitialize()

      val subscribeFailure = runCatching { liveActivities.subscribe("live-1") }.exceptionOrNull()
      val queryFailure = runCatching { liveActivities.getSubscriberId("live-1") }.exceptionOrNull()

      assertEquals("PushNotifications is deinitialized", subscribeFailure?.message)
      assertEquals("PushNotifications is deinitialized", queryFailure?.message)
      coVerify(exactly = 0) { apiService.subscribeLiveActivity(any(), any(), any()) }
    }

  @Test
  fun `initialize is rejected while deinitialize is in progress`() =
    runBlocking {
      setSubscribed()
      val unregisterStarted = CompletableDeferred<Unit>()
      val finishUnregister = CompletableDeferred<Unit>()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } coAnswers {
        unregisterStarted.complete(Unit)
        finishUnregister.await()
        Response.success(null)
      }

      val deinitialize = launch(Dispatchers.Default) { PushNotifications.deinitialize() }
      unregisterStarted.await()

      val failure =
        try {
          assertThrows(IllegalStateException::class.java) {
            PushNotifications.initialize(application, config)
          }
        } finally {
          finishUnregister.complete(Unit)
        }

      deinitialize.join()

      assertEquals("PushNotifications lifecycle mutation is in progress", failure.message)
    }

  @Test
  fun `callback survives deinitialize and is used by the next runtime`() =
    runBlocking {
      var callbackArguments: Triple<String, String, String>? = null
      PushNotifications.setInvalidProjectIdHandler { pushProjectId, pushSubscriberId, currentProjectId ->
        callbackArguments = Triple(pushProjectId, pushSubscriberId, currentProjectId)
      }

      preferences.isSubscribed = false
      PushNotifications.deinitialize()
      PushNotifications.initialize(application, otherConfig)

      PushNotifications.handleBackgroundNotificationClick(
        Intent()
          .putExtra(PushNotificationDelegate.PROJECT_ID_EXTRA, config.projectId)
          .putExtra(PushNotificationDelegate.SUBSCRIBER_ID_EXTRA, "subscriber"),
      )

      assertEquals(Triple(config.projectId, "subscriber", otherConfig.projectId), callbackArguments)
    }

  @Test
  fun `null callbacks restore defaults`() {
    PushNotifications.setNotificationClickHandler { _, _, _ -> }
    PushNotifications.setInvalidProjectIdHandler { _, _, _ -> }
    PushNotifications.setErrorCallback { }

    PushNotifications.setNotificationClickHandler(null)
    PushNotifications.setInvalidProjectIdHandler(null)
    PushNotifications.setErrorCallback(null)

    assertTrue(PushNotifications.notificationClickHandler is DefaultNotificationClickHandler)
    assertTrue(PushNotifications.invalidProjectIdHandler is DefaultInvalidProjectIdHandler)
    assertNull(PushNotifications.errorCallback)
  }

  private fun setSubscribed() {
    preferences.subscriberId = "sub-123"
    preferences.isSubscribed = true
  }
}
