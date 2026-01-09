package com.pushpushgo.sdk.inapp.repository

import com.pushpushgo.sdk.inapp.model.InAppMessage

internal interface InAppMessageRepository {
  suspend fun fetchMessages(): List<InAppMessage>
}
