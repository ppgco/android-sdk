package com.pushpushgo.sdk

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.di.NetworkModule
import com.pushpushgo.sdk.di.WorkModule
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.fcm.deserializeNotificationData
import com.pushpushgo.sdk.fcm.handleNotificationLinkClick
import com.pushpushgo.sdk.utils.TimberChuckerErrorTree
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

class PushPushGo private constructor(
    private val application: Application,
    private val apiKey: String,
    private val projectId: String
) {

    companion object {
        internal const val TAG = "PPGo"

        /**
         * an instance of PushPushGo library
         */
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
        fun getInstance(context: Context): PushPushGo {
            if (INSTANCE == null) {
                val ai = context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA
                )
                val bundle = ai.metaData
                val apiKey = bundle.getString("com.pushpushgo.apikey")
                    ?: throw PushPushException("You have to declare apiKey in Your Manifest file")
                val projectId = bundle.getString("com.pushpushgo.projectId")
                    ?: throw PushPushException("You have to declare projectId in Your Manifest file")
                INSTANCE = PushPushGo(context.applicationContext as Application, apiKey, projectId)
            }
            return INSTANCE as PushPushGo
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
                INSTANCE = PushPushGo(context.applicationContext as Application, apiKey, projectId)
            }
            return INSTANCE as PushPushGo
        }
    }

    private val networkModule by lazy { NetworkModule(application, apiKey, projectId) }

    init {
        Timber.tag(TAG).d("PushPushGo initialized (project id: $projectId)")

        if (BuildConfig.DEBUG) Timber.plant(TimberChuckerErrorTree(networkModule.chuckerCollector))

        NotificationStatusChecker.start(application)
    }

    private val workModule by lazy { WorkModule(application) }

    internal fun getNetwork() = networkModule.apiRepository

    internal fun getUploadManager() = workModule.uploadManager

    /**
     * helper function to handle click on notification from background
     */
    fun handleBackgroundNotificationClick(intent: Intent?) {
        if (intent?.action != "APP_PUSH_CLICK") return

        val notify = deserializeNotificationData(intent.extras)
        handleNotificationLinkClick(application, notify.redirectLink)
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
        GlobalScope.launch {
            getNetwork().registerToken()
        }
    }

    /**
     * function to unregister subscriber
     */
    fun unregisterSubscriber() {
        GlobalScope.launch {
            getNetwork().unregisterSubscriber()
        }
    }

    /**
     * function to start construct and send beacon
     */
    fun createBeacon(): BeaconBuilder {
        return BeaconBuilder(getUploadManager())
    }
}
