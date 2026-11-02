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
  fun `flow and callback receive the same distinct statuses in order`() {
    val callbacks = PushNotificationsCallbacks()
    val received = mutableListOf<Pair<SubscriptionStatus, SubscriptionStatus>>()
    callbacks.subscriptionStatusCallback =
      SubscriptionStatusCallback { status ->
        received += status to callbacks.subscriptionStatusFlow.value
      }

    callbacks.publishSubscriptionStatus(SubscriptionStatus.Unsubscribed)
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-1", "project-1"))
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-1", "project-1"))
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-2", "project-1"))

    assertEquals(
      listOf(
        SubscriptionStatus.Subscribed("sub-1", "project-1") to
          SubscriptionStatus.Subscribed("sub-1", "project-1"),
        SubscriptionStatus.Subscribed("sub-2", "project-1") to
          SubscriptionStatus.Subscribed("sub-2", "project-1"),
      ),
      received,
    )
  }

  @Test
  fun `new callback receives only future status changes and null removes it`() {
    val callbacks = PushNotificationsCallbacks()
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-1", "project-1"))
    val received = mutableListOf<SubscriptionStatus>()
    callbacks.subscriptionStatusCallback = SubscriptionStatusCallback(received::add)

    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-1", "project-1"))
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-2", "project-1"))
    callbacks.subscriptionStatusCallback = null
    callbacks.publishSubscriptionStatus(SubscriptionStatus.Unsubscribed)

    assertEquals(listOf(SubscriptionStatus.Subscribed("sub-2", "project-1")), received)
  }

  @Test
  fun `callback exception does not escape publication`() {
    val callbacks = PushNotificationsCallbacks()
    callbacks.subscriptionStatusCallback = SubscriptionStatusCallback { throw IllegalStateException("callback failed") }

    callbacks.publishSubscriptionStatus(SubscriptionStatus.Subscribed("sub-1", "project-1"))

    assertEquals(SubscriptionStatus.Subscribed("sub-1", "project-1"), callbacks.subscriptionStatusFlow.value)
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
