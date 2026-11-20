package com.pushpushgo.sdk.push

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.dto.PushPushGoNotification
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class NotificationDataTest {
  private val notification =
    """{"badge":1,"sound":"default","vibrate":"true","title":"Notification title","body":"Notification body","priority":0,"click_action":"APP_PUSH_CLICK"}"""

  private val expected =
    PushPushGoNotification(
      title = "Notification title",
      body = "Notification body",
      campaignId = "campaign ID",
      priority = 0,
      redirectLink = "https://pushpushgo.com/pl/blog",
    )

  @Test
  fun `notification details are read from an intent`() {
    val intent =
      Intent()
        .putExtra("campaign", "campaign ID")
        .putExtra("redirectLink", "https://pushpushgo.com/pl/blog")
        .putExtra("notification", notification)

    val details = PushNotifications.getNotificationDetails(intent)

    assertEquals(expected, details)
  }

  @Test
  fun `notification details are read from a map`() {
    val data =
      mapOf(
        "campaign" to "campaign ID",
        "redirectLink" to "https://pushpushgo.com/pl/blog",
        "notification" to notification,
      )

    val details = PushNotifications.getNotificationDetails(data)

    assertEquals(expected, details)
  }

  @Test
  fun `an action without a link does not prevent reading notification details`() {
    val data =
      mapOf(
        "campaign" to "campaign ID",
        "redirectLink" to "https://pushpushgo.com/pl/blog",
        "notification" to notification,
        "actions" to
          """[{"link":"https://pushpushgo.com","action":"ACTION_PUSH","title":"Test action"},{"link":null,"action":"ACTION_PUSH","title":"Test action"}]""",
      )

    val details = PushNotifications.getNotificationDetails(data)

    assertEquals(expected, details)
  }
}
