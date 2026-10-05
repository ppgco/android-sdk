package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.network.data.TokenResponse
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import com.pushpushgo.sdk.push.work.UploadManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
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
import org.robolectric.Shadows.shadowOf
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

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
    mockkConstructor(NotificationStatusChecker::class)
    every { anyConstructed<NotificationStatusChecker>().start() } just Runs
    every { ApiService.fromConfig(any()) } returns apiService
    coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)
    coEvery { apiService.unsubscribeLiveActivity(any(), any()) } returns Response.success(null)
    mockkStatic(PUSH_TOKEN_UTILS)
    coEvery { getPlatformPushToken(any()) } returns "push-token"

    PushNotifications.initialize(application, config)
  }

  @After
  fun tearDown() {
    PushNotifications.setNotificationClickHandler(null)
    PushNotifications.setInvalidProjectIdHandler(null)
    PushNotifications.setErrorCallback(null)

    if (PushNotifications.isInitialized()) {
      PushNotifications.sharedPreferencesHelper.subscriptionRequested = false
      runBlocking { PushNotifications.deinitialize() }
    }

    WorkManagerTestInitHelper.closeWorkDatabase()
    unmockkConstructor(NotificationStatusChecker::class)
    unmockkObject(ApiService.Companion)
    unmockkStatic(PUSH_TOKEN_UTILS)
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

      assertTrue("Expected IOException, got: $failure", failure is IOException)
      assertTrue("SDK should remain initialized", PushNotifications.isInitialized())
      assertEquals("Subscriber ID should be preserved", "sub-123", preferences.subscriberId)
      assertEquals(
        "Removed Live Activity subscription should stay removed",
        "",
        preferences.getLiveActivitySubscriberId("live-1"),
      )
    }

  @Test
  fun `deinitialize skips unregister and clears stale subscriber when not subscribed`() =
    runBlocking {
      preferences.subscriberId = "stale-sub-123"
      preferences.subscriptionRequested = false

      PushNotifications.deinitialize()

      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
      assertFalse(PushNotifications.isInitialized())
      assertNull(preferences.subscriberId)
    }

  @Test
  fun `cached live activities facade rejects calls after deinitialize`() =
    runBlocking {
      preferences.subscriptionRequested = false

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
      val finishUnregister = CompletableDeferred<Unit>()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } coAnswers {
        finishUnregister.await()
        Response.success(null)
      }

      val deinitialize = launch(start = CoroutineStart.UNDISPATCHED) { PushNotifications.deinitialize() }

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

      preferences.subscriptionRequested = false
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

  @Test
  fun `switchProject switches locally while offline and removes the previous subscriber later`() =
    runBlocking {
      setSubscribed()
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      givenNotificationsEnabled()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } throws IOException("offline")
      val errors = CopyOnWriteArrayList<Throwable>()
      PushNotifications.setErrorCallback { errors += it }

      PushNotifications.switchProject(otherConfig)

      assertEquals(otherConfig.projectId, PushNotifications.getProjectId())
      awaitTrue("Failed subscription should be reported, got: $errors") { errors.any { it is IOException } }
      assertFalse(PushNotifications.isSubscribed())
      assertTrue("Subscription request should survive", preferences.subscriptionRequested)
      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }

      runCleanupWork()

      coVerify(timeout = 5_000) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "sub-123") }
      coVerify(timeout = 5_000) {
        apiService.unsubscribeLiveActivity(
          match { it.endsWith("/projects/${config.projectId}/live-notifications/live-1/subscribers/live-sub-1") },
          config.apiKey,
        )
      }
    }

  @Test
  fun `switchProject returns before the device is subscribed to the new project in the background`() =
    runBlocking {
      setSubscribed()
      givenNotificationsEnabled()
      val finishRegistration = CompletableDeferred<Unit>()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } coAnswers {
        finishRegistration.await()
        TokenResponse(id = "new-sub")
      }

      try {
        PushNotifications.switchProject(otherConfig)

        assertEquals(otherConfig.projectId, PushNotifications.getProjectId())
        assertFalse("Subscription should still be in progress", PushNotifications.isSubscribed())
      } finally {
        finishRegistration.complete(Unit)
      }

      awaitTrue("Device should get subscribed to the new project") { PushNotifications.isSubscribed() }
      coVerify(exactly = 1) { apiService.registerSubscriber(otherConfig.apiKey, otherConfig.projectId, any()) }
      assertEquals("new-sub", PushNotifications.getSubscriberId())
      assertEquals(1, cleanupWork().size)
    }

  @Test
  fun `switchProject keeps handlers and does not subscribe a user who was not subscribed`() {
    var invalidProject: Triple<String, String, String>? = null
    PushNotifications.setInvalidProjectIdHandler { pushProjectId, pushSubscriberId, currentProjectId ->
      invalidProject = Triple(pushProjectId, pushSubscriberId, currentProjectId)
    }
    givenNotificationsEnabled()
    val previousLiveActivities = PushNotifications.liveActivities

    PushNotifications.switchProjectAsync(otherConfig).get()

    coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
    assertEquals(otherConfig.projectId, PushNotifications.getProjectId())
    assertTrue(cleanupWork().isEmpty())
    assertEquals(
      "PushNotifications is deinitialized",
      runCatching { previousLiveActivities.getSubscriberId("live-1") }.exceptionOrNull()?.message,
    )

    PushNotifications.handleBackgroundNotificationClick(
      Intent()
        .putExtra(PushNotificationDelegate.PROJECT_ID_EXTRA, config.projectId)
        .putExtra(PushNotificationDelegate.SUBSCRIBER_ID_EXTRA, "subscriber"),
    )

    assertEquals(Triple(config.projectId, "subscriber", otherConfig.projectId), invalidProject)
  }

  @Test
  fun `switchProject to the current configuration does nothing`() =
    runBlocking {
      setSubscribed()
      val liveActivities = PushNotifications.liveActivities

      PushNotifications.switchProject(config)

      assertEquals("sub-123", PushNotifications.getSubscriberId())
      assertTrue(cleanupWork().isEmpty())
      assertEquals("Runtime should stay active", "", liveActivities.getSubscriberId("live-1"))
    }

  @Test
  fun `switchProject requires an initialized SDK`() =
    runBlocking {
      PushNotifications.deinitialize()

      val failure = runCatching { PushNotifications.switchProject(otherConfig) }.exceptionOrNull()

      assertEquals("PushNotifications SDK is not initialized", failure?.message)
    }

  private fun setSubscribed() {
    preferences.subscriberId = "sub-123"
    preferences.subscriptionRequested = true
  }

  private fun awaitTrue(
    message: String,
    condition: () -> Boolean,
  ) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      Thread.sleep(20)
    }
    assertTrue(message, condition())
  }

  private fun givenNotificationsEnabled() {
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun cleanupWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosByTag(UploadManager.PROJECT_CLEANUP_TAG).get()

  private fun runCleanupWork() {
    val work = cleanupWork().single()
    requireNotNull(WorkManagerTestInitHelper.getTestDriver(application)).setAllConstraintsMet(work.id)
  }

  private companion object {
    const val PUSH_TOKEN_UTILS = "com.pushpushgo.sdk.push.utils.PushTokenUtilsKt"
  }
}
