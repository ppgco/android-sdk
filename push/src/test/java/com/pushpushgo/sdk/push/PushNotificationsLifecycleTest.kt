package com.pushpushgo.sdk.push

import android.Manifest
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.liveactivity.LiveActivityPersistence
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeResponse
import com.pushpushgo.sdk.push.network.data.TokenResponse
import com.pushpushgo.sdk.push.utils.getPlatformPushToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
class PushNotificationsLifecycleTest {
  private val application by lazy { getApplicationContext<android.app.Application>() }
  private val config =
    Config.create(
      projectId = "hm93nzyt5bmczmtjeghy2aph",
      apiKey = "e5d706d7-0ebb-4793-9edc-6bd9eb9aff3a",
    )

  private lateinit var apiService: ApiService
  private lateinit var preferences: SharedPreferencesHelper

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    preferences = SharedPreferencesHelper(application)
    preferences.clearProjectData()
    LiveActivityPersistence(application).clearAll()

    apiService = mockk(relaxed = true)
    mockkObject(ApiService.Companion)
    mockkStatic("com.pushpushgo.sdk.push.utils.PushTokenUtilsKt")
    every { ApiService.fromConfig(any()) } returns apiService
    coEvery { getPlatformPushToken(any()) } returns "token-123"
    coEvery { apiService.unregisterSubscriber(any(), any(), any()) } returns Response.success(null)
    coEvery { apiService.registerSubscriber(any(), any(), any()) } returns TokenResponse(id = "sub-new")

    PushNotifications.initialize(application, config)
  }

  @After
  fun tearDown() {
    PushNotifications.setNotificationClickHandler(null)
    PushNotifications.setInvalidProjectIdHandler(null)
    PushNotifications.setErrorCallback(null)
    if (PushNotifications.isInitialized()) {
      PushNotifications.sharedPreferencesHelper.isSubscribed = false
      runBlocking { PushNotifications.deinitialize() }
    }
    unmockkObject(ApiService.Companion)
    unmockkStatic("com.pushpushgo.sdk.push.utils.PushTokenUtilsKt")
  }

  @Test
  fun `deinitialize unsubscribes clears project data and releases runtime`() =
    runBlocking {
      preferences.subscriberId = "sub-123"
      preferences.lastToken = "token-123"
      preferences.isSubscribed = true
      preferences.customIntentFlags = 17
      val installationId = preferences.installationId
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      preferences.setNotificationId("notification-1", 51)
      LiveActivityPersistence(application).addActiveId("live-1")

      PushNotifications.deinitialize()

      coVerify(exactly = 1) { apiService.unregisterSubscriber(any(), config.projectId, "sub-123") }
      assertFalse(PushNotifications.isInitialized())

      assertNull(preferences.subscriberId)
      assertNull(preferences.lastToken)
      assertFalse(preferences.isSubscribed)
      assertEquals("", preferences.getLiveActivitySubscriberId("live-1"))
      assertEquals(-1, preferences.getNotificationId("notification-1"))
      assertEquals(17, preferences.customIntentFlags)
      assertEquals(installationId, preferences.installationId)
      assertTrue(LiveActivityPersistence(application).getActiveIds().isEmpty())
    }

  @Test
  fun `deinitialize preserves state and runtime when unsubscribe fails`() =
    runBlocking {
      preferences.subscriberId = "sub-123"
      preferences.lastToken = "token-123"
      preferences.isSubscribed = true
      preferences.setLiveActivitySubscriberId("live-1", "live-sub-1")
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } throws IOException("offline")

      val failure = runCatching { PushNotifications.deinitialize() }.exceptionOrNull()

      assertTrue(failure is IOException)
      assertTrue(PushNotifications.isInitialized())
      assertEquals("sub-123", preferences.subscriberId)
      assertEquals("token-123", preferences.lastToken)
      assertTrue(preferences.isSubscribed)
      assertEquals("live-sub-1", preferences.getLiveActivitySubscriberId("live-1"))
    }

  @Test
  fun `deinitialize skips unregister and clears stale subscriber when not subscribed`() =
    runBlocking {
      preferences.subscriberId = "stale-sub-123"
      preferences.lastToken = "token-123"
      preferences.isSubscribed = false

      PushNotifications.deinitialize()

      coVerify(exactly = 0) { apiService.unregisterSubscriber(any(), any(), any()) }
      assertFalse(PushNotifications.isInitialized())
      assertNull(preferences.subscriberId)
      assertNull(preferences.lastToken)
    }

  @Test
  fun `cached live activities facade rejects calls after deinitialize`() =
    runBlocking {
      preferences.isSubscribed = false

      val liveActivities = PushNotifications.liveActivities

      PushNotifications.deinitialize()

      val subscribeFailure = runCatching { liveActivities.subscribe("live-1") }.exceptionOrNull()
      val queryFailure = runCatching { liveActivities.getSubscriberId("live-1") }.exceptionOrNull()

      assertEquals("PushNotifications is deinitialized", subscribeFailure?.message)
      assertEquals("PushNotifications is deinitialized", queryFailure?.message)
      coVerify(exactly = 0) { apiService.subscribeLiveActivity(any(), any(), any()) }
    }

  @Test
  fun `deinitialize waits for live activity subscription`() =
    runBlocking {
      val subscriptionStarted = CompletableDeferred<Unit>()
      val finishSubscription = CompletableDeferred<Unit>()
      coEvery { apiService.subscribeLiveActivity(any(), any(), any()) } coAnswers {
        subscriptionStarted.complete(Unit)
        finishSubscription.await()
        LiveActivitySubscribeResponse("live-sub-1")
      }

      val subscription = launch(Dispatchers.Default) { PushNotifications.liveActivities.subscribe("live-1") }
      subscriptionStarted.await()

      val deinitialize = launch(Dispatchers.Default) { PushNotifications.deinitialize() }
      yield()

      assertFalse(deinitialize.isCompleted)

      finishSubscription.complete(Unit)
      subscription.join()
      deinitialize.join()

      assertFalse(PushNotifications.isInitialized())
      assertEquals("", preferences.getLiveActivitySubscriberId("live-1"))
    }

  @Test
  fun `mutation queued behind deinitialize cannot access released runtime`() =
    runBlocking {
      preferences.subscriberId = "sub-123"
      preferences.lastToken = "token-123"
      preferences.isSubscribed = true
      val unsubscribeStarted = CompletableDeferred<Unit>()
      val finishUnsubscribe = CompletableDeferred<Unit>()
      coEvery { apiService.unregisterSubscriber(any(), any(), any()) } coAnswers {
        unsubscribeStarted.complete(Unit)
        finishUnsubscribe.await()
        Response.success(null)
      }

      val deinitialize = launch(Dispatchers.Default) { PushNotifications.deinitialize() }
      unsubscribeStarted.await()

      val initializeFailure =
        assertThrows(IllegalStateException::class.java) {
          PushNotifications.initialize(application, config)
        }
      assertEquals("PushNotifications lifecycle mutation is in progress", initializeFailure.message)

      val clickHandler = NotificationClickHandler { _, _, _ -> }
      PushNotifications.setNotificationClickHandler(clickHandler)
      assertSame(clickHandler, PushNotifications.notificationClickHandler)

      val subscribeFailure = CompletableDeferred<Throwable?>()
      val queuedSubscribe =
        launch(Dispatchers.Default) {
          subscribeFailure.complete(runCatching { PushNotifications.subscribe() }.exceptionOrNull())
        }
      finishUnsubscribe.complete(Unit)
      deinitialize.join()
      queuedSubscribe.join()

      assertTrue(subscribeFailure.await() is IllegalStateException)
      coVerify(exactly = 0) { apiService.registerSubscriber(any(), any(), any()) }
    }

  @Test
  fun `callbacks can be configured while uninitialized and survive another project initialization`() =
    runBlocking {
      preferences.isSubscribed = false
      PushNotifications.deinitialize()

      val clickHandler = NotificationClickHandler { _, _, _ -> }
      val invalidProjectIdHandler = InvalidProjectIdHandler { _, _, _ -> }
      val errorCallback = PushNotificationsErrorCallback { }

      PushNotifications.setNotificationClickHandler(clickHandler)
      PushNotifications.setInvalidProjectIdHandler(invalidProjectIdHandler)
      PushNotifications.setErrorCallback(errorCallback)

      assertSame(clickHandler, PushNotifications.notificationClickHandler)
      assertSame(invalidProjectIdHandler, PushNotifications.invalidProjectIdHandler)
      assertSame(errorCallback, PushNotifications.errorCallback)

      val otherConfig =
        Config.create(
          projectId = "hm93nzyt5bmczmtjeghy2aaa",
          apiKey = "e5d706d7-0ebb-4793-9edc-6bd9eb9aff3a",
        )
      PushNotifications.initialize(application, otherConfig)

      assertSame(clickHandler, PushNotifications.notificationClickHandler)
      assertSame(invalidProjectIdHandler, PushNotifications.invalidProjectIdHandler)
      assertSame(errorCallback, PushNotifications.errorCallback)
    }

  @Test
  fun `null callbacks restore defaults`() {
    PushNotifications.setNotificationClickHandler(NotificationClickHandler { _, _, _ -> })
    PushNotifications.setInvalidProjectIdHandler(InvalidProjectIdHandler { _, _, _ -> })
    PushNotifications.setErrorCallback(PushNotificationsErrorCallback { })

    PushNotifications.setNotificationClickHandler(null)
    PushNotifications.setInvalidProjectIdHandler(null)
    PushNotifications.setErrorCallback(null)

    assertTrue(PushNotifications.notificationClickHandler is DefaultNotificationClickHandler)
    assertTrue(PushNotifications.invalidProjectIdHandler is DefaultInvalidProjectIdHandler)
    assertNull(PushNotifications.errorCallback)
  }

  @Test
  fun `active runtime uses callback holder`() {
    var callbackArguments: Triple<String, String, String>? = null
    PushNotifications.setInvalidProjectIdHandler { pushProjectId, pushSubscriberId, currentProjectId ->
      callbackArguments = Triple(pushProjectId, pushSubscriberId, currentProjectId)
    }

    PushNotifications.handleBackgroundNotificationClick(
      android.content
        .Intent()
        .putExtra(com.pushpushgo.sdk.push.push.PushNotificationDelegate.PROJECT_ID_EXTRA, "another-project")
        .putExtra(com.pushpushgo.sdk.push.push.PushNotificationDelegate.SUBSCRIBER_ID_EXTRA, "subscriber"),
    )

    assertEquals(Triple("another-project", "subscriber", config.projectId), callbackArguments)
  }

  @Test
  fun `another project can be initialized after deinitialize`() =
    runBlocking {
      preferences.isSubscribed = false
      PushNotifications.deinitialize()

      val otherConfig =
        Config.create(
          projectId = "hm93nzyt5bmczmtjeghy2aaa",
          apiKey = "e5d706d7-0ebb-4793-9edc-6bd9eb9aff3a",
        )
      PushNotifications.initialize(application, otherConfig)

      assertEquals(otherConfig.projectId, PushNotifications.getProjectId())
    }
}
