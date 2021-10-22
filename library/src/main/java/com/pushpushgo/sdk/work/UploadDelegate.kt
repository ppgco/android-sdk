package com.pushpushgo.sdk.work

import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import kotlinx.coroutines.*
import org.json.JSONObject
import timber.log.Timber

internal class UploadDelegate {

    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val errorHandler = CoroutineExceptionHandler { _, e -> Timber.e(e) }

    suspend fun doNetworkWork(type: String?, data: String?) {
        if (!PushPushGo.getInstance().isSubscribed() && type != UploadWorker.REGISTER) {
            return Timber.d("UploadWorker: skipped. Reason: not subscribed")
        }

        with(PushPushGo.getInstance().getNetwork()) {
            when (type) {
                UploadWorker.REGISTER -> registerToken(data)
                UploadWorker.UNREGISTER -> unregisterSubscriber()
                else -> Timber.tag(PushPushGo.TAG).w("Unknown upload data type")
            }
        }
    }

    fun sendEvent(type: EventType, buttonId: Int, campaign: String, projectId: String?, subscriberId: String?) {
        uploadScope.launch(errorHandler) {
            PushPushGo.getInstance().getNetwork().sendEvent(type, buttonId, campaign, projectId, subscriberId)
        }
    }

    fun sendBeacon(beacon: JSONObject) {
        uploadScope.launch(errorHandler) {
            PushPushGo.getInstance().getNetwork().sendBeacon(beacon.toString())
        }
    }
}
