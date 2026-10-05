package com.pushpushgo.sdk.push

import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.work.UploadManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class ProjectStateReconciliationTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()
  private val previousProject = otherProjectTestConfig()

  private lateinit var apiService: ApiService
  private lateinit var preferences: SharedPreferencesHelper
  private lateinit var liveActivityPersistence: LiveActivityPersistence
  private var runtime: PushNotificationsRuntime? = null

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    liveActivityPersistence = LiveActivityPersistence(application)
    liveActivityPersistence.clearAll()

    apiService = mockk(relaxed = true)
    mockkObject(ApiService.Companion)
    mockkConstructor(NotificationStatusChecker::class)
    every { anyConstructed<NotificationStatusChecker>().start() } just Runs
    every { ApiService.fromConfig(any()) } returns apiService
    coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)
    coEvery { apiService.unsubscribeLiveActivity(any(), any()) } returns Response.success(null)
  }

  @After
  fun tearDown() {
    runBlocking { runCatching { runtime?.deinitialize() } }
    preferences.clearProjectData()

    WorkManagerTestInitHelper.closeWorkDatabase()
    unmockkConstructor(NotificationStatusChecker::class)
    unmockkObject(ApiService.Companion)
  }

  @Test
  fun `state of another project is released and removed with that project's credentials`() {
    preferences.projectOwner = previousProject
    preferences.subscriberId = "old-sub"
    preferences.subscriptionRequested = true
    preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
    liveActivityPersistence.addActiveId("live-1")
    liveActivityPersistence.setNotificationId("live-1", LIVE_ACTIVITY_NOTIFICATION_ID)
    postNotification(LIVE_ACTIVITY_NOTIFICATION_ID)

    val runtime = startRuntime()

    assertNull(preferences.subscriberId)
    assertTrue("Subscription request should survive", preferences.subscriptionRequested)
    assertFalse(runtime.isSubscribed())
    assertEquals(config.projectId, preferences.projectOwner?.projectId)
    assertTrue(preferences.getLiveActivitySubscriptions().isEmpty())
    assertTrue(liveActivityPersistence.getActiveIds().isEmpty())
    assertTrue(
      "Live Activity of the previous project should be dismissed",
      notificationManager().activeNotifications.none { it.id == LIVE_ACTIVITY_NOTIFICATION_ID },
    )
    assertTrue(
      "Token of the previous subscriber must not be synced",
      uniqueWork(UploadManager.SYNC_TOKEN_WORK_NAME).none { it.state == WorkInfo.State.ENQUEUED },
    )

    runCleanupWork()

    coVerify(timeout = 5_000) {
      apiService.unregisterSubscriber(previousProject.apiKey, previousProject.projectId, "old-sub")
    }
    coVerify(timeout = 5_000) {
      apiService.unsubscribeLiveActivity(
        match { it.endsWith("/projects/${previousProject.projectId}/live-notifications/live-1/subscribers/live-sub-1") },
        previousProject.apiKey,
      )
    }
  }

  @Test
  fun `state of another project without subscriptions schedules no cleanup`() {
    preferences.projectOwner = previousProject

    startRuntime()

    assertTrue(cleanupWork().isEmpty())
    assertEquals(config.projectId, preferences.projectOwner?.projectId)
  }

  @Test
  fun `state saved before project tracking is adopted by the current project`() {
    preferences.subscriberId = "sub-1"
    preferences.subscriptionRequested = true

    val runtime = startRuntime()

    assertEquals("sub-1", preferences.subscriberId)
    assertTrue(runtime.isSubscribed())
    assertEquals(config.projectId, preferences.projectOwner?.projectId)
    assertTrue(cleanupWork().isEmpty())
  }

  @Test
  fun `rotated api key keeps the subscription of the same project`() {
    preferences.projectOwner = Config.create(config.projectId, "00000000-0000-0000-0000-000000000003")
    preferences.subscriberId = "sub-1"
    preferences.subscriptionRequested = true

    val runtime = startRuntime()

    assertEquals("sub-1", preferences.subscriberId)
    assertTrue(runtime.isSubscribed())
    assertEquals(config.apiKey, preferences.projectOwner?.apiKey)
    assertTrue(cleanupWork().isEmpty())
  }

  private fun startRuntime(): PushNotificationsRuntime =
    PushNotificationsRuntime(application, config, PushNotificationsCallbacks()).also { runtime = it }

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

  private fun runCleanupWork() {
    val work = cleanupWork().single()
    requireNotNull(WorkManagerTestInitHelper.getTestDriver(application)).setAllConstraintsMet(work.id)
  }

  private companion object {
    const val LIVE_ACTIVITY_NOTIFICATION_ID = 4242
  }
}
