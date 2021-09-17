package com.pushpushgo.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.common.util.concurrent.ListenableFuture
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.mapToDto
import com.pushpushgo.sdk.di.NetworkModule
import com.pushpushgo.sdk.di.WorkModule
import com.pushpushgo.sdk.dto.PPGoNotification
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.push.createNotificationChannel
import com.pushpushgo.sdk.push.deserializeNotificationData
import com.pushpushgo.sdk.push.handleNotificationLinkClick
import com.pushpushgo.sdk.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking

class PushPushGo private constructor(
    private val context: Context,
    private val apiKey: String,
    private val projectId: String,
) {

    companion object {
        const val VERSION = "1.2.0-20210917~1"

        internal const val TAG = "PPGo"

        /**
         * an instance of PushPushGo library
         */
        @Volatile
        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: PushPushGo? = null

        fun isInitialized(): Boolean {
            return INSTANCE != null
        }

        @JvmStatic
        fun getInstance(): PushPushGo =
            INSTANCE ?: throw PushPushException("You have to initialize PushPushGo with context first!")

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to get apiKey from META DATA stored in Your Manifest.xml file
         * @return PushPushGo instance
         */
        @JvmStatic
        fun getInstance(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildPushPushGoFromContext(context).also { INSTANCE = it }
        }

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to handle DI
         * @param apiKey - key to communicate with RESTFul API
         * @return PushPushGo instance
         */
        @JvmStatic
        fun getInstance(context: Context, apiKey: String, projectId: String): PushPushGo {
            if (INSTANCE == null) {
                INSTANCE = PushPushGo(context, apiKey, projectId)
            }
            return INSTANCE as PushPushGo
        }

        @JvmStatic
        internal fun reinitialize(context: Context, apiKey: String, projectId: String): PushPushGo {
            INSTANCE = PushPushGo(context, apiKey, projectId)

            return INSTANCE as PushPushGo
        }

        private fun buildPushPushGoFromContext(context: Context): PushPushGo {
            val (projectId, apiKey) = extractCredentialsFromContext(context)
            validateCredentials(projectId, apiKey)
            return PushPushGo(context, apiKey, projectId)
        }

        private fun extractCredentialsFromContext(context: Context): Pair<String, String> {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
            )
            val bundle = ai.metaData
            val apiKey = bundle.getString("com.pushpushgo.apikey")
                ?: throw PushPushException("You have to declare apiKey in Your Manifest file")
            val projectId = bundle.getString("com.pushpushgo.projectId")
                ?: throw PushPushException("You have to declare projectId in Your Manifest file")

            return projectId to apiKey
        }

        private fun validateCredentials(projectId: String, apiKey: String) {
            validateApiKey(apiKey)
            validateProjectId(projectId)
        }
    }

    init {
        val platformType = getPlatformType()
        val startupMessage = "PushPushGo $VERSION initialized (project id: $projectId, platform: $platformType)"
        println(startupMessage)

        createNotificationChannel(context)
        NotificationStatusChecker.start(context)
    }

    private val networkModule by lazy { NetworkModule(context, apiKey, projectId) }

    private val workModule by lazy { WorkModule(context) }

    internal fun getNetwork() = networkModule.apiRepository

    internal fun getUploadManager() = workModule.uploadManager

    /**
     * Settings used for migration to support switch user before start first time app after upgrade/switch
     * defaultIsSubscribed  default is false
     */
    var defaultIsSubscribed: Boolean = false

    var notificationHandler: NotificationHandler = { context, url -> handleNotificationLinkClick(context, url) }

    /**
     * function to check whether the given notification data belongs to the PPGo sender
     *
     * @param notificationIntent - pending intent of clicked notification
     *
     * @return boolean
     */
    fun isPPGoPush(notificationIntent: Intent?): Boolean = notificationIntent?.getStringExtra("project") == projectId

    /**
     * function to check whether the given notification data belongs to the PPGo sender
     *
     * @param notificationData - data field of received notification
     *
     * @return boolean
     */
    fun isPPGoPush(notificationData: Map<String, String>): Boolean = notificationData["project"] == projectId

    /**
     * function to retrieve PPGo notification details
     *
     * @param notificationIntent - pending intent of clicked notification
     *
     * @return Notification
     */
    fun getNotificationDetails(notificationIntent: Intent?): PPGoNotification? =
        deserializeNotificationData(notificationIntent?.extras)?.mapToDto()

    /**
     * function to retrieve PPGo notification details
     *
     * @param notificationData - data field of received notification
     *
     * @return Notification
     */
    fun getNotificationDetails(notificationData: Map<String, String>): PPGoNotification? =
        deserializeNotificationData(notificationData.mapToBundle())?.mapToDto()

    /**
     * helper function to handle click on notification from background
     */
    fun handleBackgroundNotificationClick(intent: Intent?) {
        if (intent?.getStringExtra("project") != projectId) return

        val notify = deserializeNotificationData(intent.extras) ?: return
        notificationHandler(context, notify.redirectLink)
        getUploadManager().sendEvent(
            type = EventType.CLICKED,
            buttonId = 0,
            campaign = notify.campaignId
        )
    }

    /**
     * function to read Your API Key from an PushPushGo library instance
     * @return API Key String
     */
    fun getApiKey(): String = apiKey

    /**
     * function to read Your API Key from an PushPushGo library instance
     * @return API Key String
     */
    fun getProjectId(): String = projectId

    /**
     * function to check if user subscribed to notifications
     * @return boolean true if subscribed
     */
    fun isSubscribed(): Boolean = networkModule.sharedPref.isSubscribed

    /**
     * function to retrieve last push token used to subscribe
     */
    @Deprecated("use getPushTokenAsync() instead")
    fun getPushToken(): String = runBlocking { getPushTokenSuspend() }

    /**
     * async function to retrieve last push token used to subscribe that
     */
    fun getPushTokenAsync(): ListenableFuture<String> = CoroutineScope(Job() + Dispatchers.IO).future {
        getPushTokenSuspend()
    }

    private suspend fun getPushTokenSuspend(): String {
        return networkModule.sharedPref.lastToken.takeIf { it.isNotEmpty() } ?: getPlatformPushToken(context)
    }

    /**
     * function to register subscriber
     */
    fun registerSubscriber() {
        getUploadManager().sendRegister(null)
    }

    /**
     * function to unregister subscriber
     */
    fun unregisterSubscriber() {
        getUploadManager().sendUnregister()
    }

    /**
     * function to re-subscribe to different project (previously unsubscribe from current project)
     * WARNING: after resubscribe use object returned by this function instead of previous one
     *
     * @param newProjectId - project id to which we are switching
     * @param newProjectToken - project token
     */
    fun migrateToNewProject(newProjectId: String, newProjectToken: String): ListenableFuture<PushPushGo> {
        return CoroutineScope(Job() + Dispatchers.IO).future {
            getInstance().getNetwork().migrateSubscriber(
                newProjectId = newProjectId,
                newToken = newProjectToken,
            )
            reinitialize(
                context = context,
                projectId = newProjectId,
                apiKey = newProjectToken
            )
        }
    }

    @Deprecated("use migrateToNewProject instead")
    fun resubscribe(newProjectId: String, newProjectToken: String): PushPushGo {
        runBlocking {
            getInstance().getNetwork().migrateSubscriber(
                newProjectId = newProjectId,
                newToken = newProjectToken,
            )
        }

        return reinitialize(
            context = context,
            projectId = newProjectId,
            apiKey = newProjectToken
        )
    }

    /**
     * function to construct and send beacon
     */
    fun createBeacon(): BeaconBuilder = BeaconBuilder(getUploadManager())
}

typealias NotificationHandler = (context: Context, url: String) -> Unit
