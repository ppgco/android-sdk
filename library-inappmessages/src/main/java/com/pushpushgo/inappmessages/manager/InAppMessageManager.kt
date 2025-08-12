package com.pushpushgo.inappmessages.manager

import com.pushpushgo.inappmessages.model.InAppMessage
import kotlinx.coroutines.flow.Flow

interface InAppMessageManager {
  val messagesFlow: Flow<List<InAppMessage>>

  suspend fun initialize()

  suspend fun trigger(
    key: String,
    value: String? = null,
  ): InAppMessage?

  fun getActiveMessages(): List<InAppMessage>

  suspend fun refreshActiveMessages(route: String? = null)

  suspend fun isMessageEligible(message: InAppMessage): Boolean
}
