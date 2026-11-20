package com.pushpushgo.sdk.push.liveactivity

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LiveActivityDismissReceiverTest {
  @Test
  fun `dismiss broadcast resolves to the receiver class`() {
    val context = getApplicationContext<Context>()
    val intent = Intent(LiveActivityHandler.ACTION_DISMISS).setPackage(context.packageName)

    val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)

    assertEquals(listOf(LiveActivityDismissReceiver::class.java.name), receivers.map { it.activityInfo.name })
  }
}
