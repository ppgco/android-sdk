package com.pushpushgo.sdk.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
        const val LINK = "link"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.tag(PushPushGo.TAG).d("ClickActionReceiver received click event")

        if (PushPushGo.isInitialized() && SharedPreferencesHelper(context).isSubscribed) {
            GlobalScope.launch {
                PushPushGo.getInstance().getNetwork().sendEvent(
                    type = EventType.CLICKED,
                    buttonId = intent?.getIntExtra(BUTTON_ID, 0) ?: 0,
                    campaign = intent?.getStringExtra(CAMPAIGN_ID).orEmpty()
                )
            }

            intent?.getStringExtra(LINK)?.let { uri ->
                Intent.parseUri(uri, 0).let {
                    if (it.resolveActivity(context.packageManager) != null) context.startActivity(it)
                    else {
                        Timber.tag(PushPushGo.TAG).e("Not found activity to open uri: %s", uri)
                        Toast.makeText(context, uri, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
