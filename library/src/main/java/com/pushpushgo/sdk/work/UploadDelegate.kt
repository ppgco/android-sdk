package com.pushpushgo.sdk.work

import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber

internal class UploadDelegate {

    suspend fun doNetworkWork(
        type: String?, data: String?,
        oldProjectId: String? = null, oldToken: String? = null, oldSubscriberId: String? = null
    ) {
        if (!PushPushGo.getInstance().isSubscribed() && type != UploadWorker.REGISTER) {
            return Timber.d("UploadWorker: skipped. Reason: not subscribed")
        }

        with(PushPushGo.getInstance().getNetwork()) {
            when (type) {
                UploadWorker.REGISTER -> registerToken(data)
                UploadWorker.UNREGISTER -> unregisterSubscriber()
                UploadWorker.MIGRATION -> migrateSubscriber(oldProjectId, oldToken, oldSubscriberId)
                UploadWorker.EVENT -> sendEvent(data.orEmpty())
                UploadWorker.BEACON -> sendBeacon(data.orEmpty())
                else -> Timber.tag(PushPushGo.TAG).w("Unknown upload data type")
            }
        }
    }
}
