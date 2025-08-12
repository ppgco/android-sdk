package com.pushpushgo.inappmessages.repository

import com.pushpushgo.inappmessages.model.InAppMessage

interface InAppMessageRepository {
  suspend fun fetchMessages(): List<InAppMessage>
}
