package com.pushpushgo.inappmessages

import android.app.Activity
import android.app.Application
import com.pushpushgo.inappmessages.manager.InAppMessageManager
import com.pushpushgo.inappmessages.manager.InAppMessageManagerImpl
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistenceImpl
import com.pushpushgo.inappmessages.repository.InAppMessageRepositoryImpl
import com.pushpushgo.inappmessages.ui.InAppMessageDisplayer
import com.pushpushgo.inappmessages.ui.InAppMessageDisplayerImpl
import com.pushpushgo.inappmessages.model.TriggerType

class InAppMessagesSDK private constructor(
    private val application: Application,
    private val projectId: String,
    private val apiKey: String,
    private val baseUrl: String? = null,
) {
    private var manager: InAppMessageManager? = null
    private var displayer: InAppMessageDisplayer? = null

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
                INSTANCE ?: InAppMessagesSDK(application, projectId, apiKey, baseUrl).also { INSTANCE = it }
            }
        }

        @JvmStatic
        fun getInstance(): InAppMessagesSDK =
            INSTANCE ?: throw IllegalStateException("InAppMessagesSDK is not initialized!")
    }

    init {
        val repository = InAppMessageRepositoryImpl(application, "in_app_messages.json")
        val persistence = InAppMessagePersistenceImpl(application)
        manager = InAppMessageManagerImpl(repository, persistence, application)
        manager?.initialize()
        this.displayer = displayer ?: InAppMessageDisplayerImpl(persistence)
    }

    /**
     * Shows all in-app messages that should be displayed automatically:
     * - If currentRoute is null: shows all messages with trigger.type == APP_OPEN
     * - If currentRoute is not null: shows all messages with trigger.type == ROUTE and trigger.route == currentRoute, and all with trigger.type == APP_OPEN
     *
     * Call this once on app start (with currentRoute = null),
     * and on route/view change (with currentRoute = route name).
     */
    fun showActiveMessages(activity: Activity, currentRoute: String? = null) {
        // Cancel any pending delayed messages when route/activity changes
        displayer?.cancelPendingMessages()
        
        manager?.let {
            val messages = it.getActiveMessages().filter { msg ->
                (msg.trigger.type == TriggerType.APP_OPEN) ||
                (currentRoute != null && msg.trigger.type == TriggerType.ROUTE && msg.trigger.route == currentRoute)
            }
            messages.forEach { message ->
                displayer?.showMessage(activity, message)
            }
        }
    }

    /**
     * Shows all in-app messages for a custom trigger.
     * Only messages with trigger.type == CUSTOM and matching key (and value, if provided) will be shown.
     */
    fun showMessagesOnTrigger(activity: Activity, key: String, value: String? = null) {
        // Cancel any pending delayed messages when trigger changes
        displayer?.cancelPendingMessages()
        
        manager?.let {
            val messages = it.getActiveMessages().filter { msg ->
                msg.trigger.type == TriggerType.CUSTOM &&
                msg.trigger.key == key &&
                (value == null || msg.trigger.value == value)
            }
            messages.forEach { message ->
                displayer?.showMessage(activity, message)
            }
        }
    }
}


