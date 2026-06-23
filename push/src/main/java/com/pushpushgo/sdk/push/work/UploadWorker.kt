package com.pushpushgo.sdk.push.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.coroutineScope

internal class UploadWorker(
  context: Context,
  parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
  companion object {
    const val TYPE = "type"
    const val DATA = "data"

    const val REGISTER = "register"
    const val UNREGISTER = "unregister"
    const val EVENT = "event"

    // EVENT payload keys
    const val EVENT_TYPE = "event_type"
    const val EVENT_BUTTON_ID = "event_button_id"
    const val EVENT_CAMPAIGN = "event_campaign"
    const val EVENT_PROJECT_ID = "event_project_id"
    const val EVENT_SUBSCRIBER_ID = "event_subscriber_id"
  }

  private val delegate = PushNotifications.getInstance().uploadDelegate

  override suspend fun doWork(): Result =
    coroutineScope {
      logDebug("UploadWorker: started")

      val type = inputData.getString(TYPE)

      try {
        when (type) {
          EVENT ->
            delegate.sendEvent(
              type = EventType.valueOf(inputData.getString(EVENT_TYPE).orEmpty()),
              buttonId = inputData.getInt(EVENT_BUTTON_ID, 0),
              campaign = inputData.getString(EVENT_CAMPAIGN).orEmpty(),
              projectId = inputData.getString(EVENT_PROJECT_ID),
              subscriberId = inputData.getString(EVENT_SUBSCRIBER_ID),
            )

          else -> delegate.doNetworkWork(type, inputData.getString(DATA))
        }
      } catch (e: Throwable) {
        logError("UploadWorker error", e)

        return@coroutineScope when {
          "Please configure FCM keys and senderIds on your " in e.message.orEmpty() -> Result.failure()
          type == REGISTER || type == UNREGISTER || type == EVENT -> Result.retry()
          else -> Result.failure()
        }
      }

      logDebug("UploadWorker: success")

      Result.success()
    }
}
