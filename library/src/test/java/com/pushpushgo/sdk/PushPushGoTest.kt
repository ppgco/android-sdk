package com.pushpushgo.sdk

import android.content.Intent
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.dto.PPGoNotification
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushPushGoTest {

    private lateinit var systemUnderTest: PushPushGo

    @Before
    fun setUp() {
        systemUnderTest = PushPushGo.getInstance(
            application = getApplicationContext(),
            projectId = "hm93nzyt5bmczmtjeghy2aph",
            apiKey = "e5d706d7-0ebb-4793-9edc-6bd9eb9aff3a",
            isProduction = false,
            isDebug = true,
        )
    }

    @Test
    fun `check is intent PPGo notification`() {
        assertFalse(systemUnderTest.isPPGoPush(Intent()))
        assertTrue(systemUnderTest.isPPGoPush(Intent().putExtra("project", "")))
    }

    @Test
    fun `check is extras map PPGo notification`() {
        assertFalse(systemUnderTest.isPPGoPush(mapOf()))
        assertTrue(systemUnderTest.isPPGoPush(mapOf("project" to "")))
    }

    @Test
    fun `get notification data mapping from invalid intent`() {
        assertEquals(null, systemUnderTest.getNotificationDetails(Intent().putExtra("adsasfdafdf", "")))
    }

    @Test
    fun `get notification data mapping from intent`() {
        val dto = PPGoNotification(
            title = "Notification title",
            body = "Notification body",
            campaignId = "campaign ID",
            priority = 0,
            redirectLink = "https://pushpushgo.com/pl/blog",
        )
        assertEquals(dto, systemUnderTest.getNotificationDetails(Intent()
            .putExtra("campaign", "campaign ID")
            .putExtra("redirectLink", "https://pushpushgo.com/pl/blog")
            .putExtra("notification",
                """{"badge":1,"sound":"default","vibrate":"true","title":"Notification title","body":"Notification body","priority":0,"click_action":"APP_PUSH_CLICK"}""")
        ))
    }

    @Test
    fun `get notification data mapping from map`() {
        val dto = PPGoNotification(
            title = "Notification title",
            body = "Notification body",
            campaignId = "campaign ID",
            priority = 0,
            redirectLink = "https://pushpushgo.com/pl/blog",
        )
        assertEquals(dto, systemUnderTest.getNotificationDetails(mapOf(
            "campaign" to "campaign ID",
            "redirectLink" to "https://pushpushgo.com/pl/blog",
            "notification" to """{"badge":1,"sound":"default","vibrate":"true","title":"Notification title","body":"Notification body","priority":0,"click_action":"APP_PUSH_CLICK"}"""
        )))
    }

    @Test
    fun `get notification data mapping from invalid map`() {
        assertEquals(null, systemUnderTest.getNotificationDetails(mapOf("adsasfdafdf" to "")))
    }
}
