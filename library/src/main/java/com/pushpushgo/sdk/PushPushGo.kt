package com.pushpushgo.sdk

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.iid.FirebaseInstanceId
import com.pushpushgo.sdk.di.NetworkModule
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.fcm.PushPushGoMessagingListener
import com.pushpushgo.sdk.utils.NotLoggingTree
import com.pushpushgo.sdk.utils.deviceToken
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

class PushPushGo private constructor(
    private val application: Application,
    private val apiKey: String,
    private val projectId: String
) {

    companion object {
        internal const val TAG = "_PushPushGoSDKProvider_"

        /**
         * an instance of PushPushGo library
         */
        internal var INSTANCE: PushPushGo? = null

        fun getInstance(): PushPushGo =
            INSTANCE ?: throw PushPushException("You have to initialize PushPushGo with context first!")

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to get apiKey from META DATA stored in Your Manifest.xml file
         * @return PushPushGoFacade instance
         */
        @kotlin.jvm.JvmStatic
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
        @kotlin.jvm.JvmStatic
        fun getInstance(context: Context, apiKey: String, projectId: String): PushPushGo {
            if (INSTANCE == null) {
                INSTANCE = PushPushGo(context.applicationContext as Application, apiKey, projectId)
            }
            return INSTANCE as PushPushGo
        }
    }

    private val network = NetworkModule(application, apiKey, projectId)

    internal fun getNetwork() = network.apiRepository

    private var pushPushGoMessagingListener: PushPushGoMessagingListener? = null

    init {
        if (BuildConfig.DEBUG)
            Timber.plant(Timber.DebugTree())
        else
            Timber.plant(NotLoggingTree())
        Timber.tag(TAG).d("Register API Key: $apiKey")
        Timber.tag(TAG).d("Register ProjectId Key: $projectId")
        checkNotifications()
    }

    private fun checkNotifications() {
        Timer().scheduleAtFixedRate(ForegroundTaskChecker(application, InternalTimerTask()), Date(), 10000)
    }

    /**
     * function to register a listener and handle RemoteMessage from push notifications
     * @param listener - implementation of PushPushGoMessagingListener
     */
    fun registerListener(listener: PushPushGoMessagingListener) {
        pushPushGoMessagingListener = listener
        Timber.tag(TAG).d("Registered PushPushGoMessagingListener")
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
     * function to get Your API MessageListener from an PushPushGo library instance
     * @return PushPushGoMessagingListener listener implementation
     * @throws PushPushException if listener is not set
     */
    @Throws(PushPushException::class)
    fun getListener() = pushPushGoMessagingListener

    /**
     * function to register subscriber
     */
    fun registerSubscriber() {
        GlobalScope.launch {
//            val token = getDefaultSharedPreferences(application).getString(LAST_TOKEN, "").orEmpty()
            network.apiRepository.registerToken(FirebaseInstanceId.getInstance().deviceToken)
        }
    }
}
