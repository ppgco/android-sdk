package com.pushpushgo.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.JsonParser
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.PushPushGo.Companion.getInstance
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

internal class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    companion object {
        const val BEACON = "beacon"
    }

    override suspend fun doWork(): Result = coroutineScope {
        Timber.tag(PushPushGo.TAG).d("UploadWorker: started")

        try {
            val beacon = JsonParser.parseString(inputData.getString(BEACON)).asJsonObject
            getInstance().getNetwork().sendBeacon(beacon)
        } catch (e: Throwable) {
            Timber.tag(PushPushGo.TAG).e("UploadWorker: Unknown exception %s", e.message)
            return@coroutineScope Result.retry()
        }

        Timber.tag(PushPushGo.TAG).d("UploadWorker: success")

        Result.success()
    }
}
