package com.pushpushgo.sdk.work

import androidx.work.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.Event
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.Payload
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.work.UploadWorker.Companion.BEACON
import com.pushpushgo.sdk.work.UploadWorker.Companion.DATA
import com.pushpushgo.sdk.work.UploadWorker.Companion.EVENT
import com.pushpushgo.sdk.work.UploadWorker.Companion.REGISTER
import com.pushpushgo.sdk.work.UploadWorker.Companion.TYPE
import com.pushpushgo.sdk.work.UploadWorker.Companion.UNREGISTER
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal class UploadManager(private val workManager: WorkManager, private val sharedPref: SharedPreferencesHelper) {

    fun sendRegister() {
        Timber.tag(PushPushGo.TAG).d("Register enqueued")

        enqueueJob(REGISTER, isMustRunImmediately = true)
    }

    fun sendUnregister() {
        Timber.tag(PushPushGo.TAG).d("Unregister enqueued")

        enqueueJob(UNREGISTER, isMustRunImmediately = true)
    }

    fun sendEvent(type: EventType, buttonId: Int, campaign: String) {
        Timber.tag(PushPushGo.TAG).d("Event enqueued: ($type, $buttonId, $campaign)")

        enqueueJob(
            name = EVENT,
            isMustRunImmediately = true,
            data = workDataOf(
                DATA to Gson().toJson(
                    Event(
                        type = type.value,
                        payload = Payload(
                            button = buttonId,
                            campaign = campaign,
                            subscriber = sharedPref.subscriberId
                        )
                    )
                )
            )
        )
    }

    fun sendBeacon(beacon: JsonObject) {
        Timber.tag(PushPushGo.TAG).d("Beacon enqueued: $beacon")

        enqueueJob(BEACON, workDataOf(TYPE to BEACON, DATA to beacon.toString()))
    }

    private fun enqueueJob(name: String, data: Data = workDataOf(), isMustRunImmediately: Boolean = false) {
        workManager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.APPEND,
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(Data.Builder().putString(TYPE, name).putAll(data).build())
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
