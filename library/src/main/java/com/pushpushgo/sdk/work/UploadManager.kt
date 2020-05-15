package com.pushpushgo.sdk.work

import androidx.work.*
import com.google.gson.JsonObject
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.work.UploadWorker.Companion.BEACON
import com.pushpushgo.sdk.work.UploadWorker.Companion.BEACON_DATA
import com.pushpushgo.sdk.work.UploadWorker.Companion.EVENT
import com.pushpushgo.sdk.work.UploadWorker.Companion.EVENT_BUTTON_ID
import com.pushpushgo.sdk.work.UploadWorker.Companion.EVENT_CAMPAIGN
import com.pushpushgo.sdk.work.UploadWorker.Companion.EVENT_TYPE
import com.pushpushgo.sdk.work.UploadWorker.Companion.REGISTER
import com.pushpushgo.sdk.work.UploadWorker.Companion.TYPE
import com.pushpushgo.sdk.work.UploadWorker.Companion.UNREGISTER
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal class UploadManager(private val workManager: WorkManager) {

    fun sendRegister() {
        Timber.tag(PushPushGo.TAG).d("Register enqueued")

        enqueueJob(REGISTER, workDataOf(TYPE to REGISTER), true)
    }

    fun sendUnregister() {
        Timber.tag(PushPushGo.TAG).d("Unregister enqueued")

        enqueueJob(UNREGISTER, workDataOf(TYPE to UNREGISTER), true)
    }

    fun sendEvent(type: EventType, buttonId: Int, campaign: String) {
        Timber.tag(PushPushGo.TAG).d("Event enqueued: ($type, $buttonId, $campaign)")

        enqueueJob(
            name = EVENT,
            isMustRunImmediately = true,
            data = workDataOf(
                TYPE to EVENT,
                EVENT_TYPE to type.value,
                EVENT_BUTTON_ID to buttonId,
                EVENT_CAMPAIGN to campaign
            )
        )
    }

    fun sendBeacon(beacon: JsonObject) {
        Timber.tag(PushPushGo.TAG).d("Beacon enqueued: $beacon")

        enqueueJob(BEACON, workDataOf(TYPE to BEACON, BEACON_DATA to beacon.toString()))
    }

    private fun enqueueJob(name: String, data: Data, isMustRunImmediately: Boolean = false) {
        workManager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.APPEND,
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
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
            it.state == WorkInfo.State.ENQUEUED
        }
    }
}
