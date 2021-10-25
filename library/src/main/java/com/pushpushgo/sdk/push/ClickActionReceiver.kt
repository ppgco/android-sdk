package com.pushpushgo.sdk.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.work.UploadDelegate
import timber.log.Timber

internal class ClickActionReceiver : BroadcastReceiver() {

    companion object {
        const val NOTIFICATION_ID = "notification_id"
        const val BUTTON_ID = "button_id"
        const val CAMPAIGN_ID = "campaign_id"
        const val PROJECT_ID = "project_id"
        const val SUBSCRIBER_ID = "subscriber_id"
        const val LINK = "link"
    }

    private val uploadDelegate by lazy { UploadDelegate() }

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.tag(PushPushGo.TAG).d("ClickActionReceiver received click event")

        val intentProjectId = intent?.getStringExtra(PROJECT_ID).orEmpty()
        val intentSubscriberId = intent?.getStringExtra(SUBSCRIBER_ID).orEmpty()
        val initializedProjectId = PushPushGo.getInstance().getProjectId()
        if (intentProjectId != initializedProjectId) {
            PushPushGo.getInstance().onInvalidProjectIdHandler(intentProjectId, intentSubscriberId, initializedProjectId)
            return
        }

        intent?.getIntExtra(NOTIFICATION_ID, 0)?.let {
            NotificationManagerCompat.from(context).cancel(it)
        }

        if (PushPushGo.isInitialized() && SharedPreferencesHelper(context).isSubscribed) {
            uploadDelegate.sendEvent(
                type = EventType.CLICKED,
                buttonId = intent?.getIntExtra(BUTTON_ID, 0) ?: 0,
                campaign = intent?.getStringExtra(CAMPAIGN_ID).orEmpty(),
                projectId = intentProjectId,
                subscriberId = intentSubscriberId,
            )

            intent?.getStringExtra(LINK)?.let { it ->
                PushPushGo.getInstance().notificationHandler(context, it)
            }
        }
    }
}
