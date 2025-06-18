package com.pushpushgo.inappmessages

import android.app.Activity
import android.app.Application
import android.util.Log
import com.pushpushgo.inappmessages.manager.InAppMessageManager
import com.pushpushgo.inappmessages.manager.InAppMessageManagerImpl
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistenceImpl
import com.pushpushgo.inappmessages.repository.InAppMessageRepositoryImpl
import com.pushpushgo.inappmessages.ui.InAppMessageDisplayer
import com.pushpushgo.inappmessages.ui.InAppMessageDisplayerImpl
import com.pushpushgo.inappmessages.utils.AutoCleanupManager
import com.pushpushgo.inappmessages.ui.InAppUIController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class InAppMessagesSDK private constructor(
    private val application: Application,
    private val projectId: String,
    private val apiKey: String,
    private val baseUrl: String? = null,
) {
    private val tag = "InAppMessagesSDK"
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager: InAppMessageManager
    private val displayer: InAppMessageDisplayer
    private val uiController: InAppUIController
    private var autoCleanupManager: AutoCleanupManager? = null

    companion object {
        @Volatile
        private var INSTANCE: InAppMessagesSDK? = null

        @JvmStatic
        fun initialize(
            application: Application,
            projectId: String,
            apiKey: String,
            baseUrl: String? = null,
        ): InAppMessagesSDK {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InAppMessagesSDK(application, projectId, apiKey, baseUrl).also { 
                    INSTANCE = it
                }
            }
        }

        @JvmStatic
        fun getInstance(): InAppMessagesSDK =
            INSTANCE ?: throw IllegalStateException("InAppMessagesSDK is not initialized!")
    }

    init {
        val repository = InAppMessageRepositoryImpl(application, "in_app_messages.json")
        val persistence = InAppMessagePersistenceImpl(application)
        manager = InAppMessageManagerImpl(sdkScope, repository, persistence, application)
        displayer = InAppMessageDisplayerImpl(persistence, onMessageDismissed = {
            showActiveMessages()
        })
        uiController = InAppUIController(application, manager, displayer)

        sdkScope.launch {
            manager.initialize()
        }
        uiController.start()

        autoCleanupManager = AutoCleanupManager(
            application = application,
            cleanupCallback = { cleanup() }
        )
        autoCleanupManager?.start()

        Log.d(tag, "InAppMessagesSDK initialized with automatic background cleanup")
    }
    
    /**
     * Cleans up resources used by the SDK
     * This is called automatically after app is in background for a prolonged period,
     * but can also be called manually from app's onDestroy()
     */
    private fun cleanup() {
        Log.d(tag, "Cleaning up InAppMessagesSDK resources")
        
        // Stop the auto-cleanup manager
        autoCleanupManager?.stop()
        autoCleanupManager = null
        
        uiController.stop()
        displayer.cancelPendingMessages()
        sdkScope.cancel()
        
        Log.d(tag, "InAppMessagesSDK resources cleaned up")
    }

    /**
     * Shows all in-app messages that should be displayed automatically:
     * - If currentRoute is null: shows all messages with trigger.type == APP_OPEN
     * - If currentRoute is not null: shows all messages with trigger.type == ROUTE and trigger.route == currentRoute, and all with trigger.type == APP_OPEN
     *
     * Call this once on app start (with currentRoute = null),
     * and on route/view change (with currentRoute = route name).
     */
    fun showActiveMessages(currentRoute: String? = null) {
        Log.d(tag, "Request to show active messages for route: ${currentRoute ?: "APP_OPEN"}")
        sdkScope.launch {
            manager.refreshActiveMessages(currentRoute)
        }
    }

    /**
     * Shows in-app messages for a custom trigger.
     * Only messages with trigger.type == CUSTOM and matching key (and value, if provided) will be shown.
     * Also doesn't cancel pending messages for APP_OPEN trigger.
     */
    fun showMessagesOnTrigger(key: String, value: String? = null) {
        Log.d(tag, "Request to show messages for custom trigger: $key")
        sdkScope.launch {
            val messageToShow = manager.trigger(key, value)
            if (messageToShow != null) {
                uiController.displayCustomMessage(messageToShow)
            }
        }
    }
}
