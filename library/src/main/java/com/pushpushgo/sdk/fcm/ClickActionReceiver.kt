package com.pushpushgo.sdk.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.fcm.MessagingService.Companion.NOTIFICATION_ID
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import timber.log.Timber

internal class ClickActionReceiver : BroadcastReceiver() {

    companion object {
        const val BUTTON_ID = "button_id"
        const val CAMPAIGN_ID = "campaign_id"
        const val LINK = "link"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.tag(PushPushGo.TAG).d("ClickActionReceiver received click event")

        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

        if (PushPushGo.isInitialized() && SharedPreferencesHelper(context).isSubscribed) {
            PushPushGo.getInstance().getUploadManager().sendEvent(
                type = EventType.CLICKED,
                buttonId = intent?.getIntExtra(BUTTON_ID, 0) ?: 0,
                campaign = intent?.getStringExtra(CAMPAIGN_ID).orEmpty()
            )

            intent?.getStringExtra(LINK)?.let { it ->
                handleNotificationLinkClick(context, it)
            }
        }
    }
}
