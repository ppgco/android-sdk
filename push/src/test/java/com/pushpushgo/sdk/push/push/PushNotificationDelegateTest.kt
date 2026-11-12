package com.pushpushgo.sdk.push.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.InvalidProjectIdHandler
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.PushNotificationsCallbacks
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.otherProjectTestConfig
import com.pushpushgo.sdk.push.testConfig
import com.pushpushgo.sdk.push.work.UploadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PushNotificationDelegateTest {
  private val context: Application = getApplicationContext()
  private val apiRepository = mockk<ApiRepository>()
  private val uploadManager = mockk<UploadManager>(relaxUnitFun = true)
  private val callbacks = PushNotificationsCallbacks()
  private val bitmap = Bitmap.createBitmap(10, 5, Bitmap.Config.ARGB_8888)
  private lateinit var delegate: PushNotificationDelegate

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    PushNotifications.initialize(application = context, config = testConfig())
    shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    PushNotifications.sharedPreferencesHelper.subscriptionRequested = true
    coEvery { apiRepository.getBitmapFromUrl(ICON_URL) } returns null

    delegate =
      PushNotificationDelegate(
        sharedPreferencesHelper = PushNotifications.sharedPreferencesHelper,
        apiRepository = apiRepository,
        uploadManager = uploadManager,
        callbacks = callbacks,
      )
  }

  @After
  fun tearDown() {
    runTest { PushNotifications.reset() }
    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  @Test
  fun `notification is posted with image when service is destroyed mid download`() {
    val downloadStarted = CountDownLatch(1)
    val finishDownload = CompletableDeferred<Unit>()
    coEvery { apiRepository.getBitmapFromUrl(IMAGE_URL) } coAnswers {
      downloadStarted.countDown()
      finishDownload.await()
      bitmap
    }

    val worker = thread { delegate.onMessageReceived(pushMessage(), context) }
    assertTrue(downloadStarted.await(10, TimeUnit.SECONDS), "image download did not start")

    delegate.onDestroy()
    finishDownload.complete(Unit)
    worker.join(10_000)

    assertEquals(BIG_PICTURE_STYLE, postedNotification().extras.getString(Notification.EXTRA_TEMPLATE))
    verify(exactly = 1) {
      uploadManager.sendEvent(EventType.DELIVERED, 0, "campaign-id", "subscriber-id")
    }
  }

  @Test
  fun `image download is retried after transient network error`() {
    var attempts = 0
    coEvery { apiRepository.getBitmapFromUrl(IMAGE_URL) } coAnswers {
      if (attempts++ == 0) throw UnknownHostException("Unable to resolve host")
      bitmap
    }

    deliver()

    assertEquals(BIG_PICTURE_STYLE, postedNotification().extras.getString(Notification.EXTRA_TEMPLATE))
    coVerify(exactly = 2) { apiRepository.getBitmapFromUrl(IMAGE_URL) }
  }

  @Test
  fun `notification is posted without image when image cannot be downloaded`() {
    coEvery { apiRepository.getBitmapFromUrl(IMAGE_URL) } throws IllegalStateException("HTTP 404")

    deliver()

    assertEquals(BIG_TEXT_STYLE, postedNotification().extras.getString(Notification.EXTRA_TEMPLATE))
    coVerify(exactly = 1) { apiRepository.getBitmapFromUrl(IMAGE_URL) }
  }

  @Test
  fun `regular push is ignored when notifications are disabled`() {
    shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    delegate.onMessageReceived(pushMessage(), context)

    assertEquals(0, postedNotifications().size)
    coVerify(exactly = 0) { apiRepository.getBitmapFromUrl(any()) }
    verify(exactly = 0) { uploadManager.sendEvent(any(), any(), any(), any()) }
  }

  @Test
  fun `live activity push is reported as invalid only when it belongs to another project`() {
    PushNotifications.sharedPreferencesHelper.subscriptionRequested = false
    val otherProjectId = otherProjectTestConfig().projectId
    val invalidProjects = mutableListOf<Triple<String, String, String>>()
    callbacks.invalidProjectIdHandler =
      InvalidProjectIdHandler { pushProjectId, pushSubscriberId, currentProjectId ->
        invalidProjects += Triple(pushProjectId, pushSubscriberId, currentProjectId)
      }

    delegate.onMessageReceived(liveActivityPush(projectId = otherProjectId), context)
    delegate.onMessageReceived(liveActivityPush(projectId = testConfig().projectId), context)

    assertEquals(listOf(Triple(otherProjectId, "subscriber-id", testConfig().projectId)), invalidProjects)
  }

  @Test
  fun `new token updates live activity subscriptions without a push subscription`() {
    PushNotifications.sharedPreferencesHelper.subscriptionRequested = false
    PushNotifications.sharedPreferencesHelper.setLiveActivitySubscriberId("live-1", "live-sub-1")

    delegate.onNewToken("new-token")

    verify(exactly = 1) { uploadManager.updateLiveActivityEndpoints("new-token") }
    verify(exactly = 0) { uploadManager.syncToken(any(), any()) }
  }

  @Test
  fun `new token does not touch live activities without subscriptions`() {
    delegate.onNewToken("new-token")

    verify(exactly = 0) { uploadManager.updateLiveActivityEndpoints(any()) }
  }

  private fun liveActivityPush(projectId: String) =
    PushMessage(
      from = "702491788352",
      data =
        mapOf(
          "type" to "live_notification",
          "liveNotificationId" to "live-1",
          "event" to "update",
          "project" to projectId,
          "subscriber" to "subscriber-id",
        ),
      notification = null,
    )

  /** Delivers the message the way a messaging service does (on a worker thread) and waits for the notification. */
  private fun deliver() {
    val worker = thread { delegate.onMessageReceived(pushMessage(), context) }
    worker.join(10_000)
  }

  private fun postedNotifications() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

  private fun postedNotification(): Notification {
    val notifications = postedNotifications()
    assertEquals(1, notifications.size)
    return notifications.single()
  }

  private fun pushMessage() =
    PushMessage(
      from = "702491788352",
      data =
        mapOf(
          "project" to testConfig().projectId,
          "subscriber" to "subscriber-id",
          "campaign" to "campaign-id",
          "nId" to "notification-id",
          "image" to IMAGE_URL,
          "icon" to ICON_URL,
          "redirectLink" to "https://www.polsatnews.pl/",
          "notification" to
            """{"title":"Title","body":"Body","sound":"default","vibrate":"true","priority":0,"badge":1,"click_action":"APP_PUSH_CLICK"}""",
        ),
      notification = null,
    )

  private companion object {
    const val IMAGE_URL = "https://static-f.pushpushgo.com/image_medium.webp"
    const val ICON_URL = "https://static-a.pushpushgo.com/icon_original.webp"
    const val BIG_PICTURE_STYLE = "android.app.Notification\$BigPictureStyle"
    const val BIG_TEXT_STYLE = "android.app.Notification\$BigTextStyle"
  }
}
