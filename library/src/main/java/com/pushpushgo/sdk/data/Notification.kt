package com.pushpushgo.sdk.data

import com.google.gson.annotations.SerializedName

internal data class PushPushNotification(

    @SerializedName("campaignId")
    val campaignId: String,

    @SerializedName("notification")
    val notification: Notification,

    @SerializedName("actions")
    val actions: List<Action>,

    @SerializedName("icon")
    val icon: String,

    @SerializedName("image")
    val image: String,

    @SerializedName("redirectLink")
    val redirectLink: String
)

internal data class Notification(

    @SerializedName("badge")
    val badge: Int = 0,

    @SerializedName("sound")
    val sound: String?,

    @SerializedName("vibrate")
    val vibrate: Boolean = true,

    @SerializedName("title")
    val title: String?,

    @SerializedName("body")
    val body: String?,

    @SerializedName("priority")
    val priority: Int = 0,

    @SerializedName("click_action")
    val click_action: String?
)

internal data class Action(

    @SerializedName("link")
    val link: String,

    @SerializedName("action")
    val action: String,

    @SerializedName("title")
    val title: String
)
