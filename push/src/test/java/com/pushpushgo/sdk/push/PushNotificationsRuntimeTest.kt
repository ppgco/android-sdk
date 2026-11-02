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
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
  fun `permission removal keeps confirmed state until backend confirmation`() =
    runBlocking {
      setSubscribed()
      val removalStarted = CompletableDeferred<Unit>()
      val finishRemoval = CompletableDeferred<Unit>()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } coAnswers {
        removalStarted.complete(Unit)
        finishRemoval.await()
        Response.success(null)
      }

      val removal = launch(start = CoroutineStart.UNDISPATCHED) { runtime.unregisterKeepingRequest() }
      removalStarted.await()

      assertTrue(preferences.subscriptionRequested)
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), runtime.getSubscriptionStatus())

      finishRemoval.complete(Unit)
      removal.join()

      assertTrue(preferences.subscriptionRequested)
      assertNull(preferences.subscriberId)
      assertEquals(SubscriptionStatus.Unsubscribed, runtime.getSubscriptionStatus())
    }

  @Test
  fun `failed permission removal retains intent and confirmed subscriber for retry`() =
    runBlocking {
      setSubscribed()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

      val failure = runCatching { runtime.unregisterKeepingRequest() }.exceptionOrNull()

      assertTrue(failure is IOException)
      assertTrue(preferences.subscriptionRequested)
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), runtime.getSubscriptionStatus())
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
  fun `subscribe does not store the request when notification permission is disabled`() =
    runBlocking {
      shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

      val failure = runCatching { runtime.subscribe() }.exceptionOrNull()

      assertEquals(IllegalStateException::class.java, failure?.javaClass)
      assertFalse(preferences.subscriptionRequested)
      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
    }

  @Test
  fun `subscribe stores the request before registration failure`() =
    runBlocking {
      grantNotifications()
      val expected = IOException("offline")
      mockkStatic(PUSH_TOKEN_UTILS)
      coEvery { getPlatformPushToken(any()) } returns "push-token"
      coEvery { apiService.registerSubscriber(any(), any(), any()) } coAnswers {
        assertTrue(preferences.subscriptionRequested)
        throw expected
      }

      try {
        val actual = runCatching { runtime.subscribe() }.exceptionOrNull()

        assertSame(expected, actual)
        assertTrue(preferences.subscriptionRequested)
        assertNull(preferences.subscriberId)
      } finally {
        unmockkStatic(PUSH_TOKEN_UTILS)
      }
    }

  @Test
  fun `subscribe is idempotent when a subscriber already exists`() =
    runBlocking {
      grantNotifications()
      preferences.subscriberId = "sub-123"
      preferences.subscriptionRequested = false

      runtime.subscribe()
      runtime.subscribe()

      assertTrue(preferences.subscriptionRequested)
      assertEquals("sub-123", preferences.subscriberId)
      assertTrue(cleanupWork().none { it.state == WorkInfo.State.ENQUEUED })
      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
    }

  @Test
  fun `status represents confirmed backend state independently of intent and permission`() {
    grantNotifications()
    setSubscribed()
    assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), runtime.getSubscriptionStatus())
    assertTrue(runtime.isSubscribed())

    preferences.subscriberId = null
    assertEquals(SubscriptionStatus.Unsubscribed, runtime.getSubscriptionStatus())
    assertFalse(runtime.isSubscribed())

    // Revoked while still registered - before the checker unregisters the device, e.g. offline
    preferences.subscriberId = "sub-123"
    shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), runtime.getSubscriptionStatus())
    assertTrue(runtime.isSubscribed())

    preferences.subscriptionRequested = false
    assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), runtime.getSubscriptionStatus())
    assertTrue(runtime.isSubscribed())
  }

  @Test
  fun `subscription request getter is independent of confirmed backend state`() {
    preferences.subscriptionRequested = true
    preferences.subscriberId = null

    assertTrue(runtime.isSubscriptionRequested())
    assertFalse(runtime.isSubscribed())

    preferences.subscriptionRequested = false
    preferences.subscriberId = "sub-123"

    assertFalse(runtime.isSubscriptionRequested())
    assertTrue(runtime.isSubscribed())
  }

  @Test
  fun `status getter does not publish persisted changes`() {
    preferences.subscriberId = "sub-123"

    assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), runtime.getSubscriptionStatus())
    assertEquals(SubscriptionStatus.Unsubscribed, callbacks.subscriptionStatusFlow.value)
  }

  @Test
  fun `status is published when the runtime starts`() {
    grantNotifications()
    setSubscribed()
    val startedCallbacks = PushNotificationsCallbacks()

    val startedRuntime = PushNotificationsRuntime(application, config, startedCallbacks)

    try {
      startedRuntime.start()
      assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), startedCallbacks.subscriptionStatusFlow.value)
    } finally {
      runBlocking { startedRuntime.release() }
    }
  }

  @Test
  fun `unsubscribe without network retains the subscriber and schedules background removal`() =
    runBlocking {
      grantNotifications()
      setSubscribed()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } coAnswers {
        assertFalse(preferences.subscriptionRequested)
        throw IOException("offline")
      }

      runtime.unsubscribe()

      assertFalse(preferences.subscriptionRequested)
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), callbacks.subscriptionStatusFlow.value)
      val cleanup = cleanupWork().single()
      assertEquals(WorkInfo.State.ENQUEUED, cleanup.state)
    }

  @Test
  fun `unsubscribe online removes the subscriber right away`() =
    runBlocking {
      setSubscribed()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } coAnswers {
        assertFalse(preferences.subscriptionRequested)
        Response.success(null)
      }

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
      assertEquals("sub-123", preferences.subscriberId)
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
      assertEquals(SubscriptionStatus.Unsubscribed, runtime.getSubscriptionStatus())
    }

  @Test
  fun `subscribing again restores intent without registering the retained subscriber again`() =
    runBlocking {
      grantNotifications()
      setSubscribed()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")
      runtime.unsubscribe()

      runtime.subscribe()

      assertTrue(preferences.subscriptionRequested)
      assertEquals("sub-123", preferences.subscriberId)
      assertTrue(uniqueWork(UploadManager.SYNC_TOKEN_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
      assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
    }

  @Test
  fun `runtime restart retries a failed requested subscription`() =
    runBlocking {
      grantNotifications()
      mockkStatic(PUSH_TOKEN_UTILS)
      coEvery { getPlatformPushToken(any()) } returns "push-token"
      coEvery { apiService.registerSubscriber(any(), any(), any()) } throws IOException("offline")

      try {
        runCatching { runtime.subscribe() }
        assertTrue(preferences.subscriptionRequested)
        assertNull(preferences.subscriberId)

        runtime.release()
        coEvery { apiService.registerSubscriber(any(), any(), any()) } returns TokenResponse(id = "sub-after-restart")
        runtime = PushNotificationsRuntime(application, config, callbacks)
        runtime.start()

        withTimeout(5_000) {
          while (preferences.subscriberId == null) delay(10)
        }

        assertTrue(preferences.subscriptionRequested)
        assertEquals("sub-after-restart", preferences.subscriberId)
        coVerify(exactly = 2) { apiService.registerSubscriber(any(), config.projectId, any()) }
      } finally {
        unmockkStatic(PUSH_TOKEN_UTILS)
      }
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
        val first = launch(start = CoroutineStart.UNDISPATCHED) { runtime.subscribe() }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { runtime.subscribe() }
        finishRegistration.complete(Unit)
        joinAll(first, second)
      } finally {
        unmockkStatic(PUSH_TOKEN_UTILS)
      }

      coVerify(exactly = 1) { apiService.registerSubscriber(any(), any(), any()) }
      assertTrue(runtime.isSubscribed())
    }

  private fun grantNotifications() {
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun cleanupWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosByTag(UploadManager.PROJECT_CLEANUP_TAG).get()

  private fun uniqueWork(name: String): List<WorkInfo> = WorkManager.getInstance(application).getWorkInfosForUniqueWork(name).get()

  private fun setSubscribed() {
    preferences.subscriberId = "sub-123"
    preferences.subscriptionRequested = true
  }

  private companion object {
    const val PUSH_TOKEN_UTILS = "com.pushpushgo.sdk.push.utils.PushTokenUtilsKt"
  }
}
