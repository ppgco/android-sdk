package com.pushpushgo.sdk.push

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import java.util.Collections
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class PushNotificationsCallbacksTest {
  @Test
  fun `callback receives the current status and then distinct changes in order`() {
    val callbacks = PushNotificationsCallbacks()
    val received = mutableListOf<SubscriptionStatus>()
    callbacks.subscriptionStatusCallback = SubscriptionStatusCallback(received::add)

    listOf(
      SubscriptionStatus.Subscribed("sub-1", "project-1"),
      SubscriptionStatus.Subscribed("sub-1", "project-1"),
      SubscriptionStatus.Subscribed("sub-2", "project-1"),
    ).forEach {
      callbacks.publishSubscriptionStatus(it)
      shadowOf(Looper.getMainLooper()).idle()
    }

    assertEquals(
      listOf(
        SubscriptionStatus.Unsubscribed,
        SubscriptionStatus.Subscribed("sub-1", "project-1"),
        SubscriptionStatus.Subscribed("sub-2", "project-1"),
      ),
      received,
    )
  }

  @Test
  fun `callback set later receives the current status and null removes it`() {
    val callbacks = PushNotificationsCallbacks()
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-1", "project-1"))
    val received = mutableListOf<SubscriptionStatus>()

    callbacks.subscriptionStatusCallback = SubscriptionStatusCallback(received::add)
    shadowOf(Looper.getMainLooper()).idle()
    callbacks.subscriptionStatusCallback = null
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Unsubscribed)
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(listOf(SubscriptionStatus.Subscribed("sub-1", "project-1")), received)
  }

  @Test
  @LooperMode(LooperMode.Mode.PAUSED)
  fun `public callbacks triggered on a worker are delivered on the main thread`() {
    val callbacks = PushNotificationsCallbacks()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val received = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())

    fun record(name: String) {
      received += name to (Looper.myLooper() == Looper.getMainLooper())
    }

    callbacks.notificationClickHandler = NotificationClickHandler { _, _, _ -> record("click") }
    callbacks.invalidProjectIdHandler = InvalidProjectIdHandler { _, _, _ -> record("invalid-project") }
    callbacks.errorCallback = PushNotificationsErrorCallback { record("error") }
    callbacks.subscriptionStatusCallback = SubscriptionStatusCallback { record("subscription") }
    shadowOf(Looper.getMainLooper()).idle()
    received.clear()

    thread {
      callbacks.notifyNotificationClick(context, "https://example.com", 0)
      callbacks.notifyInvalidProjectId("push-project", "subscriber", "current-project")
      callbacks.notifyError(IllegalStateException("test"))
      callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("subscriber", "current-project"))
    }.join()

    assertEquals(emptyList<Pair<String, Boolean>>(), received)
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(
      listOf(
        "click" to true,
        "invalid-project" to true,
        "error" to true,
        "subscription" to true,
      ),
      received,
    )
  }
}
