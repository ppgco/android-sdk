package com.pushpushgo.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class PushPushGo private constructor(
    private val context: Context,
    private val apiKey: String,
    private val projectId: String
) {

    companion object {
        const val VERSION = "1.1.0-20210804~1"

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

    private val networkModule by lazy { NetworkModule(context, apiKey, projectId) }

    init {
        val platformType = getPlatformType()
        val startupMessage = "PushPushGo $VERSION initialized (project id: $projectId, platform: $platformType)"
        println(startupMessage)
        Timber.tag(TAG).d(startupMessage)

        createNotificationChannel(context)
        NotificationStatusChecker.start(context)
    }

    private val workModule by lazy { WorkModule(context) }

    internal fun getNetwork() = networkModule.apiRepository

    internal fun getUploadManager() = workModule.uploadManager

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
        handleNotificationLinkClick(context, notify.redirectLink)
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
    fun isSubscribed(): Boolean {
        return networkModule.sharedPref.isSubscribed
    }

    /**
     * function to retrieve last push token used to subscribe
     */
    fun getPushToken(): String = runBlocking {
        networkModule.sharedPref.lastToken.takeIf { it.isNotEmpty() } ?: getPlatformPushToken(context)
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
     * function to re-subscribe to new project (previously unregistering from previous project)
     */
    fun resubscribe(newProjectId: String, token: String): PushPushGo {
        val oldProjectId = projectId
        val oldToken = apiKey
        val oldSubscriberId = networkModule.sharedPref.subscriberId

        val newInstance = reinitialize(
            context = context,
            projectId = newProjectId,
            apiKey = token
        )

        newInstance.getUploadManager().sendMigration(
            oldProjectId = oldProjectId,
            oldToken = oldToken,
            oldSubscriberId = oldSubscriberId,
        )

        return newInstance
    }

    /**
     * function to construct and send beacon
     */
    fun createBeacon(): BeaconBuilder {
        return BeaconBuilder(getUploadManager())
    }
}
