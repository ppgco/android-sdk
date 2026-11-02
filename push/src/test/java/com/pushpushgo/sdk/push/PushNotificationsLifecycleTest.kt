package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
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
    PushNotifications.setSubscriptionStatusCallback(null)

    runBlocking { PushNotifications.reset() }

    WorkManagerTestInitHelper.closeWorkDatabase()
    unmockkObject(ApiService.Companion)
    unmockkStatic(PUSH_TOKEN_UTILS)
  }

  @Test
  fun `cached live activities facade rejects calls after switchProject`() =
    runBlocking {
      val liveActivities = PushNotifications.liveActivities

      PushNotifications.switchProject(otherConfig)

      val subscribeFailure = runCatching { liveActivities.subscribe("live-1") }.exceptionOrNull()
      val queryFailure = runCatching { liveActivities.getSubscriberId("live-1") }.exceptionOrNull()

      assertEquals("This instance has been released", subscribeFailure?.message)
      assertEquals("This instance has been released", queryFailure?.message)
      coVerify(exactly = 0) { apiService.subscribeLiveActivity(any(), any(), any()) }
    }

  @Test
  fun `initialize is rejected while switchProject is in progress`() =
    runBlocking {
      givenNotificationsEnabled()
      val registrationStarted = CompletableDeferred<Unit>()
      val finishRegistration = CompletableDeferred<Unit>()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } coAnswers {
        registrationStarted.complete(Unit)
        finishRegistration.await()
        TokenResponse(id = "sub-123")
      }
      PushNotifications.subscribe()
      registrationStarted.await()

      val projectSwitch = launch(start = CoroutineStart.UNDISPATCHED) { PushNotifications.switchProject(otherConfig) }

      val failure =
        try {
          assertThrows(IllegalStateException::class.java) {
            PushNotifications.initialize(application, config)
          }
        } finally {
          finishRegistration.complete(Unit)
        }

      projectSwitch.join()

      assertEquals("PushNotifications lifecycle mutation is in progress", failure.message)
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
  fun `public intent getter is independent of confirmed state and permission`() {
    preferences.subscriptionRequested = false
    preferences.subscriberId = "sub-123"
    shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    assertFalse(PushNotifications.isSubscriptionRequested())
    assertTrue(PushNotifications.isSubscribed())
    assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), PushNotifications.getSubscriptionStatus())
    assertEquals("sub-123", PushNotifications.getSubscriberId())
  }

  @Test
  fun `callback set before initialization receives only initialized runtime status`() =
    runBlocking {
      PushNotifications.reset()
      preferences.subscriberId = "sub-before-init"
      val received = mutableListOf<SubscriptionStatus>()

      PushNotifications.setSubscriptionStatusCallback(received::add)
      assertTrue(received.isEmpty())
      PushNotifications.initialize(application, config)

      assertEquals(listOf(SubscriptionStatus.Subscribed("sub-before-init", config.projectId)), received)
    }

  @Test
  fun `status callback follows project switch and new subscriber id`() =
    runBlocking {
      setSubscribed()
      givenNotificationsEnabled()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } returns TokenResponse(id = "new-sub")
      val received = CopyOnWriteArrayList<SubscriptionStatus>()
      PushNotifications.setSubscriptionStatusCallback(received::add)

      PushNotifications.switchProject(otherConfig)

      awaitTrue("Callback should receive the new project's subscriber: $received") {
        received.lastOrNull() == SubscriptionStatus.Subscribed("new-sub", otherConfig.projectId)
      }
      assertEquals(
        listOf(SubscriptionStatus.Subscribed("new-sub", otherConfig.projectId)),
        received,
      )
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
        assertEquals(SubscriptionStatus.Unsubscribed, PushNotifications.subscriptionStatusFlow.value)
      } finally {
        finishRegistration.complete(Unit)
      }

      awaitTrue("Flow should report the registration done in the background") {
        PushNotifications.subscriptionStatusFlow.value == SubscriptionStatus.Subscribed("new-sub", otherConfig.projectId)
      }
      assertTrue(PushNotifications.isSubscribed())
      coVerify(exactly = 1) { apiService.registerSubscriber(otherConfig.apiKey, otherConfig.projectId, any()) }
      assertEquals("new-sub", PushNotifications.getSubscriberId())
      assertEquals(1, cleanupWork().size)
    }

  @Test
  fun `unsubscribe followed by switchProject while offline removes the previous subscriber later`() =
    runBlocking {
      setSubscribed()
      givenNotificationsEnabled()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

      PushNotifications.unsubscribe()
      PushNotifications.switchProject(otherConfig)

      assertEquals(otherConfig.projectId, PushNotifications.getProjectId())
      assertFalse(PushNotifications.isSubscribed())
      assertFalse(preferences.subscriptionRequested)

      clearMocks(apiService, answers = false, recordedCalls = true, childMocks = false, exclusionRules = false)
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)
      runCleanupWork()

      coVerify(timeout = 5_000) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "sub-123") }
      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
    }

  @Test
  fun `subscribe followed by switchProject leaves the device registered only in the new project`() =
    runBlocking {
      givenNotificationsEnabled()
      val registeredProjects = CopyOnWriteArrayList<String>()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } coAnswers {
        val projectId = secondArg<String>()
        registeredProjects += projectId
        TokenResponse(id = if (projectId == config.projectId) "old-sub" else "new-sub")
      }

      PushNotifications.subscribe()
      PushNotifications.switchProject(otherConfig)

      awaitTrue("Device should register in the new project") {
        PushNotifications.subscriptionStatusFlow.value == SubscriptionStatus.Subscribed("new-sub", otherConfig.projectId)
      }
      assertEquals(1, registeredProjects.count { it == otherConfig.projectId })
      // The previous project registers only when its reconciliation took the lock before the switch
      if (config.projectId in registeredProjects) {
        runCleanupWork()
        coVerify(timeout = 5_000) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "old-sub") }
      } else {
        assertTrue(cleanupWork().isEmpty())
      }
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
      "This instance has been released",
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
      PushNotifications.reset()

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
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
    shadowOf(Looper.getMainLooper()).idle()
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
