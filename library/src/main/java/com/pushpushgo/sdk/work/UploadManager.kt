package com.pushpushgo.sdk.work

import androidx.work.*
import com.google.gson.JsonObject
import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal class UploadManager(private val workManager: WorkManager) {

    fun sendBeacon(beacon: JsonObject) {
        Timber.tag(PushPushGo.TAG).d("Beacon send enqueued: $beacon")

        enqueueJob("beacon", workDataOf(UploadWorker.BEACON to beacon.toString()))
    }

    private fun enqueueJob(name: String, data: Data, isMustRunImmediately: Boolean = false) {
        workManager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.APPEND,
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(data)
                .setInitialDelay(if (isJobAlreadyEnqueued(name) || isMustRunImmediately) 0 else 10, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()
        )
    }

    private fun isJobAlreadyEnqueued(name: String): Boolean {
        return workManager.getWorkInfosForUniqueWork(name).get().any {
            it.state != WorkInfo.State.SUCCEEDED
        }
    }
}
