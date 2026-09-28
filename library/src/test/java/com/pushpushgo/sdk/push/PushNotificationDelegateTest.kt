package com.pushpushgo.sdk.push

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.network.ApiRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
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
  private val context: Context = getApplicationContext()
  private val api = mockk<ApiRepository>(relaxed = true)
  private val bitmap = Bitmap.createBitmap(10, 5, Bitmap.Config.ARGB_8888)

  @Before
  fun setUp() {
    val pushPushGo = mockk<PushPushGo>(relaxed = true)
    every { pushPushGo.isPPGoPush(any<Map<String, String>>()) } returns true
    every { pushPushGo.getProjectId() } returns PROJECT_ID
    every { pushPushGo.getNetwork() } returns api
    coEvery { api.getBitmapFromUrl(ICON_URL) } returns null

    mockkObject(PushPushGo.Companion)
    every { PushPushGo.getInstance() } returns pushPushGo
    every { PushPushGo.isInitialized() } returns true
  }

  @After
  fun tearDown() = unmockkAll()

  @Test
  fun `notification is posted with image when service is destroyed mid download`() {
    val downloadStarted = CountDownLatch(1)
    val finishDownload = CompletableDeferred<Unit>()
    coEvery { api.getBitmapFromUrl(IMAGE_URL) } coAnswers {
      downloadStarted.countDown()
      finishDownload.await()
      bitmap
    }
    val delegate = PushNotificationDelegate(context)

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
    coEvery { api.getBitmapFromUrl(IMAGE_URL) } coAnswers {
      if (attempts++ == 0) throw UnknownHostException("Unable to resolve host")
      bitmap
    }

    deliver(PushNotificationDelegate(context))

    assertEquals(BIG_PICTURE_STYLE, postedNotification().extras.getString(Notification.EXTRA_TEMPLATE))
    coVerify(exactly = 2) { api.getBitmapFromUrl(IMAGE_URL) }
  }

  @Test
  fun `notification is posted without image when image cannot be downloaded`() {
    coEvery { api.getBitmapFromUrl(IMAGE_URL) } throws IllegalStateException("HTTP 404")

    deliver(PushNotificationDelegate(context))

    assertEquals(BIG_TEXT_STYLE, postedNotification().extras.getString(Notification.EXTRA_TEMPLATE))
    coVerify(exactly = 1) { api.getBitmapFromUrl(IMAGE_URL) }
  }

  /** Delivers the message the way a messaging service does (on a worker thread) and waits for the notification. */
  private fun deliver(delegate: PushNotificationDelegate) {
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
          "project" to PROJECT_ID,
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
    const val PROJECT_ID = "648bf40e32ca635c992e0186"
    const val IMAGE_URL = "https://static-f.pushpushgo.com/image_medium.webp"
    const val ICON_URL = "https://static-a.pushpushgo.com/icon_original.webp"
    const val BIG_PICTURE_STYLE = "android.app.Notification\$BigPictureStyle"
    const val BIG_TEXT_STYLE = "android.app.Notification\$BigTextStyle"
  }
}
