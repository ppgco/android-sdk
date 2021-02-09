package com.pushpushgo.sdk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.di.NetworkModule
import com.pushpushgo.sdk.di.WorkModule
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.push.createNotificationChannel
import com.pushpushgo.sdk.push.deserializeNotificationData
import com.pushpushgo.sdk.push.handleNotificationLinkClick
import com.pushpushgo.sdk.utils.validateApiKey
import com.pushpushgo.sdk.utils.validateProjectId
import timber.log.Timber

class PushPushGo private constructor(
    private val context: Context,
    private val apiKey: String,
    private val projectId: String
) {

    companion object {
        const val VERSION = "0.1.10-20201027~2"

        internal const val TAG = "PPGo"

        /**
         * an instance of PushPushGo library
         */
        @Volatile
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
         * @return PushPushGoFacade instance
         */
        @JvmStatic
        fun getInstance(context: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildPushPushGo(context).also { INSTANCE = it }
        }

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to handle DI
         * @param apiKey - key to communicate with RESTFul API
         * @return PushPushGoFacade instance
         */
        @JvmStatic
        fun getInstance(context: Context, apiKey: String, projectId: String): PushPushGo {
            if (INSTANCE == null) {
                INSTANCE = PushPushGo(context, apiKey, projectId)
            }
            return INSTANCE as PushPushGo
        }

        private fun buildPushPushGo(context: Context): PushPushGo {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val bundle = ai.metaData
            val apiKey = bundle.getString("com.pushpushgo.apikey")
                ?: throw PushPushException("You have to declare apiKey in Your Manifest file")
            val projectId = bundle.getString("com.pushpushgo.projectId")
                ?: throw PushPushException("You have to declare projectId in Your Manifest file")

            validateApiKey(apiKey)
            validateProjectId(projectId)

            return PushPushGo(context, apiKey, projectId)
        }
    }

    private val networkModule by lazy { NetworkModule(context, apiKey, projectId) }

    init {
        Timber.tag(TAG).d("PushPushGo $VERSION initialized (project id: $projectId)")

        createNotificationChannel(context)
        NotificationStatusChecker.start(context)
    }

    private val workModule by lazy { WorkModule(context) }

    internal fun getNetwork() = networkModule.apiRepository

    internal fun getUploadManager() = workModule.uploadManager

    /**
     * helper function to handle click on notification from background
     */
    fun handleBackgroundNotificationClick(intent: Intent?) {
        if (intent?.action != "APP_PUSH_CLICK") return

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
    fun getApiKey() = apiKey

    /**
     * function to read Your API Key from an PushPushGo library instance
     * @return API Key String
     */
    fun getProjectId() = projectId

    /**
     * function to check if user subscribed to notifications
     * @return boolean true if subscribed
     */
    fun isSubscribed(): Boolean {
        return networkModule.sharedPref.isSubscribed
    }

    /**
     * function to register subscriber
     */
    fun registerSubscriber() {
        getUploadManager().sendRegister()
    }

    /**
     * function to unregister subscriber
     */
    fun unregisterSubscriber() {
        getUploadManager().sendUnregister()
    }

    /**
     * function to start construct and send beacon
     */
    fun createBeacon(): BeaconBuilder {
        return BeaconBuilder(getUploadManager())
    }
}
