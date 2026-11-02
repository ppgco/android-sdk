package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.FakeApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.utils.PushTokenProvider
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SubscriptionTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()
  private val subscribed = SubscriptionStatus.Subscribed(FakeApiService.SUBSCRIBER_ID, config.projectId)

  private val apiService = FakeApiService()
  private val callbacks = PushNotificationsCallbacks()
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    LiveActivityPersistence(application).clearAll()
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  @Test
  fun `subscribe throws when notifications are disabled without changing the request`() =
    runTest {
      val runtime = createRuntime()
      shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

      assertFailsWith<IllegalStateException> { runtime.subscribe() }
      advanceUntilIdle()

      assertFalse(preferences.subscriptionRequested)
      assertTrue(apiService.registeredTokens.isEmpty())
    }

  @Test
  fun `subscribe stores the request and registers the device`() =
    runTest {
      val runtime = createRuntime()
      grantNotifications()

      runtime.subscribe()
      advanceUntilIdle()

      assertTrue(preferences.subscriptionRequested)
      assertEquals(subscribed, callbacks.subscriptionStatusFlow.value)
      assertEquals(listOf("push-token"), apiService.registeredTokens)
      assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
    }

  @Test
  fun `subscribe again after a failed removal keeps the retained subscriber`() =
    runTest {
      val runtime = createRuntime()
      grantNotifications()
      preferences.subscriberId = FakeApiService.SUBSCRIBER_ID
      preferences.subscriptionRequested = false
      runtime.uploadManager.schedulePeriodicTokenSync(FakeApiService.SUBSCRIBER_ID)

      runtime.subscribe()
      advanceUntilIdle()

      assertTrue(preferences.subscriptionRequested)
      assertEquals(FakeApiService.SUBSCRIBER_ID, preferences.subscriberId)
      assertTrue(uniqueWork(UploadManager.PERIODIC_TOKEN_SYNC_WORK_NAME).any { it.state == WorkInfo.State.ENQUEUED })
      assertTrue(apiService.registeredTokens.isEmpty())
      assertTrue(apiService.unregisteredSubscriberIds.isEmpty())
    }

  @Test
  fun `unsubscribe stores the choice and removes the subscriber`() =
    runTest {
      val runtime = createRuntime()
      grantNotifications()
      setSubscribed()

      runtime.unsubscribe()
      advanceUntilIdle()

      assertFalse(preferences.subscriptionRequested)
      assertEquals(listOf(FakeApiService.SUBSCRIBER_ID), apiService.unregisteredSubscriberIds)
      assertNull(preferences.subscriberId)
      assertEquals(SubscriptionStatus.Unsubscribed, callbacks.subscriptionStatusFlow.value)
    }

  @Test
  fun `revoked permission removes the subscriber but keeps the request`() =
    runTest {
      val runtime = createRuntime()
      setSubscribed()
      shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

      runtime.reconcile()

      assertEquals(listOf(FakeApiService.SUBSCRIBER_ID), apiService.unregisteredSubscriberIds)
      assertNull(preferences.subscriberId)
      assertTrue(preferences.subscriptionRequested)
    }

  @Test
  fun `permission granted back registers the device when an activity resumes`() =
    runTest {
      val runtime = createRuntime()
      preferences.subscriptionRequested = true
      shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
      runtime.start()
      advanceUntilIdle()

      grantNotifications()
      Robolectric.buildActivity(Activity::class.java).setup()
      advanceUntilIdle()

      assertEquals(listOf("push-token"), apiService.registeredTokens)
      assertEquals(subscribed, callbacks.subscriptionStatusFlow.value)
    }

  @Test
  fun `transient failure enqueues the reconcile work`() =
    runTest {
      val runtime = createRuntime()
      grantNotifications()
      preferences.subscriptionRequested = true
      apiService.exception = IOException("offline")

      runtime.requestReconcile()
      advanceUntilIdle()

      assertTrue(reconcileWork().any { it.state == WorkInfo.State.ENQUEUED })
      assertNull(preferences.subscriberId)
    }

  @Test
  fun `unsubscribe offline keeps the subscriber and enqueues the reconcile work`() =
    runTest {
      val runtime = createRuntime()
      grantNotifications()
      setSubscribed()
      apiService.exception = IOException("offline")

      runtime.unsubscribe()
      advanceUntilIdle()

      assertFalse(preferences.subscriptionRequested)
      assertTrue(reconcileWork().any { it.state == WorkInfo.State.ENQUEUED })
      assertEquals(FakeApiService.SUBSCRIBER_ID, preferences.subscriberId)
      assertEquals(subscribed, callbacks.subscriptionStatusFlow.value)
      assertTrue(cleanupWork().isEmpty())
    }

  @Test
  fun `concurrent reconciles register the device once`() =
    runTest {
      val runtime = createRuntime(slowPushTokenProvider())
      grantNotifications()
      preferences.subscriptionRequested = true

      runtime.requestReconcile()
      runtime.requestReconcile()
      advanceUntilIdle()

      assertEquals(listOf("push-token"), apiService.registeredTokens)
      assertTrue(runtime.isSubscribed())
    }

  @Test
  fun `unsubscribe before the registration finishes ends without a subscriber`() =
    runTest {
      val runtime = createRuntime(slowPushTokenProvider())
      grantNotifications()

      runtime.subscribe()
      runCurrent()
      runtime.unsubscribe()
      advanceUntilIdle()

      assertEquals(listOf("push-token"), apiService.registeredTokens)
      assertEquals(listOf(FakeApiService.SUBSCRIBER_ID), apiService.unregisteredSubscriberIds)
      assertNull(preferences.subscriberId)
      assertEquals(SubscriptionStatus.Unsubscribed, callbacks.subscriptionStatusFlow.value)
      assertFalse(preferences.subscriptionRequested)
    }

  private fun TestScope.createRuntime(pushTokenProvider: PushTokenProvider = PushTokenProvider { "push-token" }) =
    PushNotificationsRuntime(
      application = application,
      config = config,
      callbacks = callbacks,
      apiServiceFactory = { apiService },
      pushTokenProvider = pushTokenProvider,
      dispatcher = StandardTestDispatcher(testScheduler),
    )

  private fun slowPushTokenProvider() =
    PushTokenProvider {
      delay(1_000)
      "push-token"
    }

  private fun grantNotifications() {
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun setSubscribed() {
    preferences.subscriberId = FakeApiService.SUBSCRIBER_ID
    preferences.subscriptionRequested = true
  }

  private fun cleanupWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosByTag(UploadManager.PROJECT_CLEANUP_TAG).get()

  private fun reconcileWork(): List<WorkInfo> = uniqueWork(UploadManager.SUBSCRIPTION_RECONCILE_WORK_NAME)

  private fun uniqueWork(name: String): List<WorkInfo> = WorkManager.getInstance(application).getWorkInfosForUniqueWork(name).get()
}
