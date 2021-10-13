package com.pushpushgo.sdk.work

import androidx.work.*
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.work.UploadWorker.Companion.DATA
import com.pushpushgo.sdk.work.UploadWorker.Companion.REGISTER
import com.pushpushgo.sdk.work.UploadWorker.Companion.TYPE
import com.pushpushgo.sdk.work.UploadWorker.Companion.UNREGISTER
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal class UploadManager(
    private val workManager: WorkManager,
    private val sharedPref: SharedPreferencesHelper,
) {

    companion object {
        const val UPLOAD_DELAY = 10L
        const val UPLOAD_RETRY_DELAY = 30L
    }

    fun sendRegister(token: String?) {
        Timber.tag(PushPushGo.TAG).d("Register enqueued")

        enqueueJob(REGISTER, isMustRunImmediately = true, data = token)
        listOf(UNREGISTER).forEach {
            workManager.cancelAllWorkByTag(it)
        }
    }

    fun sendUnregister() {
        if (!sharedPref.isSubscribed) {
            Timber.tag(PushPushGo.TAG).d("Can't unregister, because device not registered. Skipping")
            return
        }

        Timber.tag(PushPushGo.TAG).d("Unregister enqueued")

        enqueueJob(UNREGISTER, isMustRunImmediately = true)
        listOf(REGISTER).forEach {
            workManager.cancelAllWorkByTag(it)
        }
    }

    private fun enqueueJob(name: String, data: String? = null, isMustRunImmediately: Boolean = false) {
        workManager.enqueueUniqueWork(
            name,
            if (name == REGISTER || name == UNREGISTER) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND,
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(TYPE to name, DATA to data)
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, UPLOAD_RETRY_DELAY, TimeUnit.SECONDS)
                .setInitialDelay(if (isMustRunImmediately || isJobAlreadyEnqueued(name)) 0 else UPLOAD_DELAY, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()
        )
    }

    private fun isJobAlreadyEnqueued(name: String) = try {
        workManager.getWorkInfosForUniqueWork(name).get().any {
            it.state == WorkInfo.State.ENQUEUED
        }
    } catch (e: InterruptedException) {
        false
    }
}
