package com.pushpushgo.sdk.push.work

import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class UploadDelegate(
  private val apiRepository: ApiRepository,
) {
  private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  private val errorHandler = CoroutineExceptionHandler { _, e -> logError(e) }

  suspend fun doNetworkWork(
    type: String?,
    data: String?,
  ) {
    if (PushNotifications.getInstance().getSubscriberId() == null && type != UploadWorker.REGISTER) {
      return logDebug("UploadWorker: skipped. Reason: not subscribed")
    }

    when (type) {
      UploadWorker.REGISTER -> apiRepository.registerToken(data)
      UploadWorker.UNREGISTER -> apiRepository.unregisterSubscriber()
      else -> logDebug("Unknown upload data type")
    }
  }

  fun sendEvent(
    type: EventType,
    buttonId: Int,
    campaign: String,
    projectId: String?,
    subscriberId: String?,
  ) {
    uploadScope.launch(errorHandler) {
      apiRepository.sendEvent(
        type = type,
        buttonId = buttonId,
        campaign = campaign,
        project = projectId,
        subscriber = subscriberId,
      )
    }
  }

  fun sendBeacon(beacon: JSONObject) {
    if (PushNotifications.getInstance().getSubscriberId() == null) {
      logDebug("Beacon not sent. Reason: not subscribed")
      return
    }

    uploadScope.launch(errorHandler) {
      apiRepository.sendBeacon(beacon.toString())
    }
  }
}
