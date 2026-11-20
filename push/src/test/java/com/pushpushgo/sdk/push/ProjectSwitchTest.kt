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
import com.pushpushgo.sdk.push.network.DefaultApiServiceFactory
import com.pushpushgo.sdk.push.network.FakeApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.PushNotificationDelegate
import com.pushpushgo.sdk.push.utils.PlatformPushTokenProvider
import com.pushpushgo.sdk.push.work.UploadManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ProjectSwitchTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()
  private val otherConfig = otherProjectTestConfig()

  private val apiService = FakeApiService()
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    LiveActivityPersistence(application).clearAll()
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    mockkObject(DefaultApiServiceFactory)
    every { DefaultApiServiceFactory.create(any()) } returns apiService
    mockkConstructor(PlatformPushTokenProvider::class)
    coEvery { anyConstructed<PlatformPushTokenProvider>().getToken() } returns "push-token"
  }

  @After
  fun tearDown() {
    PushNotifications.setInvalidProjectIdHandler(null)
    runTest { PushNotifications.reset() }
    preferences.clearProjectData()
    WorkManagerTestInitHelper.closeWorkDatabase()
    unmockkObject(DefaultApiServiceFactory)
    unmockkConstructor(PlatformPushTokenProvider::class)
  }

  @Test
  fun `cached live activities facade rejects calls after the switch`() =
    runTest {
      PushNotifications.initialize(application, config)
      val liveActivities = PushNotifications.liveActivities

      PushNotifications.switchProject(otherConfig)

      assertFailsWith<IllegalStateException> { liveActivities.subscribe("live-1") }
      assertFailsWith<IllegalStateException> { liveActivities.getActiveLiveActivities() }
      assertTrue(apiService.liveActivitySubscribeRequests.isEmpty())
    }

  @Test
  fun `offline switch moves to the new project and enqueues cleanup of the previous one`() =
    runTest {
      setSubscribed()
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      apiService.exception = IOException("offline")
      PushNotifications.initialize(application, config)

      PushNotifications.switchProject(otherConfig)

      assertEquals(otherConfig.projectId, PushNotifications.getProjectId())
      assertFalse(PushNotifications.isSubscribed())
      assertTrue(PushNotifications.isSubscriptionRequested())
      assertEquals(WorkInfo.State.ENQUEUED, cleanupWork().single().state)
    }

  @Test
  fun `switch to the current config does nothing`() =
    runTest {
      setSubscribed()
      PushNotifications.initialize(application, config)
      val liveActivities = PushNotifications.liveActivities

      PushNotifications.switchProject(config)

      assertEquals("sub-123", PushNotifications.getSubscriberId())
      assertTrue(cleanupWork().isEmpty())
      assertEquals(emptyList(), liveActivities.getActiveLiveActivities())
    }

  @Test
  fun `invalid project id handler survives the switch`() =
    runTest {
      var invalidProject: Triple<String, String, String>? = null
      PushNotifications.setInvalidProjectIdHandler { pushProjectId, pushSubscriberId, currentProjectId ->
        invalidProject = Triple(pushProjectId, pushSubscriberId, currentProjectId)
      }
      PushNotifications.initialize(application, config)

      PushNotifications.switchProject(otherConfig)
      PushNotifications.handleBackgroundNotificationClick(
        Intent()
          .putExtra(PushNotificationDelegate.PROJECT_ID_EXTRA, config.projectId)
          .putExtra(PushNotificationDelegate.SUBSCRIBER_ID_EXTRA, "subscriber"),
      )

      assertEquals(Triple(config.projectId, "subscriber", otherConfig.projectId), invalidProject)
    }

  private fun setSubscribed() {
    preferences.projectOwner = config
    preferences.subscriberId = "sub-123"
    preferences.subscriptionRequested = true
  }

  private fun cleanupWork(): List<WorkInfo> =
    WorkManager.getInstance(application).getWorkInfosByTag(UploadManager.PROJECT_CLEANUP_TAG).get()
}
