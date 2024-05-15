package com.pushpushgo.sdk.work

import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.utils.logDebug
import com.pushpushgo.sdk.utils.logError
import kotlinx.coroutines.*
import org.json.JSONObject

internal class UploadDelegate {

    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val errorHandler = CoroutineExceptionHandler { _, e -> logError(e) }

    suspend fun doNetworkWork(type: String?, data: String?) {
        if (!PushPushGo.getInstance().isSubscribed() && type != UploadWorker.REGISTER) {
            return logDebug("UploadWorker: skipped. Reason: not subscribed")
        }

        with(PushPushGo.getInstance().getNetwork()) {
            when (type) {
                UploadWorker.REGISTER -> registerToken(data)
                UploadWorker.UNREGISTER -> unregisterSubscriber(isSubscribed = false)
                else -> logDebug("Unknown upload data type")
            }
        }
    }

    fun sendEvent(type: EventType, buttonId: Int, campaign: String, projectId: String?, subscriberId: String?) {
        uploadScope.launch(errorHandler) {
            PushPushGo.getInstance().getNetwork().sendEvent(
                type = type,
                buttonId = buttonId,
                campaign = campaign,
                project = projectId,
                subscriber = subscriberId,
            )
        }
    }

    fun sendBeacon(beacon: JSONObject) {
        if (!PushPushGo.getInstance().areNotificationsEnabled()) {
            logDebug("Beacon not sent. Reason: notifications disabled")
            return
        }

        uploadScope.launch(errorHandler) {
            PushPushGo.getInstance().getNetwork().sendBeacon(beacon.toString())
        }
    }
}
