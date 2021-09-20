package com.pushpushgo.sdk.dto

data class PPGoNotification(
    val title: String?,
    val body: String?,
    val priority: Int = 0,
    val campaignId: String,
    val redirectLink: String?,
)
