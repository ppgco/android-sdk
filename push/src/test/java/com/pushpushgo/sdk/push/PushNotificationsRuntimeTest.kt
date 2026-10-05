package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.network.data.TokenResponse
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class PushNotificationsRuntimeTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()

  private lateinit var apiService: ApiService
  private lateinit var preferences: SharedPreferencesHelper
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

    runtime = PushNotificationsRuntime(application, config, PushNotificationsCallbacks())
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
  fun `isSubscribed requires both the subscription request and a registration`() {
    setSubscribed()
    assertTrue(runtime.isSubscribed())

    preferences.subscriberId = null
    assertFalse("Requested but not registered", runtime.isSubscribed())

    preferences.subscriberId = "sub-123"
    preferences.subscriptionRequested = false
    assertFalse("Registered but not requested", runtime.isSubscribed())
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

  private fun setSubscribed() {
    preferences.subscriberId = "sub-123"
    preferences.subscriptionRequested = true
  }

  private companion object {
    const val PUSH_TOKEN_UTILS = "com.pushpushgo.sdk.push.utils.PushTokenUtilsKt"
  }
}
