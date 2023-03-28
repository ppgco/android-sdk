package com.pushpushgo.sdk

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.common.util.concurrent.ListenableFuture
import com.pushpushgo.sdk.BuildConfig.DEBUG
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.mapToDto
import com.pushpushgo.sdk.di.NetworkModule
import com.pushpushgo.sdk.di.WorkModule
import com.pushpushgo.sdk.dto.PPGoNotification
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.push.*
import com.pushpushgo.sdk.utils.*
import com.pushpushgo.sdk.work.UploadDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.future

class PushPushGo private constructor(
    private val application: Application,
    private val apiKey: String,
    private val projectId: String,
    private val isProduction: Boolean,
    internal val isDebug: Boolean,
) {

    companion object {
        const val VERSION = "2.0.0-20230328~2"

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
         * @param application - an application to get apiKey from META DATA stored in Your Manifest.xml file
         * @return PushPushGo instance
         */
        @JvmStatic
        fun getInstance(application: Application) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildPushPushGoFromContext(application).also { INSTANCE = it }
        }

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param application - an application to handle DI
         * @param apiKey - key to communicate with RESTFul API
         * @return PushPushGo instance
         */
        @JvmStatic
        @JvmOverloads
        fun getInstance(
            application: Application, apiKey: String, projectId: String, isProduction: Boolean, isDebug: Boolean = DEBUG,
        ): PushPushGo {
            if (INSTANCE == null) {
                INSTANCE = createPushPushGoInstance(application, apiKey, projectId, isProduction, isDebug)
            }
            return INSTANCE as PushPushGo
        }

        @JvmStatic
        private fun reinitialize(
            application: Application, apiKey: String, projectId: String, isProduction: Boolean, isDebug: Boolean,
        ): PushPushGo {
            INSTANCE = createPushPushGoInstance(application, apiKey, projectId, isProduction, isDebug)

            return INSTANCE as PushPushGo
        }

        private fun buildPushPushGoFromContext(application: Application): PushPushGo {
            val (projectId, apiKey) = extractCredentialsFromContext(application)

            return createPushPushGoInstance(application, apiKey, projectId, isProduction = true, DEBUG)
        }

        @Suppress("DEPRECATION")
        private fun extractCredentialsFromContext(context: Context): Pair<String, String> {
            val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else context.packageManager.getApplicationInfo(
                context.packageName, PackageManager.GET_META_DATA
            )

            val bundle = ai.metaData
            val apiKey = bundle.getString("com.pushpushgo.apikey")
                ?: throw PushPushException("You have to declare apiKey in Your Manifest file")
            val projectId = bundle.getString("com.pushpushgo.projectId")
                ?: throw PushPushException("You have to declare projectId in Your Manifest file")

            return projectId to apiKey
        }

        private fun createPushPushGoInstance(
            app: Application, key: String, project: String, isProduction: Boolean, isDebug: Boolean,
        ): PushPushGo {
            validateCredentials(project, key)
            return PushPushGo(app, key, project, isProduction, isDebug)
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

        createNotificationChannel(application)
        NotificationStatusChecker.start(application)
    }

    private val networkModule by lazy {
        NetworkModule(
            context = application,
            apiKey = apiKey,
            projectId = projectId,
            isProduction = isProduction,
            isDebug = isDebug,
        )
    }

    private val workModule by lazy { WorkModule(application) }

    private val uploadDelegate by lazy { UploadDelegate() }

    internal fun getNetwork() = networkModule.apiRepository

    internal fun getUploadManager() = workModule.uploadManager

    var onInvalidProjectIdHandler: InvalidProjectIdHandler = { pushProjectId, _, currentProjectId ->
        logDebug("Project ID inconsistency detected! Project ID from push is $pushProjectId while SDK is configured with $currentProjectId")
    }

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
    fun isPPGoPush(notificationIntent: Intent?): Boolean = notificationIntent?.hasExtra("project") == true

    /**
     * function to check whether the given notification data belongs to the PPGo sender
     *
     * @param notificationData - data field of received notification
     *
     * @return boolean
     */
    fun isPPGoPush(notificationData: Map<String, String>): Boolean = notificationData.containsKey("project")

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
        if (intent?.hasExtra(PushNotificationDelegate.PROJECT_ID_EXTRA) != true) return

        val intentProjectId = intent.getStringExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)
        val intentSubscriberId = intent.getStringExtra(PushNotificationDelegate.SUBSCRIBER_ID_EXTRA).orEmpty()
        val intentButtonId = intent.getIntExtra(PushNotificationDelegate.BUTTON_ID_EXTRA, 0)
        val intentLink = intent.getStringExtra(PushNotificationDelegate.LINK_EXTRA).orEmpty()
        val intentCampaignId = intent.getStringExtra(PushNotificationDelegate.CAMPAIGN_ID_EXTRA).orEmpty()
        val intentNotificationId = intent.getIntExtra(PushNotificationDelegate.NOTIFICATION_ID_EXTRA, 0)

        if (intentProjectId != getInstance().projectId) {
            return onInvalidProjectIdHandler(intentProjectId.orEmpty(), intentSubscriberId, getInstance().projectId)
        }

        NotificationManagerCompat.from(application).cancel(intentNotificationId)

        //TODO Remove duplicated code
        val notify = deserializeNotificationData(intent.extras)
        notificationHandler(application, notify?.redirectLink ?: intentLink)
        intent.removeExtra(PushNotificationDelegate.PROJECT_ID_EXTRA)

        uploadDelegate.sendEvent(
            type = EventType.CLICKED,
            buttonId = intentButtonId,
            projectId = notify?.project ?: intentProjectId,
            subscriberId = notify?.subscriber ?: intentSubscriberId,
            campaign = notify?.campaignId ?: intentCampaignId,
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
     * function to read Your subscriber id from an PushPushGo library instance
     * @return subscriber id String
     */
    fun getSubscriberId(): String = networkModule.sharedPref.subscriberId

    /**
     * function to check if user subscribed to notifications
     * @return boolean true if subscribed
     */
    fun isSubscribed(): Boolean = networkModule.sharedPref.isSubscribed

    /**
     * function to retrieve last push token used to subscribe that
     */
    fun getPushToken(): ListenableFuture<String> {
        return CoroutineScope(Job() + Dispatchers.IO).future {
            networkModule.sharedPref.lastToken.takeIf { it.isNotEmpty() } ?: getPlatformPushToken(application)
        }
    }

    /**
     * function to register subscriber
     */
    fun registerSubscriber() {
        if (!areNotificationsEnabled(application)) {
            return logError("Notifications disabled! Subscriber registration canceled")
        }

        getUploadManager().sendRegister(null)
    }

    /**
     * function to register subscriber and returns future with subscriber id
     *
     * @return string subscriber ID
     */
    fun createSubscriber(): ListenableFuture<String> {
        return CoroutineScope(Job() + Dispatchers.IO).future {
            check(areNotificationsEnabled(application)) {
                "Notifications disabled! Subscriber registration canceled"
            }

            getNetwork().registerToken(null)
            getSubscriberId()
        }
    }

    /**
     * function to unregister subscriber
     */
    fun unregisterSubscriber() {
        getUploadManager().sendUnregister()
    }

    fun unregisterSubscriber(projectId: String, projectToken: String, subscriberId: String): ListenableFuture<Unit> {
        return CoroutineScope(Job() + Dispatchers.IO).future {
            getInstance().getNetwork().unregisterSubscriber(
                projectId = projectId,
                token = projectToken,
                subscriberId = subscriberId,
            )
        }
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
            check(areNotificationsEnabled(application)) {
                "Notifications disabled! Subscriber registration canceled"
            }

            getInstance().getNetwork().migrateSubscriber(
                newProjectId = newProjectId,
                newToken = newProjectToken,
            )
            reinitialize(
                application = application,
                projectId = newProjectId,
                apiKey = newProjectToken,
                isProduction = isProduction,
                isDebug = isDebug,
            ).apply {
                notificationHandler = this@PushPushGo.notificationHandler
                onInvalidProjectIdHandler = this@PushPushGo.onInvalidProjectIdHandler
            }
        }
    }

    /**
     * function to construct and send beacon
     */
    fun createBeacon(): BeaconBuilder = BeaconBuilder(uploadDelegate)
}

typealias NotificationHandler = (context: Context, url: String) -> Unit

typealias InvalidProjectIdHandler = (pushProjectId: String, pushSubscriberId: String, currentProjectId: String) -> Unit
