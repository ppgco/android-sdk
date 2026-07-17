package com.pushpushgo.sdk.push.work

import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.utils.logDebug

internal class UploadDelegate(
  private val apiRepository: ApiRepository,
) {
  suspend fun syncToken(token: String?) {
    if (PushNotifications.getSubscriberId() == null) {
      return logDebug("UploadWorker: skipped. Reason: not subscribed")
    }

    apiRepository.updateSubscriberToken(token)
  }

  /**
   * Performs the actual event upload. Invoked from [UploadWorker] (an EVENT job),
   * so it runs durably with retry/backoff rather than as a fire-and-forget
   * coroutine. The subscriber is resolved from the payload when present, falling
   * back to the locally stored id inside [ApiRepository.sendEvent].
   */
  suspend fun sendEvent(
    type: EventType,
    buttonId: Int,
    campaign: String,
    projectId: String?,
    subscriberId: String?,
  ) {
    apiRepository.sendEvent(
      type = type,
      buttonId = buttonId,
      campaign = campaign,
      project = projectId,
      subscriber = subscriberId,
    )
  }
}
