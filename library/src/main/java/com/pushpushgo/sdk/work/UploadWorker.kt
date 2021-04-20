package com.pushpushgo.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.PushPushGo
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

internal class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    companion object {
        const val TYPE = "type"
        const val DATA = "data"

        const val REGISTER = "register"
        const val UNREGISTER = "unregister"
        const val BEACON = "beacon"
        const val EVENT = "event"
    }

    private val delegate by lazy { UploadDelegate() }

    override suspend fun doWork(): Result = coroutineScope {
        Timber.tag(PushPushGo.TAG).d("UploadWorker: started")

        val type = inputData.getString(TYPE)

        try {
            delegate.doNetworkWork(type, inputData.getString(DATA).orEmpty())
        } catch (e: Throwable) {
            Timber.tag(PushPushGo.TAG).e(e, "UploadWorker: error %s", e.message)
            return@coroutineScope when (type) {
                REGISTER, UNREGISTER -> Result.retry()
                else -> Result.failure()
            }
        }

        Timber.tag(PushPushGo.TAG).d("UploadWorker: success")

        Result.success()
    }
}
