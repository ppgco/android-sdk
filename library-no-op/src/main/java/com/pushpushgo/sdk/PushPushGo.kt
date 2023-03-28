package com.pushpushgo.sdk

import android.app.Application
import android.content.Context
import android.content.Intent
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.pushpushgo.sdk.BuildConfig.DEBUG
import com.pushpushgo.sdk.dto.PPGoNotification
import com.pushpushgo.sdk.exception.PushPushException

@Suppress("unused", "UNUSED_PARAMETER")
class PushPushGo private constructor(
    private val application: Application,
    private val apiKey: String,
    private val projectId: String,
    private val isProduction: Boolean,
    private val isNetworkDebug: Boolean,
) {

    companion object {
        const val VERSION = "NO-OP"

        private var INSTANCE: PushPushGo? = null

        fun isInitialized(): Boolean = INSTANCE != null

        @JvmStatic
        fun getInstance(): PushPushGo =
            INSTANCE ?: throw PushPushException("You have to initialize PushPushGo with context first!")

        @JvmStatic
        fun getInstance(application: Application) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: PushPushGo(application, "", "", true, DEBUG).also { INSTANCE = it }
        }

        @JvmStatic
        @JvmOverloads
        fun getInstance(
            application: Application, apiKey: String, projectId: String, isProduction: Boolean, isDebug: Boolean = false,
        ): PushPushGo {
            if (INSTANCE == null) {
                INSTANCE = PushPushGo(application, apiKey, projectId, isProduction, isDebug)
            }
            return INSTANCE as PushPushGo
        }
    }

    var defaultIsSubscribed: Boolean = false

    var notificationHandler: NotificationHandler = { _, _ -> }

    var onInvalidProjectIdHandler: InvalidProjectIdHandler = { _, _, _ -> }

    fun createBeacon(): BeaconBuilder = BeaconBuilder()

    fun getApiKey(): String = apiKey

    fun getNotificationDetails(notificationIntent: Intent?): PPGoNotification? = null

    fun getNotificationDetails(notificationData: Map<String, String>): PPGoNotification? = null

    fun getProjectId(): String = projectId

    fun getSubscriberId(): String = ""

    fun getPushToken(): ListenableFuture<String> = Futures.immediateFuture("null")

    fun handleBackgroundNotificationClick(intent: Intent?) = Unit

    fun isPPGoPush(notificationIntent: Intent?): Boolean = false

    fun isPPGoPush(notificationData: Map<String, String>): Boolean = false

    fun isSubscribed(): Boolean = false

    fun migrateToNewProject(newProjectId: String, newProjectToken: String): ListenableFuture<PushPushGo> =
        Futures.immediateFuture(this)

    fun registerSubscriber() = Unit

    fun createSubscriber(): ListenableFuture<String> = Futures.immediateFuture("")

    fun unregisterSubscriber() = Unit

    fun unregisterSubscriber(projectId: String, projectToken: String, subscriberId: String): ListenableFuture<Unit> =
        Futures.immediateFuture(Unit)
}

typealias NotificationHandler = (context: Context, url: String) -> Unit

typealias InvalidProjectIdHandler = (pushProjectId: String, pushSubscriberId: String, currentProjectId: String) -> Unit
