package com.pushpushgo.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.PushPushGo.Companion.getInstance
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.IOException

internal class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    companion object {
        const val TYPE = "type"

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
                EVENT -> {
                    val type = inputData.getString(EVENT_TYPE)!!
                    val buttonId = inputData.getInt(EVENT_BUTTON_ID, 0)
                    val campaign = inputData.getString(EVENT_CAMPAIGN)!!
                    getInstance().getNetwork().sendEvent(type, buttonId, campaign)
                }
                BEACON -> {
                    val data = inputData.getString(BEACON_DATA)
                    val beacon = JsonParser.parseString(data).asJsonObject
                    getInstance().getNetwork().sendBeacon(beacon)
                }
                else -> Timber.tag(PushPushGo.TAG).w("Unknown upload data type")
            }
        } catch (e: IOException) {
            Timber.tag(PushPushGo.TAG).e(e,"UploadWorker: error %s", e.message)
            return@coroutineScope Result.retry()
        }

        Timber.tag(PushPushGo.TAG).d("UploadWorker: success")

        Result.success()
    }
}
