package com.pushpushgo.sdk.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

class ClickActionReceiver : BroadcastReceiver() {

    companion object {
        const val BUTTON_ID = "button_id"
        const val CAMPAIGN_ID = "campaign_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.tag(PushPushGo.TAG).d("ClickActionReceiver received click event")

        PushPushGo.INSTANCE?.let {
            if (SharedPreferencesHelper(context).isSubscribed) {
                GlobalScope.launch {
                    it.getNetwork().sendEvent(
                        type = EventType.CLICKED,
                        buttonId = intent?.getIntExtra(BUTTON_ID, 0) ?: 0,
                        campaign = intent?.getStringExtra(CAMPAIGN_ID).orEmpty()
                    )
                }
            }
        }
    }
}
