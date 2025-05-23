package com.pushpushgo.inappmessages.manager

import com.pushpushgo.inappmessages.model.InAppMessage

interface InAppMessageManager {
    fun initialize()
    fun trigger(key: String, value: String? = null)
    fun getActiveMessages(): List<InAppMessage>
    fun refreshActiveMessages()
    suspend fun isMessageEligible(message: InAppMessage): Boolean
}
