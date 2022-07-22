package com.pushpushgo.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.utils.logDebug
import com.pushpushgo.sdk.utils.logError
import kotlinx.coroutines.coroutineScope

internal class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    companion object {
        const val TYPE = "type"
        const val DATA = "data"

        const val REGISTER = "register"
        const val UNREGISTER = "unregister"
    }

    private val delegate by lazy { UploadDelegate() }

    override suspend fun doWork(): Result = coroutineScope {
        logDebug("UploadWorker: started")

        val type = inputData.getString(TYPE)
        val data = inputData.getString(DATA)

        try {
            delegate.doNetworkWork(type, data)
        } catch (e: Throwable) {
            logError("UploadWorker error", e)

            return@coroutineScope when {
                "Please configure FCM keys and senderIds on your " in e.message.orEmpty() -> Result.failure()
                type == REGISTER || type == UNREGISTER -> Result.retry()
                else -> Result.failure()
            }
        }

        logDebug("UploadWorker: success")

        Result.success()
    }
}
