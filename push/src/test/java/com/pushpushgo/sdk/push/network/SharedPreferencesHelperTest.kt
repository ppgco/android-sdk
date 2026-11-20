package com.pushpushgo.sdk.push.network

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.testConfig
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SharedPreferencesHelperTest {
  private lateinit var prefs: SharedPreferencesHelper

  @Before
  fun setUp() {
    prefs = SharedPreferencesHelper(getApplicationContext(), prefsName = "test_prefs")
  }

  @After
  fun tearDown() {
    prefs.clearProjectData()
  }

  @Test
  fun `notification id is stored and retrieved`() {
    assertEquals(-1, prefs.getNotificationId("a"))

    prefs.setNotificationId("a", 42)

    assertEquals(42, prefs.getNotificationId("a"))
  }

  @Test
  fun `notification id eviction never deletes subscription state`() {
    prefs.subscriberId = "sub-123"
    prefs.lastToken = "token-abc"
    prefs.subscriptionRequested = true

    // Overflow the notification-id cache well past its MAX bound.
    repeat(1100) { prefs.setNotificationId("nId-$it", it) }

    assertEquals("sub-123", prefs.subscriberId)
    assertEquals("token-abc", prefs.lastToken)
    assertTrue(prefs.subscriptionRequested)
  }

  @Test
  fun `notification id eviction is deterministic FIFO`() {
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

  @Test
  fun `project owner keeps the credentials needed to reach its project`() {
    val owner = Config.create("8kp60aqdi49eioqzp0ihiytn", "00000000-0000-0000-0000-000000000001", "https://api.example.com")
    assertNull(prefs.projectOwner)

    prefs.projectOwner = owner

    val stored = assertNotNull(prefs.projectOwner)
    assertEquals(owner.projectId, stored.projectId)
    assertEquals(owner.apiKey, stored.apiKey)
    assertEquals(owner.apiUrl, stored.apiUrl)
  }

  @Test
  fun `subscription request tells a stored opt-out from a missing value`() {
    assertFalse(prefs.isSubscriptionRequestStored())
    assertFalse(prefs.subscriptionRequested)

    prefs.subscriptionRequested = false

    assertTrue(prefs.isSubscriptionRequestStored())
    assertFalse(prefs.subscriptionRequested)
  }

  @Test
  fun `clear project data removes project state and preserves the subscription request and installation settings`() {
    prefs.subscriberId = "sub-123"
    prefs.lastToken = "token-abc"
    prefs.subscriptionRequested = true
    prefs.customIntentFlags = 42
    val installationId = prefs.installationId
    prefs.setLiveActivitySubscriberId("live-1", "live-sub-1")
    prefs.setNotificationId("notification-1", 7)
    prefs.projectOwner = testConfig()

    prefs.clearProjectData()

    assertNull(prefs.subscriberId)
    assertNull(prefs.lastToken)
    assertNull(prefs.projectOwner)
    assertEquals("", prefs.getLiveActivitySubscriberId("live-1"))
    assertEquals(-1, prefs.getNotificationId("notification-1"))
    assertTrue(prefs.subscriptionRequested)
    assertEquals(42, prefs.customIntentFlags)
    assertEquals(installationId, prefs.installationId)
  }
}
