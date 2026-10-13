package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.network.data.TokenResponse
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import retrofit2.Response
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class PushNotificationsRuntimeTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()

  private lateinit var apiService: ApiService
  private lateinit var preferences: SharedPreferencesHelper
  private lateinit var callbacks: PushNotificationsCallbacks
  private lateinit var runtime: PushNotificationsRuntime

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

    callbacks = PushNotificationsCallbacks()
    runtime = PushNotificationsRuntime(application, config, callbacks)
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    // Some tests deinitialize the runtime themselves
    runBlocking { runCatching { runtime.deinitialize() } }

    WorkManagerTestInitHelper.closeWorkDatabase()
    unmockkConstructor(NotificationStatusChecker::class)
    unmockkObject(ApiService.Companion)
  }

  @Test
  fun `losing notification permission unregisters the device but keeps the subscription request`() =
    runBlocking {
      setSubscribed()

      runtime.unregisterKeepingRequest()

      coVerify(exactly = 1) { apiService.unregisterSubscriber(any(), config.projectId, "sub-123") }
      assertNull(preferences.subscriberId)
      assertTrue("Subscription request should survive", preferences.subscriptionRequested)
      assertFalse(runtime.isSubscribed())
    }

  @Test
  fun `unsubscribe cancels the subscription request`() =
    runBlocking {
      setSubscribed()

      runtime.unsubscribe()

      coVerify(exactly = 1) { apiService.unregisterSubscriber(any(), config.projectId, "sub-123") }
      assertNull(preferences.subscriberId)
      assertFalse(preferences.subscriptionRequested)
      assertFalse(runtime.isSubscribed())
    }

  @Test
  fun `status tells apart why the device does not receive notifications`() {
    grantNotifications()
    setSubscribed()
    assertStatus(SubscriptionStatus.SUBSCRIBED)
    assertTrue(runtime.isSubscribed())

    preferences.subscriberId = null
    assertStatus(SubscriptionStatus.PENDING)

    // Revoked while still registered - before the checker unregisters the device, e.g. offline
    preferences.subscriberId = "sub-123"
    shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    assertStatus(SubscriptionStatus.NOTIFICATIONS_DISABLED)

    preferences.subscriptionRequested = false
    assertStatus(SubscriptionStatus.UNSUBSCRIBED)
  }

  @Test
  fun `status is published when the runtime starts`() {
    grantNotifications()
    setSubscribed()
    val startedCallbacks = PushNotificationsCallbacks()

    val startedRuntime = PushNotificationsRuntime(application, config, startedCallbacks)

    try {
      assertEquals(SubscriptionStatus.SUBSCRIBED, startedCallbacks.subscriptionStatusFlow.value)
    } finally {
      runBlocking { startedRuntime.release() }
    }
  }

  @Test
  fun `unsubscribe without network stores the choice and removes the subscriber in the background`() =
    runBlocking {
      grantNotifications()
      setSubscribed()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

      runtime.unsubscribe()

      assertFalse(preferences.subscriptionRequested)
      assertNull(preferences.subscriberId)
      assertEquals(SubscriptionStatus.UNSUBSCRIBED, callbacks.subscriptionStatusFlow.value)
      val cleanup = cleanupWork().single()
      assertEquals(WorkInfo.State.ENQUEUED, cleanup.state)

      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)
      requireNotNull(WorkManagerTestInitHelper.getTestDriver(application)).setAllConstraintsMet(cleanup.id)

      // Once in unsubscribe, once more by the cleanup work with the same project's credentials
      coVerify(exactly = 2, timeout = 5_000) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "sub-123") }
    }

  @Test
  fun `unsubscribe online removes the subscriber right away`() =
    runBlocking {
      setSubscribed()

      runtime.unsubscribe()

      coVerify(exactly = 1) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "sub-123") }
      assertNull(preferences.subscriberId)
      assertTrue(cleanupWork().isEmpty())
    }

  @Test
  fun `unsubscribe rejected by the API still stores the choice`() =
    runBlocking {
      setSubscribed()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws PushPushException("Unauthorized", 401)

      runtime.unsubscribe()

      assertFalse(preferences.subscriptionRequested)
      assertNull(preferences.subscriberId)
      assertTrue("Retrying a rejected request does not help", cleanupWork().isEmpty())
    }

  @Test
  fun `unsubscribe while not registered cancels the request without calling the API`() =
    runBlocking {
      // e.g. the permission is revoked and the device was already unregistered
      preferences.subscriptionRequested = true

      runtime.unsubscribe()

      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
      assertFalse(preferences.subscriptionRequested)
      assertEquals(SubscriptionStatus.UNSUBSCRIBED, runtime.getSubscriptionStatus())
    }

  @Test
  fun `subscribing again cancels the background removal of the same subscriber`() =
    runBlocking {
      grantNotifications()
      setSubscribed()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")
      runtime.unsubscribe()

      // The API returns the subscriber that is still active for this push token
      mockkStatic(PUSH_TOKEN_UTILS)
      coEvery { getPlatformPushToken(any()) } returns "push-token"
      coEvery { apiService.registerSubscriber(any(), any(), any()) } returns TokenResponse(id = "sub-123")
      try {
        runtime.subscribe()
      } finally {
        unmockkStatic(PUSH_TOKEN_UTILS)
      }

      assertEquals(WorkInfo.State.CANCELLED, cleanupWork().single().state)
      assertEquals(SubscriptionStatus.SUBSCRIBED, callbacks.subscriptionStatusFlow.value)
    }

  @Test
  fun `deinitialize after losing notification permission does not call the API`() =
    runBlocking {
      preferences.subscriberId = null
      preferences.subscriptionRequested = true

      runtime.deinitialize()

      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
      assertFalse(preferences.subscriptionRequested)
    }

  @Test
  fun `concurrent registration attempts register the device once`() =
    runBlocking {
      preferences.subscriptionRequested = true
      shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
      mockkStatic(PUSH_TOKEN_UTILS)
      coEvery { getPlatformPushToken(any()) } returns "push-token"
      val finishRegistration = CompletableDeferred<Unit>()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } coAnswers {
        finishRegistration.await()
        TokenResponse(id = "sub-123")
      }

      try {
        // e.g. switchProject and the notification status checker of the new runtime
        val first = launch(start = CoroutineStart.UNDISPATCHED) { runtime.subscribeIfRequested() }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { runtime.subscribeIfRequested() }
        finishRegistration.complete(Unit)
        joinAll(first, second)
      } finally {
        unmockkStatic(PUSH_TOKEN_UTILS)
      }

      coVerify(exactly = 1) { apiService.registerSubscriber(any(), any(), any()) }
      assertTrue(runtime.isSubscribed())
    }

  @Test
  fun `subscription waits until notifications are enabled and is not an error`() =
    runBlocking {
      preferences.subscriptionRequested = true
      val errors = mutableListOf<Throwable>()
      PushNotifications.setErrorCallback { errors += it }

      try {
        runtime.subscribeIfRequested()
      } finally {
        PushNotifications.setErrorCallback(null)
      }

      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
      assertTrue("Subscription request should survive", preferences.subscriptionRequested)
      assertTrue("Disabled notifications are not an error, got: $errors", errors.isEmpty())
    }

  @Test
  fun `user who did not subscribe is not subscribed`() =
    runBlocking {
      shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

      runtime.subscribeIfRequested()

      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
    }

  private fun grantNotifications() {
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun assertStatus(expected: SubscriptionStatus) {
    assertEquals(expected, runtime.getSubscriptionStatus())
    assertEquals("Flow should follow the status", expected, callbacks.subscriptionStatusFlow.value)
    assertEquals(expected == SubscriptionStatus.SUBSCRIBED, runtime.isSubscribed())
  }

  private fun cleanupWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosByTag(UploadManager.PROJECT_CLEANUP_TAG).get()

  private fun setSubscribed() {
    preferences.subscriberId = "sub-123"
    preferences.subscriptionRequested = true
  }

  private companion object {
    const val PUSH_TOKEN_UTILS = "com.pushpushgo.sdk.push.utils.PushTokenUtilsKt"
  }
}
