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
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.work.UploadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.joinAll
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
    coEvery { apiService.registerSubscriber(any(), any(), any()) } returns TokenResponse(id = "sub-123")
    coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)
    mockkStatic(PUSH_TOKEN_UTILS, LOG_UTILS)
    coEvery { getPlatformPushToken(any()) } returns "push-token"

    callbacks = PushNotificationsCallbacks()
    runtime = PushNotificationsRuntime(application, config, callbacks)
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    // Some tests release the runtime themselves
    runBlocking { runCatching { runtime.release() } }

    WorkManagerTestInitHelper.closeWorkDatabase()
    unmockkObject(ApiService.Companion)
    unmockkStatic(PUSH_TOKEN_UTILS, LOG_UTILS)
  }

  @Test
  fun `subscribe throws when notifications are disabled without changing the request`() {
    shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    val failure = assertThrows(IllegalStateException::class.java) { runtime.subscribe() }

    assertEquals("Cannot subscribe because notifications are disabled", failure.message)
    assertFalse(preferences.subscriptionRequested)
    coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
  }

  @Test
  fun `subscribe stores the request and registers the device in the background`() {
    grantNotifications()

    runtime.subscribe()

    assertTrue(preferences.subscriptionRequested)
    awaitTrue("Registration should be published") {
      callbacks.subscriptionStatusFlow.value == SubscriptionStatus.Subscribed("sub-123", config.projectId)
    }
    coVerify(exactly = 1) { apiService.registerSubscriber(config.apiKey, config.projectId, any()) }
    assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
  }

  @Test
  fun `subscribe again after a failed removal keeps the retained subscriber`() =
    runBlocking {
      grantNotifications()
      preferences.subscriberId = "sub-123"
      preferences.subscriptionRequested = false
      runtime.uploadManager.schedulePeriodicTokenSync("sub-123")

      runtime.subscribe()
      runtime.reconcile()

      assertTrue(preferences.subscriptionRequested)
      assertEquals("sub-123", preferences.subscriberId)
      assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
    }

  @Test
  fun `unsubscribe stores the choice and removes the subscriber in the background`() {
    grantNotifications()
    setSubscribed()

    runtime.unsubscribe()

    assertFalse(preferences.subscriptionRequested)
    coVerify(timeout = 5_000) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "sub-123") }
    awaitTrue("Subscriber should be removed") { preferences.subscriberId == null }
  }

  @Test
  fun `reconcile registers a requested subscription`() =
    runBlocking {
      grantNotifications()
      preferences.subscriptionRequested = true

      runtime.reconcile()

      coVerify(exactly = 1) { apiService.registerSubscriber(config.apiKey, config.projectId, any()) }
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), callbacks.subscriptionStatusFlow.value)
      assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
    }

  @Test
  fun `reconcile removes a subscriber that is no longer requested`() =
    runBlocking {
      grantNotifications()
      preferences.subscriberId = "sub-123"
      preferences.subscriptionRequested = false
      runtime.uploadManager.schedulePeriodicTokenSync("sub-123")

      runtime.reconcile()

      coVerify(exactly = 1) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "sub-123") }
      assertNull(preferences.subscriberId)
      assertFalse(preferences.subscriptionRequested)
      assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).all { it.state == WorkInfo.State.CANCELLED })
    }

  @Test
  fun `reconcile removes the subscriber but keeps the request when notifications are disabled`() =
    runBlocking {
      setSubscribed()
      shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

      runtime.reconcile()

      coVerify(exactly = 1) { apiService.unregisterSubscriber(any(), config.projectId, "sub-123") }
      assertNull(preferences.subscriberId)
      assertTrue("Subscription request should survive", preferences.subscriptionRequested)
      assertFalse(runtime.isSubscribed())
    }

  @Test
  fun `reconcile does nothing while the registration matches the request and permission`() =
    runBlocking {
      grantNotifications()
      setSubscribed()
      runtime.reconcile()

      preferences.subscriberId = null
      preferences.subscriptionRequested = false
      runtime.reconcile()

      shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
      preferences.subscriptionRequested = true
      runtime.reconcile()

      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
    }

  @Test
  fun `removal keeps the subscriber until the backend confirms it`() =
    runBlocking {
      setSubscribed()
      preferences.subscriptionRequested = false
      val removalStarted = CompletableDeferred<Unit>()
      val finishRemoval = CompletableDeferred<Unit>()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } coAnswers {
        removalStarted.complete(Unit)
        finishRemoval.await()
        Response.success(null)
      }

      val removal = launch(start = CoroutineStart.UNDISPATCHED) { runtime.reconcile() }
      removalStarted.await()

      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), runtime.getSubscriptionStatus())

      finishRemoval.complete(Unit)
      removal.join()

      assertNull(preferences.subscriberId)
      assertEquals(SubscriptionStatus.Unsubscribed, runtime.getSubscriptionStatus())
    }

  @Test
  fun `failed removal keeps the subscriber, its status and token sync`() =
    runBlocking {
      grantNotifications()
      preferences.subscriberId = "sub-123"
      preferences.subscriptionRequested = false
      runtime.uploadManager.schedulePeriodicTokenSync("sub-123")
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

      val failure = runCatching { runtime.reconcile() }.exceptionOrNull()

      assertTrue("Expected IOException, got: $failure", failure is IOException)
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), callbacks.subscriptionStatusFlow.value)
      assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
    }

  @Test
  fun `transient failure is reported and retried by the reconcile work`() {
    grantNotifications()
    preferences.subscriptionRequested = true
    val error = IOException("offline")
    coEvery { apiService.registerSubscriber(any(), any(), any()) } throws error

    runtime.requestReconcile()

    awaitTrue("Reconcile work should be enqueued") {
      reconcileWork().any { it.state == WorkInfo.State.ENQUEUED }
    }
    verify(exactly = 1) { logError("Subscription reconciliation failed", error) }
    assertNull(preferences.subscriberId)
  }

  @Test
  fun `rejected request is only reported`() {
    grantNotifications()
    preferences.subscriptionRequested = true
    val error = PushPushException("Bad request", 400)
    coEvery { apiService.registerSubscriber(any(), any(), any()) } throws error

    runtime.requestReconcile()

    verify(timeout = 5_000) { logError("Subscription reconciliation failed", error) }
    assertTrue("Retrying a rejected request does not help", reconcileWork().isEmpty())
  }

  @Test
  fun `unsubscribe offline keeps the subscriber and retries the removal with the reconcile work`() {
    grantNotifications()
    setSubscribed()
    coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

    runtime.unsubscribe()

    assertFalse(preferences.subscriptionRequested)
    awaitTrue("Reconcile work should be enqueued") {
      reconcileWork().any { it.state == WorkInfo.State.ENQUEUED }
    }
    assertEquals("sub-123", preferences.subscriberId)
    assertEquals(SubscriptionStatus.Subscribed("sub-123", config.projectId), callbacks.subscriptionStatusFlow.value)
    assertTrue(cleanupWork().isEmpty())
  }

  @Test
  fun `concurrent reconciles register the device once`() =
    runBlocking {
      grantNotifications()
      preferences.subscriptionRequested = true
      val finishRegistration = CompletableDeferred<Unit>()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } coAnswers {
        finishRegistration.await()
        TokenResponse(id = "sub-123")
      }

      val first = launch(start = CoroutineStart.UNDISPATCHED) { runtime.reconcile() }
      val second = launch(start = CoroutineStart.UNDISPATCHED) { runtime.reconcile() }
      finishRegistration.complete(Unit)
      joinAll(first, second)

      coVerify(exactly = 1) { apiService.registerSubscriber(any(), any(), any()) }
      assertTrue(runtime.isSubscribed())
    }

  @Test
  fun `unsubscribe before the registration finishes ends without a subscriber`() =
    runBlocking {
      grantNotifications()
      val registrationStarted = CompletableDeferred<Unit>()
      val finishRegistration = CompletableDeferred<Unit>()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } coAnswers {
        registrationStarted.complete(Unit)
        finishRegistration.await()
        TokenResponse(id = "sub-123")
      }

      runtime.subscribe()
      registrationStarted.await()
      runtime.unsubscribe()
      finishRegistration.complete(Unit)

      coVerify(timeout = 5_000) { apiService.unregisterSubscriber(config.apiKey, config.projectId, "sub-123") }
      awaitTrue("Unsubscribed status should be published") {
        callbacks.subscriptionStatusFlow.value == SubscriptionStatus.Unsubscribed && preferences.subscriberId == null
      }
      assertFalse(preferences.subscriptionRequested)
      coVerify(exactly = 1) { apiService.registerSubscriber(any(), any(), any()) }
    }

  @Test
  fun `reconcile on a released runtime does nothing`() =
    runBlocking {
      grantNotifications()
      preferences.subscriberId = "sub-123"
      preferences.subscriptionRequested = false
      runtime.release()

      runtime.reconcile()

      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Unsubscribed, callbacks.subscriptionStatusFlow.value)
    }

  @Test
  fun `runtime restart retries a failed requested subscription`() =
    runBlocking {
      grantNotifications()
      coEvery { apiService.registerSubscriber(any(), any(), any()) } throws IOException("offline")

      runtime.subscribe()
      coVerify(timeout = 5_000) { apiService.registerSubscriber(any(), config.projectId, any()) }
      runtime.release()
      assertTrue(preferences.subscriptionRequested)
      assertNull(preferences.subscriberId)

      coEvery { apiService.registerSubscriber(any(), any(), any()) } returns TokenResponse(id = "sub-after-restart")
      runtime = PushNotificationsRuntime(application, config, callbacks)
      runtime.start()

      awaitTrue("Restarted runtime should register the device") { preferences.subscriberId != null }
      assertTrue(preferences.subscriptionRequested)
      assertEquals("sub-after-restart", preferences.subscriberId)
      coVerify(exactly = 2) { apiService.registerSubscriber(any(), config.projectId, any()) }
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

    // Revoked while still registered - before the removal is confirmed, e.g. offline
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

  private fun grantNotifications() {
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun awaitTrue(
    message: String,
    condition: () -> Boolean,
  ) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10)
    }
    assertTrue(message, condition())
  }

  private fun cleanupWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosByTag(UploadManager.PROJECT_CLEANUP_TAG).get()

  private fun reconcileWork(): List<WorkInfo> = uniqueWork(UploadManager.SUBSCRIPTION_RECONCILE_WORK_NAME)

  private fun uniqueWork(name: String): List<WorkInfo> = WorkManager.getInstance(application).getWorkInfosForUniqueWork(name).get()

  private fun setSubscribed() {
    preferences.subscriberId = "sub-123"
    preferences.subscriptionRequested = true
  }

  private companion object {
    const val PUSH_TOKEN_UTILS = "com.pushpushgo.sdk.push.utils.PushTokenUtilsKt"
    const val LOG_UTILS = "com.pushpushgo.sdk.push.utils.LogUtilsKt"
  }
}
