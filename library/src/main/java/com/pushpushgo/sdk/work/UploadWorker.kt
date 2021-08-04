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

        // migration
        const val OLD_PROJECT_ID = "old_project_id"
        const val OLD_TOKEN = "old_token"
        const val OLD_SUBSCRIBER_ID = "old_subscriber_id"

        const val REGISTER = "register"
        const val UNREGISTER = "unregister"
        const val MIGRATION = "migration"
        const val BEACON = "beacon"
        const val EVENT = "event"
    }

    private val delegate by lazy { UploadDelegate() }

    override suspend fun doWork(): Result = coroutineScope {
        Timber.tag(PushPushGo.TAG).d("UploadWorker: started")

        val type = inputData.getString(TYPE)
        val data = inputData.getString(DATA)
        val oldProjectId = inputData.getString(OLD_PROJECT_ID)
        val oldToken = inputData.getString(OLD_TOKEN)
        val oldSubscriberId = inputData.getString(OLD_SUBSCRIBER_ID)

        try {
            delegate.doNetworkWork(type, data, oldProjectId, oldToken, oldSubscriberId)
        } catch (e: Throwable) {
            Timber.tag(PushPushGo.TAG).e(e, "UploadWorker error: %s", e.message)

            return@coroutineScope when {
                "Please configure FCM keys and senderIds on your " in e.message.orEmpty() -> Result.failure()
                type == REGISTER || type == UNREGISTER || type == MIGRATION -> Result.retry()
                else -> Result.failure()
            }
        }

        Timber.tag(PushPushGo.TAG).d("UploadWorker: success")

        Result.success()
    }
}
