package com.pushpushgo.sdk.inapp.manager

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.inapp.model.InAppMessage
import com.pushpushgo.sdk.inapp.persistence.InAppMessagePersistence
import com.pushpushgo.sdk.inapp.repository.InAppMessageRepository
import com.pushpushgo.sdk.inapp.utils.ZonedDateTimeAdapter
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

// Runs on Robolectric for the device type, read from the resources of a phone.
@RunWith(AndroidJUnit4::class)
internal class InAppMessageManagerImplTest {
  private val adapter =
    Moshi
      .Builder()
      .add(ZonedDateTimeAdapter.FACTORY)
      .build()
      .adapter(InAppMessage::class.java)

  // Never advanced, so the periodic refresh the manager starts doesn't run.
  private val managerScope = CoroutineScope(StandardTestDispatcher())

  private val repository = mockk<InAppMessageRepository>()

  private val persistence =
    mockk<InAppMessagePersistence>(relaxed = true) {
      every { isMessageDismissed(any()) } returns false
      every { getLastDismissedAt(any()) } returns null
      every { getFirstEligibleAt(any()) } returns null
    }

  @After
  fun tearDown() {
    managerScope.cancel()
  }

  @Test
  fun `a triggered message is shown only within its from-to window`() =
    runTest {
      val now = ZonedDateTime.now()
      val manager =
        managerWith(
          message("past", to = now.minusDays(1)),
          message("future", from = now.plusDays(1)),
          message("now", from = now.minusHours(1), to = now.plusHours(1)),
          message("started", from = now.minusHours(1)),
          message("open"),
        )

      assertNull(manager.trigger("past", null))
      assertNull(manager.trigger("future", null))
      assertEquals("now", manager.trigger("now", null)?.id)
      assertEquals("started", manager.trigger("started", null)?.id)
      assertEquals("open", manager.trigger("open", null)?.id)
    }

  @Test
  fun `a triggered message is shown only on the device, OS and platform it targets`() =
    runTest {
      val manager =
        managerWith(
          message("ios", osTypes = "\"IOS\""),
          message("desktop", devices = "\"DESKTOP\""),
          message("tablet", devices = "\"TABLET\""),
          message("web", platform = "WEB"),
          message("android phone", osTypes = "\"ANDROID\"", devices = "\"MOBILE\"", platform = "MOBILE"),
          message("everyone"),
        )

      assertNull(manager.trigger("ios", null))
      assertNull(manager.trigger("desktop", null))
      assertNull(manager.trigger("tablet", null))
      assertNull(manager.trigger("web", null))
      assertEquals("android phone", manager.trigger("android phone", null)?.id)
      assertEquals("everyone", manager.trigger("everyone", null)?.id)
    }

  private suspend fun managerWith(vararg messages: InAppMessage): InAppMessageManagerImpl {
    coEvery { repository.fetchMessages() } returns messages.toList()
    return InAppMessageManagerImpl(managerScope, repository, persistence, getApplicationContext()).apply { initialize() }
  }

  /** A message shown on the custom trigger [key], which is also its id. */
  private fun message(
    key: String,
    from: ZonedDateTime? = null,
    to: ZonedDateTime? = null,
    devices: String = "\"ALL\"",
    osTypes: String = "\"ALL\"",
    platform: String = "ALL",
  ): InAppMessage {
    fun date(value: ZonedDateTime?) = value?.let { "\"${it.toInstant()}\"" } ?: "null"
    return requireNotNull(
      adapter.fromJson(
        """
        {
          "id":"$key","name":"QA $key","project":"66b3700ec595a4fe7a8864ab","enabled":true,
          "createdAt":"2026-11-19T10:18:19.966Z","updatedAt":"2026-11-19T10:19:44.119Z","deletedAt":null,
          "from":${date(from)},"to":${date(to)},
          "layout":{"placement":"CENTER","margin":"0px","padding":"0px","paddingBody":"48px 24px 48px 24px",
            "spaceBetweenImageAndBody":0,"spaceBetweenContentAndActions":16,"spaceBetweenTitleAndDescription":16},
          "style":{"backgroundColor":"#ffffff","borderRadius":"0px","border":false,"borderColor":"#ffffff","borderWidth":0,
            "fontFamily":"Roboto","fontUrl":"https://fonts.googleapis.com/css2?family=Roboto","closeIcon":true,
            "closeIconColor":"#1A202C","closeIconWidth":16,"zIndex":1,"animation":false,"animationType":"none",
            "dropShadow":true,"overlay":false},
          "template":"WEBSITE_TO_HOME_SCREEN",
          "title":{"text":"QA $key","fontSize":24,"color":"#1A202C","fontWeight":500,"alignment":"center","style":"normal"},
          "description":{"text":"QA","fontSize":16,"color":"#1A202C","fontWeight":400,"alignment":"center","style":"normal"},
          "image":null,
          "actions":[{"enabled":true,"actionType":"CLOSE","url":"","target":"_self","text":"Close","fontSize":16,
            "fontWeight":500,"style":"normal","textColor":"#ffffff","backgroundColor":"#2E7CF6","borderColor":"#2E7CF6",
            "borderRadius":"4px","padding":"5px","call":""}],
          "audience":{"platform":"$platform","userType":"ALL","device":[$devices],"userAgent":["ALL"],"osType":[$osTypes]},
          "settings":{"triggerType":"CUSTOM_TRIGGER","scrollDepth":0,"showAfterDelay":0,"display":"ALL","displayOn":[],
            "showAgain":"AFTER_TIME","showAfterTime":0,"customTriggerKey":"$key","customTriggerValue":null,"priority":0}
        }
        """.trimIndent(),
      ),
    )
  }
}
