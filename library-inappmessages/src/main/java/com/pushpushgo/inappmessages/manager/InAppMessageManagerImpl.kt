package com.pushpushgo.inappmessages.manager

import android.content.Context
import android.util.Log
import com.pushpushgo.inappmessages.model.DeviceType
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.OSType
import com.pushpushgo.inappmessages.model.ShowAgainType
import com.pushpushgo.inappmessages.model.TriggerType
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import com.pushpushgo.inappmessages.repository.InAppMessageRepository
import com.pushpushgo.inappmessages.utils.DeviceInfoProvider
import com.pushpushgo.inappmessages.utils.PushNotificationStatusProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

internal class InAppMessageManagerImpl(
    private val scope: CoroutineScope,
    private val repository: InAppMessageRepository,
    private val persistence: InAppMessagePersistence,
    private val context: Context,
) : InAppMessageManager {

    // Provider for accessing push notification subscription status
    private val notificationStatusProvider = PushNotificationStatusProvider(context)

    private val tag = "InAppMessageManager"

    // Schedule refresh configuration
    private var scheduleRefreshJob: Job? = null
    private val scheduleRefreshInterval = 60_000L // Check schedules every minute

    // Thread-safe collections for message management
    private val _messagesFlow = MutableStateFlow<List<InAppMessage>>(emptyList())
    override val messagesFlow: Flow<List<InAppMessage>> = _messagesFlow.asStateFlow()
    private val activeMessages = CopyOnWriteArrayList<InAppMessage>()
    private val allMessages = CopyOnWriteArrayList<InAppMessage>()
    private val triggerMap = mutableMapOf<String, MutableList<InAppMessage>>()

    @Volatile
    private var refreshJob: Job? = null
    private var currentRoute: String? = null

    // Mutex to ensure atomic updates to activeMessages from refresh and trigger operations
    private val messagesUpdateMutex = Mutex()
    private val refreshJobMutex = Mutex()

    // Device info
    private val currentDeviceType by lazy { DeviceInfoProvider.getCurrentDeviceType(context) }
    private val currentOsType = DeviceInfoProvider.getCurrentOSType()

    override suspend fun initialize() {
        try {
            Log.d(tag, "Initializing InAppMessageManager")

            // Fetch messages in IO context
            val messages = withContext(Dispatchers.IO) {
                Log.d(tag, "${repository.fetchMessages()}")
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
            if (e is CancellationException) throw e
            Log.e(tag, "Error initializing InAppMessageManager: ${e.message}", e)
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
        scheduleRefreshJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d(tag, "Starting periodic schedule refresh every ${scheduleRefreshInterval}ms")

                while (true) {
                    delay(scheduleRefreshInterval)

                    // Don't block on refresh, just log and continue if there's an error
                    try {
                        refreshActiveMessages(currentRoute)
                        Log.d(tag, "Performed periodic schedule check for route: $currentRoute")
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
        Log.d(tag, "buildTriggerMap called with ${messages.size} messages.")
        messages.forEach { msg ->
            Log.d(
                tag,
                "buildTriggerMap: Processing message id=${msg.id}, triggerType=${msg.settings.triggerType}, triggerKey=${msg.settings.key}, triggerValue=${msg.settings.value}"
            )
        }

        synchronized(triggerMap) {
            triggerMap.clear()
            val customTriggerMessages = messages
                .filter { it.settings.triggerType == TriggerType.CUSTOM && it.settings.key != null }

            Log.d(
                tag,
                "buildTriggerMap: Found ${customTriggerMessages.size} messages with CUSTOM trigger type and non-null key."
            )

            customTriggerMessages.forEach { msg ->
                // msg.settings.key is non-null here due to the filter
                val key = msg.settings.key!!
                triggerMap.getOrPut(key) { mutableListOf() }.add(msg)
                Log.d(tag, "buildTriggerMap: Added message id=${msg.id} to triggerMap for key='$key'")
            }

            val finalMapLog = triggerMap.entries.joinToString { "'${it.key}': ${it.value.map { m -> m.id }.size} msg(s)" }
            Log.d(tag, "Built trigger map. Size: ${triggerMap.size}. Contents: {$finalMapLog}")
        }
    }

    /**
     * Implementation of the InAppMessageManager interface method
     * Checks if a message is eligible to be shown based on cooldown state and dismissal status
     *
     * @param message The message to check eligibility for
     * @return true if the message is eligible to be shown, false otherwise
     */
    override suspend fun isMessageEligible(message: InAppMessage): Boolean = withContext(Dispatchers.IO) {
        // 1. Check permanent dismissal (for one-time messages)
        if (message.settings.showAgain == ShowAgainType.NEVER && persistence.isMessageDismissed(message.id)) {
            Log.d(tag, "Message [${message.id}] is a one-time message and has been permanently dismissed.")
            return@withContext false
        }

        // 2. Check schedule window (absolute check)
        if (!isInScheduleWindow(message)) {
            Log.d(tag, "Message [${message.id}] is outside its schedule window.")
            return@withContext false
        }

        // 3. Check user audience type
        if (!notificationStatusProvider.matchesAudienceType(message.audience.userType)) {
            Log.d(tag, "Message [${message.id}] does not match user audience type: ${message.audience.userType}")
            return@withContext false
        }

        val nowMillis = System.currentTimeMillis()

        // 4. 'showAfterDelay' is handled by InAppMessageDisplayerImpl.

        // 5. Check 'showAgain' (cooldown for repeatable messages)
        if (message.settings.showAgain == ShowAgainType.AFTER_TIME) {
            val lastDismissedAt = persistence.getLastDismissedAt(message.id)
            val requiredCooldownSec = message.settings.showAfterTime ?: 0L

            if (lastDismissedAt != null && requiredCooldownSec > 0) { // Check if it was ever dismissed and has a cooldown
                val elapsedSinceLastDismissal = nowMillis - lastDismissedAt
                val requiredCooldownMs = requiredCooldownSec * 1000L

                if (elapsedSinceLastDismissal < requiredCooldownMs) {
                    Log.d(
                        tag,
                        "Message [${message.id}] showAgain: cooldown not yet passed. " +
                            "${elapsedSinceLastDismissal}ms of ${requiredCooldownMs}ms since last dismissal."
                    )
                    return@withContext false // Still in cooldown since last dismissal
                }
            }
        }

        Log.d(tag, "Message [${message.id}] is eligible for display.")
        return@withContext true
    }

    /**
     * Refresh the list of active messages based on current conditions
     * Filters messages by eligibility, schedule, device type, OS type, and expiration
     */
    override suspend fun refreshActiveMessages(route: String?) {
        // If a new route is explicitly provided (on navigation), update the manager's internal state.
        if (route != null) {
            this.currentRoute = route
        }

        // The route to use for this specific refresh operation is the one passed in,
        // or the one we have stored if the call is for a generic refresh (e.g., on dismissal).
        val effectiveRoute = route ?: this.currentRoute

        val newJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d(tag, "Refreshing active messages for effective route: ${effectiveRoute ?: "ENTER"}")

                val eventBasedMessages = allMessages.filter { msg ->
                    // CUSTOM triggers are handled by the `trigger` method, not by general refresh.
                    if (msg.settings.triggerType == TriggerType.CUSTOM) {
                        return@filter false
                    }

                    val displayOnRules = msg.settings.displayOn

                    if (displayOnRules.isEmpty()) {
                        return@filter true
                    }

                    // Specific route rules exist. The message should only appear on these routes.

                    if (effectiveRoute == null) {
                        // If we're not on a specific route, don't show route-specific messages.
                        return@filter false
                    }

                    val (displayed, hidden) = displayOnRules.partition { it.display }
                    val isDisplayed = displayed.any { it.path == effectiveRoute }
                    val isHidden = hidden.any { it.path == effectiveRoute }

                    if (displayed.isEmpty() && !isHidden) {
                        return@filter true
                    }

                    if (isDisplayed && !isHidden) {
                        return@filter true
                    }

                    false
                }

                val initiallyFiltered = eventBasedMessages.filter { msg ->
                    val enabled = msg.enabled
                    val notExpired = msg.expiration == null || ZonedDateTime.now().isBefore(msg.expiration)
                    val correctDeviceType =
                        msg.audience.device.contains(currentDeviceType) || msg.audience.device.contains(DeviceType.ALL)
                    val correctOsType = msg.audience.osType.contains(currentOsType) || msg.audience.osType.contains(OSType.ALL)
                    enabled && notExpired && correctDeviceType && correctOsType
                }

                val finalEligibleMessages = mutableListOf<InAppMessage>()
                for (msg in initiallyFiltered) {
                    if (msg.settings.showAfterDelay > 0 && persistence.getFirstEligibleAt(msg.id) == null) {
                        Log.d(tag, "Message [${msg.id}] with showAfterDelay. Setting firstEligibleAt during refresh.")
                        persistence.setFirstEligibleAt(msg.id, System.currentTimeMillis())
                    }

                    if (isInScheduleWindow(msg) && isMessageEligible(msg)) {
                        finalEligibleMessages.add(msg)
                    }
                }

                messagesUpdateMutex.withLock {
                    val newActiveMessages = finalEligibleMessages.sortedByDescending { it.priority }

                    activeMessages.clear()
                    activeMessages.addAll(newActiveMessages)
                    _messagesFlow.value = activeMessages.toList()

                    Log.d(
                        tag,
                        "Active messages refreshed. New count: ${activeMessages.size}. Messages: ${activeMessages.joinToString { it.id }}"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d(tag, "Refresh for route '${effectiveRoute ?: "ENTER"}' was cancelled.")
                } else {
                    Log.e(tag, "Error refreshing active messages for route: ${effectiveRoute ?: "ENTER"}", e)
                }
            }
        }

        refreshJobMutex.withLock {
            refreshJob?.cancel()
            refreshJob = newJob
        }
    }

    /**
     * Trigger custom messages by key and optional value
     * Only messages with matching key (and value if specified) will be triggered
     *
     * @param key The trigger key to match
     * @param value Optional value to match (null matches any value)
     */
    override suspend fun trigger(key: String, value: String?): InAppMessage? {
        val jobToWaitFor = refreshJobMutex.withLock { refreshJob }
        jobToWaitFor?.join()

        Log.d(tag, "Attempting to trigger custom message with key=$key, paramValue=${value}")

        val potentialMessages = synchronized(triggerMap) {
            triggerMap[key]?.filter { msg ->
                val typeMatch = msg.settings.triggerType == TriggerType.CUSTOM
                val keyMatch = msg.settings.key == key
                val valueMatch = (value == null || msg.settings.value == value)
                typeMatch && keyMatch && valueMatch
            } ?: emptyList()
        }

        if (potentialMessages.isEmpty()) {
            Log.d(tag, "No messages found for trigger key=$key (paramValue=${value}).")
            return null
        }

        Log.d(
            tag,
            "Found ${potentialMessages.size} potential messages for trigger key=$key (paramValue=${value}). Checking eligibility..."
        )

        for (msg in potentialMessages.sortedByDescending { it.priority }) { // Check higher priority first
            if (isInScheduleWindow(msg) && isMessageEligible(msg)) {
                // If the message has a showAfterDelay and its firstEligibleAt is not set, set it now.
                if (msg.settings.showAfterDelay > 0 && persistence.getFirstEligibleAt(msg.id) == null) {
                    Log.d(tag, "Trigger for message [${msg.id}] with showAfterDelay. Setting firstEligibleAt.")
                    persistence.setFirstEligibleAt(msg.id, System.currentTimeMillis())
                }
                Log.d(tag, "Message [${msg.id}] is eligible for custom trigger '$key'. Returning for display.")
                return msg
            }
        }

        Log.d(tag, "No eligible message found for custom trigger '$key' with value '$value' after checking all candidates.")
        return null
    }


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
        return activeMessages.toList()
    }
}
