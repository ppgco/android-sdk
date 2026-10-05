package com.pushpushgo.sdk.inapp.persistence

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class InAppMessagePersistenceImplTest {
  private val context: Context = getApplicationContext()

  @Test
  fun `message cache belongs to its project`() {
    val projectA = InAppMessagePersistenceImpl(context, projectId = "project-a")
    val projectB = InAppMessagePersistenceImpl(context, projectId = "project-b")

    projectA.saveCache("etag-a", emptyList())

    assertEquals("etag-a", projectA.getStoredETag())
    assertNotNull(projectA.getCachedMessages())
    assertNull(projectB.getStoredETag())
    assertNull("Offline fallback must not serve another project's messages", projectB.getCachedMessages())
  }

  @Test
  fun `dismissed message stays dismissed after a project switch`() {
    InAppMessagePersistenceImpl(context, projectId = "project-a").markMessageDismissed("message-1")

    assertTrue(InAppMessagePersistenceImpl(context, projectId = "project-b").isMessageDismissed("message-1"))
  }

  @Test
  fun `cache saved without a project is dropped`() {
    val prefs = context.getSharedPreferences("in_app_messages_prefs", Context.MODE_PRIVATE)
    prefs.edit {
      putString("etag", "legacy-etag")
      putString("cached_messages", "[]")
      putLong("cache_timestamp", System.currentTimeMillis())
    }

    val persistence = InAppMessagePersistenceImpl(context, projectId = "project-a")

    assertNull(persistence.getCachedMessages())
    assertFalse(prefs.contains("cached_messages"))
    assertFalse(prefs.contains("etag"))
  }
}
