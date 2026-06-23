package com.pushpushgo.sdk.push.network

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class SharedPreferencesHelperTest {
  private lateinit var prefs: SharedPreferencesHelper

  @Before
  fun setUp() {
    prefs = SharedPreferencesHelper(getApplicationContext(), prefsName = "test_prefs")
  }

  @Test
  fun `notification id is stored and retrieved`() {
    assertEquals(-1, prefs.getNotificationId("a"))

    prefs.setNotificationId("a", 42)

    assertEquals(42, prefs.getNotificationId("a"))
  }

  @Test
  fun `notification id eviction never deletes subscription state (ISSUE-14)`() {
    prefs.subscriberId = "sub-123"
    prefs.lastToken = "token-abc"
    prefs.isSubscribed = true

    // Overflow the notification-id cache well past its MAX bound.
    repeat(1100) { prefs.setNotificationId("nId-$it", it) }

    assertEquals("sub-123", prefs.subscriberId)
    assertEquals("token-abc", prefs.lastToken)
    assertTrue(prefs.isSubscribed)
  }

  @Test
  fun `notification id eviction is deterministic FIFO (ISSUE-14)`() {
    repeat(1000) { prefs.setNotificationId("k-$it", it) }
    assertEquals(0, prefs.getNotificationId("k-0"))

    // One more entry evicts exactly the oldest (k-0), nothing else.
    prefs.setNotificationId("k-1000", 1000)

    assertEquals(-1, prefs.getNotificationId("k-0"))
    assertEquals(1, prefs.getNotificationId("k-1"))
    assertEquals(1000, prefs.getNotificationId("k-1000"))
  }

  @Test
  fun `re-setting a key refreshes its recency so it survives eviction`() {
    repeat(1000) { prefs.setNotificationId("k-$it", it) }

    // Touch the oldest key again — it should move to the back of the queue.
    prefs.setNotificationId("k-0", 999_999)

    // Next insertion now evicts k-1 (the new oldest), not k-0.
    prefs.setNotificationId("k-1000", 1000)

    assertEquals(999_999, prefs.getNotificationId("k-0"))
    assertEquals(-1, prefs.getNotificationId("k-1"))
  }
}
