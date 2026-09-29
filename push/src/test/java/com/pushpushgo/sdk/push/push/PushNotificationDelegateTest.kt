package com.pushpushgo.sdk.push.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.testConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PushNotificationDelegateTest {
  private val context: Application = getApplicationContext()
  private val apiRepository = mockk<ApiRepository>(relaxed = true)
  private val bitmap = Bitmap.createBitmap(10, 5, Bitmap.Config.ARGB_8888)
  private lateinit var delegate: PushNotificationDelegate

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    PushNotifications.initialize(application = context, config = testConfig())
    shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    coEvery { apiRepository.getBitmapFromUrl(ICON_URL) } returns null

    delegate =
      PushNotificationDelegate(
        sharedPreferencesHelper = PushNotifications.sharedPreferencesHelper,
        apiRepository = apiRepository,
        uploadManager = mockk(relaxed = true),
        callbacks = mockk(relaxed = true),
      )
  }

  @After
  fun tearDown() {
    runBlocking { PushNotifications.deinitialize() }
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

    // Messaging services call onMessageReceived on a worker thread.
    val worker = thread { delegate.onMessageReceived(pushMessage(), context) }
    pumpMainLooperUntil { downloadStarted.count == 0L }
    assertEquals("image download did not start", 0L, downloadStarted.count)
    // The service gets destroyed while the image is still downloading.
    delegate.onDestroy()
    finishDownload.complete(Unit)
    pumpMainLooperUntil { !worker.isAlive && postedNotifications().isNotEmpty() }

    assertEquals(BIG_PICTURE_STYLE, postedNotification().extras.getString(Notification.EXTRA_TEMPLATE))
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

  /** Delivers the message the way a messaging service does (on a worker thread) and waits for the notification. */
  private fun deliver() {
    val worker = thread { delegate.onMessageReceived(pushMessage(), context) }
    pumpMainLooperUntil { !worker.isAlive && postedNotifications().isNotEmpty() }
  }

  /** Also runs work dispatched to the main thread, which Robolectric only executes when its looper is idled. */
  private fun pumpMainLooperUntil(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 10_000
    while (!condition() && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
    }
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
