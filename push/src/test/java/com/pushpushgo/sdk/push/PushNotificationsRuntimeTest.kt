package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.FakeApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PushNotificationsRuntimeTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()
  private val dispatcher = StandardTestDispatcher()

  private lateinit var apiService: FakeApiService
  private lateinit var preferences: SharedPreferencesHelper
  private lateinit var callbacks: PushNotificationsCallbacks
  private lateinit var runtime: PushNotificationsRuntime

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    LiveActivityPersistence(application).clearAll()

    apiService = FakeApiService()

    callbacks = PushNotificationsCallbacks()
    runtime = createRuntime()
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()

    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  @Test
  fun `reconcile on a released runtime does nothing`() =
    runTest(dispatcher) {
      grantNotifications()
      preferences.subscriberId = "sub-123"
      preferences.subscriptionRequested = false
      runtime.release()

      runtime.reconcile()

      assertTrue(apiService.unregisteredSubscriberIds.isEmpty())
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals(SubscriptionStatus.Unsubscribed, callbacks.subscriptionStatusFlow.value)
    }

  @Test
  fun `runtime restart retries a failed requested subscription`() =
    runTest(dispatcher) {
      grantNotifications()
      apiService.exception = IOException("offline")

      runtime.subscribe()
      advanceUntilIdle()
      runtime.release()
      assertTrue(preferences.subscriptionRequested)
      assertNull(preferences.subscriberId)

      apiService.exception = null
      runtime = createRuntime()
      runtime.start()
      advanceUntilIdle()

      assertTrue(preferences.subscriptionRequested)
      assertEquals(FakeApiService.SUBSCRIBER_ID, preferences.subscriberId)
      assertEquals(listOf("push-token"), apiService.registeredTokens)
    }

  @Test
  fun `beacon is discarded when the subscription is not requested`() {
    preferences.subscriberId = FakeApiService.SUBSCRIBER_ID
    preferences.subscriptionRequested = false

    runtime.sendBeacon(beacon())

    assertTrue(beaconWork().isEmpty())
  }

  @Test
  fun `beacon is discarded when there is no subscriber`() {
    preferences.subscriptionRequested = true

    runtime.sendBeacon(beacon())

    assertTrue(beaconWork().isEmpty())
  }

  @Test
  fun `beacon is enqueued when subscribed`() {
    preferences.subscriberId = FakeApiService.SUBSCRIBER_ID
    preferences.subscriptionRequested = true

    runtime.sendBeacon(beacon())

    assertEquals(listOf(WorkInfo.State.ENQUEUED), beaconWork().map { it.state })
  }

  private fun createRuntime() =
    PushNotificationsRuntime(
      application = application,
      config = config,
      callbacks = callbacks,
      apiServiceFactory = { apiService },
      pushTokenProvider = { "push-token" },
      dispatcher = dispatcher,
    )

  private fun beacon() = BeaconBuilder().set("key", "value").build()

  private fun beaconWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosForUniqueWork(UploadManager.beaconWorkName(config.projectId)).get()

  private fun grantNotifications() {
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }
}
