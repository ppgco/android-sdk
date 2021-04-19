package com.pushpushgo.sdk.work

import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber

class UploadDelegate {

    suspend fun doNetworkWork(type: String?, data: String) {
        if (!PushPushGo.getInstance().isSubscribed() && type != UploadWorker.REGISTER) {
            return Timber.d("UploadWorker: skipped. Reason: not subscribed")
        }

        with(PushPushGo.getInstance().getNetwork()) {
            when (type) {
                UploadWorker.REGISTER -> registerToken(data)
                UploadWorker.UNREGISTER -> unregisterSubscriber()
                UploadWorker.EVENT -> sendEvent(data)
                UploadWorker.BEACON -> sendBeacon(data)
                else -> Timber.tag(PushPushGo.TAG).w("Unknown upload data type")
            }
        }
    }
}
