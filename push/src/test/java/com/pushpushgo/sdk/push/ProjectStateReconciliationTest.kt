package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.FakeApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProjectStateReconciliationTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()
  private val previousProject = otherProjectTestConfig()

  private val apiService = FakeApiService()
  private lateinit var preferences: SharedPreferencesHelper
  private lateinit var liveActivityPersistence: LiveActivityPersistence

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    liveActivityPersistence = LiveActivityPersistence(application)
    liveActivityPersistence.clearAll()
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  @Test
  fun `state of another project is released and its cleanup is enqueued`() =
    runTest {
      preferences.projectOwner = previousProject
      preferences.subscriberId = "old-sub"
      preferences.subscriptionRequested = true
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      liveActivityPersistence.addActiveId("live-1")
      liveActivityPersistence.setNotificationId("live-1", LIVE_ACTIVITY_NOTIFICATION_ID)
      postNotification(LIVE_ACTIVITY_NOTIFICATION_ID)

      val runtime = createRuntime()

      assertNull(preferences.subscriberId)
      assertTrue(preferences.subscriptionRequested)
      assertFalse(runtime.isSubscribed())
      assertEquals(config.projectId, preferences.projectOwner?.projectId)
      assertTrue(preferences.getLiveActivitySubscriptions().isEmpty())
      assertTrue(liveActivityPersistence.getActiveIds().isEmpty())
      assertTrue(notificationManager().activeNotifications.none { it.id == LIVE_ACTIVITY_NOTIFICATION_ID })
      assertTrue(uniqueWork(UploadManager.SYNC_TOKEN_WORK_NAME).none { it.state == WorkInfo.State.ENQUEUED })
      assertEquals(WorkInfo.State.ENQUEUED, cleanupWork().single().state)
    }

  @Test
  fun `registered device without a stored subscription request stays subscribed after the update`() =
    runTest {
      preferences.subscriberId = "sub-1"

      val runtime = createRuntime()

      assertTrue(preferences.subscriptionRequested)
      assertEquals(SubscriptionStatus.Subscribed("sub-1", config.projectId), runtime.getSubscriptionStatus())
      assertEquals(config.projectId, preferences.projectOwner?.projectId)
      assertTrue(cleanupWork().isEmpty())
    }

  @Test
  fun `stored opt-out of a registered device stays an opt-out after the update`() =
    runTest {
      preferences.subscriberId = "sub-1"
      preferences.subscriptionRequested = false

      val runtime = createRuntime()

      assertFalse(preferences.subscriptionRequested)
      assertEquals(SubscriptionStatus.Subscribed("sub-1", config.projectId), runtime.getSubscriptionStatus())
    }

  @Test
  fun `device unsubscribed by SDK 3 stays unsubscribed after the update`() =
    runTest {
      PreferenceManager
        .getDefaultSharedPreferences(application)
        .edit()
        .putBoolean("_PushPushGoSDK_is_subscribed_", false)
        .putString("_PushPushGoSDK_sub_id_", "")
        .commit()

      val runtime = createRuntime()
      advanceUntilIdle()

      assertFalse(preferences.subscriptionRequested)
      assertNull(preferences.subscriberId)
      assertEquals(SubscriptionStatus.Unsubscribed, runtime.getSubscriptionStatus())
      assertTrue(apiService.unregisteredSubscriberIds.isEmpty())
      assertTrue(cleanupWork().isEmpty())
    }

  @Test
  fun `rotated api key keeps the subscription of the same project`() =
    runTest {
      preferences.projectOwner = Config.create(config.projectId, "00000000-0000-0000-0000-000000000003")
      preferences.subscriberId = "sub-1"
      preferences.subscriptionRequested = true

      val runtime = createRuntime()

      assertEquals("sub-1", preferences.subscriberId)
      assertTrue(runtime.isSubscribed())
      assertEquals(config.apiKey, preferences.projectOwner?.apiKey)
      assertTrue(cleanupWork().isEmpty())
    }

  private fun TestScope.createRuntime() =
    PushNotificationsRuntime(
      application = application,
      config = config,
      callbacks = PushNotificationsCallbacks(),
      apiServiceFactory = { apiService },
      pushTokenProvider = { "push-token" },
      dispatcher = StandardTestDispatcher(testScheduler),
    )

  private fun notificationManager(): NotificationManager = requireNotNull(application.getSystemService())

  private fun postNotification(id: Int) {
    notificationManager().notify(
      id,
      NotificationCompat
        .Builder(application, "live-activities")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .build(),
    )
  }

  private fun uniqueWork(name: String): List<WorkInfo> = WorkManager.getInstance(application).getWorkInfosForUniqueWork(name).get()

  private fun cleanupWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosByTag(UploadManager.PROJECT_CLEANUP_TAG).get()

  private companion object {
    const val LIVE_ACTIVITY_NOTIFICATION_ID = 4242
  }
}
