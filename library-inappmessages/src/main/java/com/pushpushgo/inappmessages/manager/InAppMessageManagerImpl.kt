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
import kotlinx.coroutines.launch
import java.time.ZonedDateTime.now
import java.util.concurrent.CopyOnWriteArrayList

class InAppMessageManagerImpl(
    private val repository: InAppMessageRepository,
    private val persistence: InAppMessagePersistence,
    private val context: Context
) : InAppMessageManager {
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
                
                android.util.Log.d("InAppMsgManager", "isMessageEligibleToShow: [${msg.id}] lastShownAt=$lastShownAt elapsed=$elapsed showAgainTime=${msg.timeSettings.showAgainTime} canShowAgain=$canShowAgain")
                
                if (!canShowAgain) {
                    return false // Still in cooldown
                }
            }
        }
        
        return true
    }
    
    override fun refreshActiveMessages() {
        val now = now()
        activeMessages.clear()
        val deviceType = DeviceInfoProvider.getCurrentDeviceType(context)
        val osType = DeviceInfoProvider.getCurrentOSType()
        
        activeMessages.addAll(
            allMessages.filter { msg ->
                // Check cooldown and dismissal status
                val isEligible = isMessageEligibleToShow(msg)
                
                // Standard filters
                val notExpired = !persistence.isMessageExpired(msg.id)
                val inSchedule = msg.schedule?.let { sch ->
                    (sch.startTime == null || now.isAfter(sch.startTime)) &&
                        (sch.endTime == null || now.isBefore(sch.endTime))
                } ?: true
                val notExpiredByDate = msg.expiration?.let { now.isBefore(it) } ?: true
                val deviceAllowed = msg.audience.device.contains(DeviceType.ALL) || msg.audience.device.contains(deviceType)
                val osAllowed = msg.audience.os.contains(OSType.ALL) || msg.audience.os.contains(osType)
                
                isEligible && notExpired && inSchedule && notExpiredByDate && deviceAllowed && osAllowed
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
                // Check if message is eligible (not dismissed and not in cooldown)
                if (!isMessageEligibleToShow(msg)) {
                    continue
                }
                
                // Add message to active list if not already present
                if (!activeMessages.contains(msg)) {
                    activeMessages.add(msg)
                }
            }
        }
    }

    override fun getActiveMessages(): List<InAppMessage> {
        // Filter active messages to only include eligible ones (not dismissed and not in cooldown)
        return activeMessages.filter { msg -> isMessageEligibleToShow(msg) }
    }
}
