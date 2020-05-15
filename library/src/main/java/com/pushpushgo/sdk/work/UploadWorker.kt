package com.pushpushgo.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.PushPushGo.Companion.getInstance
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.IOException

internal class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    companion object {
        const val TYPE = "type"

        const val REGISTER = "register"

        const val UNREGISTER = "unregister"

        const val BEACON = "beacon"
        const val BEACON_DATA = "data"

        const val EVENT = "event"
        const val EVENT_TYPE = "event_type"
        const val EVENT_BUTTON_ID = "event_button_id"
        const val EVENT_CAMPAIGN = "event_campaign"
    }

    override suspend fun doWork(): Result = coroutineScope {
        Timber.tag(PushPushGo.TAG).d("UploadWorker: started")

        try {
            when (inputData.getString(TYPE)) {
                REGISTER -> getInstance().getNetwork().registerToken()
                UNREGISTER -> getInstance().getNetwork().unregisterSubscriber()
                EVENT -> {
                    getInstance().getNetwork().sendEvent(
                        type = inputData.getString(EVENT_TYPE)!!,
                        buttonId = inputData.getInt(EVENT_BUTTON_ID, 0),
                        campaign = inputData.getString(EVENT_CAMPAIGN)!!
                    )
                }
                BEACON -> {
                    getInstance().getNetwork().sendBeacon(
                        beacon = inputData.getString(BEACON_DATA).orEmpty()
                    )
                }
                else -> Timber.tag(PushPushGo.TAG).w("Unknown upload data type")
            }
        } catch (e: IOException) {
            Timber.tag(PushPushGo.TAG).e(e, "UploadWorker: error %s", e.message)
            return@coroutineScope Result.retry()
        }

        Timber.tag(PushPushGo.TAG).d("UploadWorker: success")

        Result.success()
    }
}
