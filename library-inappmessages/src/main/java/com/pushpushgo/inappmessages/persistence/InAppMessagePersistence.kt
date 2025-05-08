package com.pushpushgo.inappmessages.persistence

interface InAppMessagePersistence {
    fun isMessageDismissed(messageId: String): Boolean
    fun markMessageDismissed(messageId: String)
    fun isMessageExpired(messageId: String): Boolean
    fun markMessageExpired(messageId: String)
}
