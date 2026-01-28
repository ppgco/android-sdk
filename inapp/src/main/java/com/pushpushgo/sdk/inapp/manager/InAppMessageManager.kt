package com.pushpushgo.sdk.inapp.manager

import com.pushpushgo.sdk.inapp.model.InAppMessage
import kotlinx.coroutines.flow.Flow

internal interface InAppMessageManager {
  val messagesFlow: Flow<List<InAppMessage>>

  suspend fun initialize()

  suspend fun trigger(
    key: String,
    value: String? = null,
  ): InAppMessage?

  fun getActiveMessages(): List<InAppMessage>

  fun getRoute(): String?

  suspend fun refreshActiveMessages(route: String?)

  suspend fun isMessageEligible(message: InAppMessage): Boolean
}
