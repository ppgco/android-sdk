package com.pushpushgo.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.PushPushGo.Companion.getInstance
import com.pushpushgo.sdk.work.UploadManager.Companion.UPLOAD_RETRY_ATTEMPT
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.IOException

internal class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    companion object {
        const val TYPE = "type"
        const val DATA = "data"

        const val REGISTER = "register"
        const val UNREGISTER = "unregister"
        const val BEACON = "beacon"
        const val EVENT = "event"
    }

    override suspend fun doWork(): Result = coroutineScope {
        Timber.tag(PushPushGo.TAG).d("UploadWorker: started")

        try {
            val data = inputData.getString(DATA).orEmpty()

            when (inputData.getString(TYPE)) {
                REGISTER -> getInstance().getNetwork().registerToken()
                UNREGISTER -> getInstance().getNetwork().unregisterSubscriber()
                EVENT -> getInstance().getNetwork().sendEvent(data)
                BEACON -> getInstance().getNetwork().sendBeacon(data)
                else -> Timber.tag(PushPushGo.TAG).w("Unknown upload data type")
            }
        } catch (e: IOException) {
            Timber.tag(PushPushGo.TAG).e(e, "UploadWorker: error %s", e.message)
            return@coroutineScope if (runAttemptCount > UPLOAD_RETRY_ATTEMPT) {
                Result.failure()
            } else Result.retry()
        }

        Timber.tag(PushPushGo.TAG).d("UploadWorker: success")

        Result.success()
    }
}
