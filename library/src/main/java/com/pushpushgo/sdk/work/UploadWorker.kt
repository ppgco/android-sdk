package com.pushpushgo.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.work.UploadManager.Companion.UPLOAD_RETRY_ATTEMPT
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.IOException

internal class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    companion object {
        const val TYPE = "type"
        const val DATA = "data"
        const val RETRY_LIMIT = "retry_limit"

        const val REGISTER = "register"
        const val UNREGISTER = "unregister"
        const val BEACON = "beacon"
        const val EVENT = "event"
    }

    override suspend fun doWork(): Result = coroutineScope {
        Timber.tag(PushPushGo.TAG).d("UploadWorker: started")

        try {
            val type = inputData.getString(TYPE)
            if (PushPushGo.getInstance().isSubscribed() || type == REGISTER) {
                doNetworkWork(type)
            } else Timber.d("UploadWorker: skipped. Reason: not subscribed")
        } catch (e: IOException) {
            Timber.tag(PushPushGo.TAG).e(e, "UploadWorker: error %s", e.message)
            return@coroutineScope if (inputData.getBoolean(RETRY_LIMIT, false) && runAttemptCount > UPLOAD_RETRY_ATTEMPT) {
                Result.failure()
            } else Result.retry()
        } catch (e: Throwable) {
            Timber.tag(PushPushGo.TAG).e(e)
            return@coroutineScope Result.failure()
        }

        Timber.tag(PushPushGo.TAG).d("UploadWorker: success")

        Result.success()
    }

    private suspend fun doNetworkWork(type: String?) {
        val data = inputData.getString(DATA).orEmpty()

        with(PushPushGo.getInstance().getNetwork()) {
            when (type) {
                REGISTER -> registerToken(data)
                UNREGISTER -> unregisterSubscriber()
                EVENT -> sendEvent(data)
                BEACON -> sendBeacon(data)
                else -> Timber.tag(PushPushGo.TAG).w("Unknown upload data type")
            }
        }
    }
}
