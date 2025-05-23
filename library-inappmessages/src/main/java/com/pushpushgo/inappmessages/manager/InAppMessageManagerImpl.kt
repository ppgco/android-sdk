package com.pushpushgo.inappmessages.manager

import android.content.Context
import android.util.Log
import com.pushpushgo.inappmessages.model.DeviceType
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.OSType
import com.pushpushgo.inappmessages.model.TriggerType
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import com.pushpushgo.inappmessages.repository.InAppMessageRepository
import com.pushpushgo.inappmessages.utils.DeviceInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

class InAppMessageManagerImpl(
    private val repository: InAppMessageRepository,
    private val persistence: InAppMessagePersistence,
    private val context: Context,
) : InAppMessageManager, CoroutineScope {
    
    private val tag = "InAppMessageManager"
    
    // Coroutine context for this scope
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job
    
    // Schedule refresh configuration
    private var scheduleRefreshJob: Job? = null
    private val scheduleRefreshInterval = 60_000L // Check schedules every minute
    
    // Thread-safe collections for message management
    private val activeMessages = CopyOnWriteArrayList<InAppMessage>()
    private val allMessages = CopyOnWriteArrayList<InAppMessage>()
    private val triggerMap = mutableMapOf<String, MutableList<InAppMessage>>()
    
    // Device info
    private val currentDeviceType by lazy { DeviceInfoProvider.getCurrentDeviceType(context) }
    private val currentOsType = OSType.ANDROID

    override fun initialize() {
        launch {
            try {
                Log.d(tag, "Initializing InAppMessageManager")
                
                // Fetch messages in IO context
                val messages = withContext(Dispatchers.IO) {
                    repository.fetchMessages()
                }
                
                Log.d(tag, "Fetched ${messages.size} messages")
                
                // Update collections
                allMessages.clear()
                allMessages.addAll(messages)
                
                // Build trigger map and refresh active messages
                buildTriggerMap(messages)
                refreshActiveMessages()

                // Start periodic schedule checks
                startScheduleRefresh()
                
                Log.d(tag, "InAppMessageManager initialized successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error initializing InAppMessageManager: ${e.message}")
            }
        }
    }

    /**
     * Start a periodic job to refresh message schedules
     * This runs on a background thread to avoid blocking the main thread
     */
    private fun startScheduleRefresh() {
        // Cancel any existing job
        scheduleRefreshJob?.cancel()
        
        // Start a new job in the current scope to leverage its lifecycle
        scheduleRefreshJob = launch(Dispatchers.IO) {
            try {
                Log.d(tag, "Starting periodic schedule refresh every ${scheduleRefreshInterval}ms")
                
                while (true) {
                    delay(scheduleRefreshInterval)
                    
                    // Don't block on refresh, just log and continue if there's an error
                    try {
                        refreshActiveMessages()
                        Log.d(tag, "Performed periodic schedule check")
                    } catch (e: Exception) {
                        Log.e(tag, "Error during periodic schedule refresh: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Schedule refresh job failed: ${e.message}")
            }
        }
    }

    /**
     * Builds a map of trigger keys to messages for fast lookup when triggers occur
     * Only messages with CUSTOM trigger type are included in the map
     * 
     * @param messages List of all available messages
     */
    private fun buildTriggerMap(messages: List<InAppMessage>) {
        // Use synchronized to prevent concurrent modification issues
        synchronized(triggerMap) {
            triggerMap.clear()
            
            // Use functional approach with filter and groupBy when possible
            messages
                .filter { it.trigger.type == TriggerType.CUSTOM && it.trigger.key != null }
                .forEach { msg -> 
                    msg.trigger.key?.let { key ->
                        triggerMap.getOrPut(key) { mutableListOf() }.add(msg)
                    }
                }
            
            Log.d(tag, "Built trigger map with ${triggerMap.size} unique trigger keys")
        }
    }

    /**
     * Checks if a message is eligible to be shown based on cooldown state and dismissal status
     * 
     * @param msg The message to check eligibility for
     * @return true if the message is eligible to be shown, false otherwise
     */
    private suspend fun isMessageEligibleToShow(msg: InAppMessage): Boolean = withContext(Dispatchers.IO) {
        isMessageEligible(msg)
    }
    
    /**
     * Implementation of the InAppMessageManager interface method
     * Checks if a message is eligible to be shown based on cooldown state and dismissal status
     * 
     * @param message The message to check eligibility for
     * @return true if the message is eligible to be shown, false otherwise
     */
    override suspend fun isMessageEligible(message: InAppMessage): Boolean = withContext(Dispatchers.IO) {
        // Check schedule window if applicable
        if (!isInScheduleWindow(message)) {
            Log.d(tag, "Message [${message.id}] is outside schedule window and not eligible")
            return@withContext false
        }
        
        val nowMillis = System.currentTimeMillis()
        
        // Check for one-time messages (showAgain = false)
        if (!message.timeSettings.showAgain) {
            val isDismissed = persistence.isMessageDismissed(message.id)
            if (isDismissed) {
                Log.d(tag, "Message [${message.id}] is a one-time message and has been dismissed")
                return@withContext false
            }
            return@withContext true
        }
        
        // For showAgain messages, check if the cooldown period has elapsed
        val lastShownAt = persistence.getLastShownAt(message.id)
        if (lastShownAt != null) {
            val elapsed = nowMillis - lastShownAt
            val requiredCooldown = message.timeSettings.showAgainTime
            val canShowAgain = elapsed >= requiredCooldown
            
            Log.d(
                tag,
                "Message [${message.id}] lastShownAt=$lastShownAt, elapsed=${elapsed}ms, " +
                "cooldown=${requiredCooldown}ms, eligible=$canShowAgain"
            )
            
            return@withContext canShowAgain
        }
        
        // If we reach here, the message has never been shown and has no cooldown
        return@withContext true
    }

    /**
     * Refresh the list of active messages based on current conditions
     * Filters messages by eligibility, schedule, device type, OS type, and expiration
     */
    override fun refreshActiveMessages() {
        // Launch the refresh in a coroutine to avoid blocking the calling thread
        launch {
            try {
                Log.d(tag, "Refreshing active messages")
                
                // Get current time in system default zone for consistent time zone handling
                val currentTime = ZonedDateTime.now()
                
                // Run filtering in background thread
                val filteredMessages = withContext(Dispatchers.Default) {
                    allMessages.filter { msg ->
                        // Check if message is eligible (not dismissed and not in cooldown)
                        val isEligible = isMessageEligibleToShow(msg)
                        
                        // Check if message is in schedule window
                        val inScheduleWindow = isInScheduleWindow(msg)
                        
                        // Check persisted expiration state
                        val notExpired = !persistence.isMessageExpired(msg.id)
                        
                        // Also check expiration in ZonedDateTime if set
                        val notExpiredByDate = msg.expiration?.let { expiration ->
                            // Normalize expiration to same zone as current time for accurate comparison
                            val normalizedExpiration = expiration.withZoneSameInstant(currentTime.zone)
                            currentTime.isBefore(normalizedExpiration)
                        } ?: true
                        
                        // Check device and OS compatibility
                        val deviceAllowed = msg.audience.device.contains(DeviceType.ALL) || 
                                msg.audience.device.contains(currentDeviceType)
                        val osAllowed = msg.audience.os.contains(OSType.ALL) || 
                                msg.audience.os.contains(currentOsType)
                        
                        // All conditions must be met
                        val result = isEligible && inScheduleWindow && notExpired && notExpiredByDate && 
                                deviceAllowed && osAllowed
                        
                        if (result) {
                            Log.d(tag, "Message [${msg.id}] is active and eligible")
                        }
                        
                        result
                    }
                }
                
                // Update active messages list (thread-safe operation)
                synchronized(activeMessages) {
                    activeMessages.clear()
                    activeMessages.addAll(filteredMessages)
                }
                
                Log.d(tag, "Active messages refreshed, count: ${activeMessages.size}")
            } catch (e: Exception) {
                Log.e(tag, "Error refreshing active messages: ${e.message}")
            }
        }
    }

    /**
     * Trigger custom messages by key and optional value
     * Only messages with matching key (and value if specified) will be triggered
     * 
     * @param key The trigger key to match
     * @param value Optional value to match (null matches any value)
     */
    override fun trigger(key: String, value: String?) {
        // Launch in a coroutine to avoid blocking the calling thread
        launch {
            Log.d(tag, "Triggering messages with key=$key, value=$value")
            
            // Get messages that match this trigger
            val matchingMessages = synchronized(triggerMap) {
                triggerMap[key]?.filter { msg ->
                    msg.trigger.type == TriggerType.CUSTOM &&
                    msg.trigger.key == key &&
                    (msg.trigger.value == null || msg.trigger.value == value)
                } ?: emptyList()
            }
            
            if (matchingMessages.isEmpty()) {
                Log.d(tag, "No messages found for trigger key=$key")
                return@launch
            }
            
            Log.d(tag, "Found ${matchingMessages.size} matching messages for trigger key=$key")
            
            // Process each matching message
            matchingMessages.forEach { msg ->
                processTriggeredMessage(msg)
            }
        }
    }
    
    /**
     * Process a triggered message by checking eligibility and adding to active messages if appropriate
     */
    private suspend fun processTriggeredMessage(msg: InAppMessage) {
        // First check if message is in schedule window - this is absolute
        if (!isInScheduleWindow(msg)) {
            Log.d(tag, "Message [${msg.id}] matched trigger but not in schedule window")
            return
        }

        // Then check if message is eligible (not dismissed and not in cooldown)
        if (!isMessageEligibleToShow(msg)) {
            Log.d(tag, "Message [${msg.id}] matched trigger but not eligible due to dismissal/cooldown")
            return
        }

        // Add message to active list if not already present (thread-safe operation)
        synchronized(activeMessages) {
            if (!activeMessages.contains(msg)) {
                Log.d(tag, "Adding message [${msg.id}] to active messages after trigger")
                activeMessages.add(msg)
            } else {
                Log.d(tag, "Message [${msg.id}] already in active messages")
            }
        }
    }

    /**
     * Checks if a message is within its scheduled time window.
     * Schedule takes absolute precedence over all other timing conditions.
     *
     * @param msg The message to check
     * @return true if the message is within its schedule or has no schedule
     */
    private suspend fun isInScheduleWindow(msg: InAppMessage): Boolean = withContext(Dispatchers.Default) {
        val schedule = msg.schedule ?: return@withContext true // No schedule means always in window

        // Get current time in system default zone
        val currentTime = ZonedDateTime.now()

        // If there's no schedule constraints, message is always in window
        if (schedule.startTime == null && schedule.endTime == null) {
            return@withContext true
        }
        
        // Normalize time zones for accurate comparison
        val normalizedStartTime = schedule.startTime?.withZoneSameInstant(currentTime.zone)
        val normalizedEndTime = schedule.endTime?.withZoneSameInstant(currentTime.zone)

        // Check if current time is within schedule bounds
        val afterStart = normalizedStartTime == null || !currentTime.isBefore(normalizedStartTime)
        val beforeEnd = normalizedEndTime == null || currentTime.isBefore(normalizedEndTime)
        val isInWindow = afterStart && beforeEnd
        
        // Log the result for debugging
        Log.d(
            tag,
            "Schedule check for [${msg.id}]: " +
            "start=${normalizedStartTime ?: "None"}, " +
            "end=${normalizedEndTime ?: "None"}, " +
            "current=$currentTime, " +
            "inWindow=$isInWindow"
        )

        return@withContext isInWindow
    }

    /**
     * Get the current list of active messages
     * This method returns the cached list of active messages from the most recent refresh
     * For the most up-to-date list, call refreshActiveMessages() first
     * 
     * @return List of active messages that are eligible to be shown
     */
    override fun getActiveMessages(): List<InAppMessage> {
        // Simply return the current active messages list, which is already filtered
        // during refreshActiveMessages calls
        return activeMessages.toList()
    }
    
    /**
     * Clean up resources when the manager is no longer needed
     * Should be called when the app is closing or the manager is no longer needed
     */
    fun cleanup() {
        Log.d(tag, "Cleaning up InAppMessageManager resources")
        
        // Cancel all coroutines
        scheduleRefreshJob?.cancel()
        job.cancel()
        
        // Clear collections
        synchronized(activeMessages) {
            activeMessages.clear()
        }
        synchronized(allMessages) {
            allMessages.clear()
        }
        synchronized(triggerMap) {
            triggerMap.clear()
        }
        
        Log.d(tag, "InAppMessageManager resources cleaned up")
    }
}
