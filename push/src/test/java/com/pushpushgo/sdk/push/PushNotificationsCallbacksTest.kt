package com.pushpushgo.sdk.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

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
}
