package com.pushpushgo.inappmessages.manager

import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.utils.DeviceInfoProvider
import android.content.Context
import com.pushpushgo.inappmessages.model.DeviceType
import com.pushpushgo.inappmessages.model.OSType
import com.pushpushgo.inappmessages.model.TriggerType
import com.pushpushgo.inappmessages.persistence.InAppMessagePersistence
import com.pushpushgo.inappmessages.repository.InAppMessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

class InAppMessageManagerImpl(
    private val repository: InAppMessageRepository,
    private val persistence: InAppMessagePersistence,
    private val context: Context,
) : InAppMessageManager {
    private var scheduleRefreshJob: Job? = null
    private val scheduleRefreshInterval = 60_000L // Check schedules every minute
    private val activeMessages = CopyOnWriteArrayList<InAppMessage>()
    private val allMessages = CopyOnWriteArrayList<InAppMessage>()
    private val triggerMap = mutableMapOf<String, MutableList<InAppMessage>>()

    override fun initialize() {
        CoroutineScope(Dispatchers.Main).launch {
            val messages = repository.fetchMessages()
            allMessages.clear()
            allMessages.addAll(messages)
            buildTriggerMap(messages)
            refreshActiveMessages()

            // Start periodic schedule checks
            startScheduleRefresh()
        }
    }

    private fun startScheduleRefresh() {
        scheduleRefreshJob?.cancel()
        scheduleRefreshJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(scheduleRefreshInterval)
                // Check if any messages need to be updated due to schedule changes
                refreshActiveMessages()
                android.util.Log.d("InAppMsgManager", "Performed periodic schedule check")
            }
        }
    }

    private fun buildTriggerMap(messages: List<InAppMessage>) {
        triggerMap.clear()
        for (msg in messages) {
            if (msg.trigger.type == TriggerType.CUSTOM) {
                msg.trigger.key?.let { key ->
                    triggerMap.getOrPut(key) { mutableListOf() }.add(msg)
                }
            }
        }
    }

    /**
     * Checks if a message is eligible to be shown based on cooldown state and dismissal status
     */
    private fun isMessageEligibleToShow(msg: InAppMessage): Boolean {
        val nowMillis = System.currentTimeMillis()

        // For one-time messages, check if dismissed
        if (!msg.timeSettings.showAgain && persistence.isMessageDismissed(msg.id)) {
            return false
        }

        // For showAgain messages, check cooldown period
        if (msg.timeSettings.showAgain) {
            val lastShownAt = persistence.getLastShownAt(msg.id)
            if (lastShownAt != null) {
                val elapsed = nowMillis - lastShownAt
                val canShowAgain = elapsed >= msg.timeSettings.showAgainTime

                android.util.Log.d(
                    "InAppMsgManager",
                    "isMessageEligibleToShow: [${msg.id}] lastShownAt=$lastShownAt elapsed=$elapsed showAgainTime=${msg.timeSettings.showAgainTime} canShowAgain=$canShowAgain"
                )

                if (!canShowAgain) {
                    return false // Still in cooldown
                }
            }
        }

        return true
    }

    override fun refreshActiveMessages() {
        // Get current time in system default zone for consistent time zone handling
        val currentTime = ZonedDateTime.now()
        activeMessages.clear()
        val deviceType = DeviceInfoProvider.getCurrentDeviceType(context)
        val osType = DeviceInfoProvider.getCurrentOSType()

        activeMessages.addAll(
            allMessages.filter { msg ->
                // First check schedule boundaries - this is the absolute constraint
                if (!isInScheduleWindow(msg)) {
                    return@filter false
                }

                // Second, check cooldown and dismissal status
                val isEligible = isMessageEligibleToShow(msg)

                // Other standard filters
                val notExpired = !persistence.isMessageExpired(msg.id)
                // Normalize expiration to same time zone as current time for proper comparison
                val notExpiredByDate = msg.expiration?.let {
                    val normalizedExpiration = it.withZoneSameInstant(currentTime.zone)
                    currentTime.isBefore(normalizedExpiration)
                } ?: true
                val deviceAllowed = msg.audience.device.contains(DeviceType.ALL) || msg.audience.device.contains(deviceType)
                val osAllowed = msg.audience.os.contains(OSType.ALL) || msg.audience.os.contains(osType)

                val result = isEligible && notExpired && notExpiredByDate && deviceAllowed && osAllowed
                result
            }
        )
    }

    override fun trigger(key: String, value: String?) {
        val triggeredMessages = triggerMap[key] ?: return

        for (msg in triggeredMessages) {
            if (
                msg.trigger.type == TriggerType.CUSTOM &&
                msg.trigger.key == key &&
                (msg.trigger.value == null || msg.trigger.value == value)
            ) {
                // First check if message is in schedule window - this is absolute
                if (!isInScheduleWindow(msg)) {
                    android.util.Log.d("InAppMsgManager", "[${msg.id}] Trigger matched but message not in schedule window")
                    continue
                }

                // Then check if message is eligible (not dismissed and not in cooldown)
                if (!isMessageEligibleToShow(msg)) {
                    android.util.Log.d(
                        "InAppMsgManager",
                        "[${msg.id}] Trigger matched but message not eligible due to dismissal/cooldown"
                    )
                    continue
                }

                // Add message to active list if not already present
                if (!activeMessages.contains(msg)) {
                    android.util.Log.d("InAppMsgManager", "[${msg.id}] Adding to active messages after trigger")
                    activeMessages.add(msg)
                } else {
                    android.util.Log.d("InAppMsgManager", "[${msg.id}] Already in active messages")
                }
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
    private fun isInScheduleWindow(msg: InAppMessage): Boolean {
        val schedule = msg.schedule ?: return true // No schedule means always in window

        // Get current time in system default zone
        val currentTime = ZonedDateTime.now()

        // Normalize start time to the same zone as current time if specified
        val normalizedStartTime = schedule.startTime?.withZoneSameInstant(currentTime.zone)

        // Normalize end time to the same zone as current time if specified
        val normalizedEndTime = schedule.endTime?.withZoneSameInstant(currentTime.zone)

        // Compare times with normalized zones for accurate comparison
        val afterStart = normalizedStartTime == null || !currentTime.isBefore(normalizedStartTime)
        val beforeEnd = normalizedEndTime == null || currentTime.isBefore(normalizedEndTime)

        // Log schedule status for debugging with detailed time zone information
        if (schedule.startTime != null || schedule.endTime != null) {
            android.util.Log.d(
                "InAppMsgManager", "[${msg.id}] Schedule check with normalized time zones: " +
                    "afterStart=$afterStart, " +
                    "beforeEnd=$beforeEnd, " +
                    "currentTime=$currentTime (zone=${currentTime.zone})"
            )
        }

        return afterStart && beforeEnd
    }

    override fun getActiveMessages(): List<InAppMessage> {
        // First filter by schedule (absolute constraint), then by other eligibility criteria
        return activeMessages
            .filter { msg -> isInScheduleWindow(msg) }
            .filter { msg -> isMessageEligibleToShow(msg) }
    }
}
