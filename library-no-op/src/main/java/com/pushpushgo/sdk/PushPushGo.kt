package com.pushpushgo.sdk

import android.app.Application
import android.content.Context
import android.content.Intent
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pushpushgo.sdk.dto.PPGoNotification
import com.pushpushgo.sdk.exception.PushPushException

@Suppress("unused", "UNUSED_PARAMETER")
class PushPushGo private constructor(
    private val context: Application,
    private val apiKey: String,
    private val projectId: String,
) {

    companion object {
        const val VERSION = "NO-OP"

        private var INSTANCE: PushPushGo? = null

        fun isInitialized(): Boolean = INSTANCE != null

        @JvmStatic
        fun getInstance(): PushPushGo =
            INSTANCE ?: throw PushPushException("You have to initialize PushPushGo with context first!")

        @JvmStatic
        fun getInstance(context: Application) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: PushPushGo(context, "", "").also { INSTANCE = it }
        }

        @JvmStatic
        fun getInstance(context: Application, apiKey: String, projectId: String): PushPushGo {
            if (INSTANCE == null) {
                INSTANCE = PushPushGo(context, apiKey, projectId)
            }
            return INSTANCE as PushPushGo
        }
    }

    var defaultIsSubscribed: Boolean = false

    var notificationHandler: NotificationHandler = { _, _ -> }

    fun createBeacon(): BeaconBuilder = BeaconBuilder()


    fun getApiKey(): String = apiKey

    fun getNotificationDetails(notificationIntent: Intent?): PPGoNotification? = null

    fun getNotificationDetails(notificationData: Map<String, String>): PPGoNotification? = null

    fun getProjectId(): String = projectId

    fun getPushToken(): String = ""

    fun getPushTokenAsync(): ListenableFuture<String> = Futures.immediateFuture("null")

    fun handleBackgroundNotificationClick(intent: Intent?) = Unit

    fun isPPGoPush(notificationIntent: Intent?): Boolean = false

    fun isPPGoPush(notificationData: Map<String, String>): Boolean = false

    fun isSubscribed(): Boolean = false

    fun migrateToNewProject(newProjectId: String, newProjectToken: String): ListenableFuture<PushPushGo> =
        Futures.immediateFuture(this)

    fun registerSubscriber() = Unit

    fun resubscribe(newProjectId: String, newProjectToken: String): PushPushGo = this

    fun unregisterSubscriber() = Unit
}

typealias NotificationHandler = (context: Context, url: String) -> Unit
