package com.pushpushgo.sdk.push

import android.content.Intent
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.dto.PushPushGoNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class PushNotificationsTest {
  private lateinit var systemUnderTest: PushNotifications

  @Before
  fun setUp() {
    systemUnderTest =
      PushNotifications.initialize(
        application = getApplicationContext(),
        config =
          Config.create(
            projectId = "hm93nzyt5bmczmtjeghy2aph",
            apiKey = "e5d706d7-0ebb-4793-9edc-6bd9eb9aff3a",
          ),
      )
  }

  @Test
  fun `check is intent PPGo notification`() {
    assertFalse(systemUnderTest.isPushPushGoNotification(Intent()))
    assertTrue(systemUnderTest.isPushPushGoNotification(Intent().putExtra("project", "")))
  }

  @Test
  fun `check is extras map PPGo notification`() {
    assertFalse(systemUnderTest.isPushPushGoNotification(mapOf()))
    assertTrue(systemUnderTest.isPushPushGoNotification(mapOf("project" to "")))
  }

  @Test
  fun `get notification data mapping from invalid intent`() {
    assertEquals(null, systemUnderTest.getNotificationDetails(Intent().putExtra("adsasfdafdf", "")))
  }

  @Test
  fun `get notification data mapping from intent`() {
    val dto =
      PushPushGoNotification(
        title = "Notification title",
        body = "Notification body",
        campaignId = "campaign ID",
        priority = 0,
        redirectLink = "https://pushpushgo.com/pl/blog",
      )
    assertEquals(
      dto,
      systemUnderTest.getNotificationDetails(
        Intent()
          .putExtra("campaign", "campaign ID")
          .putExtra("redirectLink", "https://pushpushgo.com/pl/blog")
          .putExtra(
            "notification",
            """{"badge":1,"sound":"default","vibrate":"true","title":"Notification title","body":"Notification body","priority":0,"click_action":"APP_PUSH_CLICK"}""",
          ),
      ),
    )
  }

  @Test
  fun `get notification data mapping from map`() {
    val dto =
      PushPushGoNotification(
        title = "Notification title",
        body = "Notification body",
        campaignId = "campaign ID",
        priority = 0,
        redirectLink = "https://pushpushgo.com/pl/blog",
      )
    assertEquals(
      dto,
      systemUnderTest.getNotificationDetails(
        mapOf(
          "campaign" to "campaign ID",
          "redirectLink" to "https://pushpushgo.com/pl/blog",
          "notification" to
            """{"badge":1,"sound":"default","vibrate":"true","title":"Notification title","body":"Notification body","priority":0,"click_action":"APP_PUSH_CLICK"}""",
        ),
      ),
    )
  }

  @Test
  fun `get notification data mapping with some nulls from map`() {
    val dto =
      PushPushGoNotification(
        title = "Notification title",
        body = "Notification body",
        campaignId = "campaign ID",
        priority = 0,
        redirectLink = "https://pushpushgo.com/pl/blog",
      )
    assertEquals(
      dto,
      systemUnderTest.getNotificationDetails(
        mapOf(
          "campaign" to "campaign ID",
          "redirectLink" to "https://pushpushgo.com/pl/blog",
          "notification" to
            """{"badge":1,"sound":"default","vibrate":"true","title":"Notification title","body":"Notification body","priority":0,"click_action":"APP_PUSH_CLICK"}""",
          "actions" to
            """[{"link":"https://pushpushgo.com","action":"ACTION_PUSH","title":"Test action"},{"link":null,"action":"ACTION_PUSH","title":"Test action"}]""",
        ),
      ),
    )
  }

  @Test
  fun `get notification data mapping from invalid map`() {
    assertEquals(null, systemUnderTest.getNotificationDetails(mapOf("adsasfdafdf" to "")))
  }
}
