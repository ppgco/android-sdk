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

    override fun refreshActiveMessages() {
        val now = now()
        activeMessages.clear()
        val deviceType = DeviceInfoProvider.getCurrentDeviceType(context)
        val osType = DeviceInfoProvider.getCurrentOSType()
        activeMessages.addAll(
            allMessages.filter { msg ->
                val notDismissed = !persistence.isMessageDismissed(msg.id)
                val notExpired = !persistence.isMessageExpired(msg.id)
                val inSchedule = msg.schedule?.let { sch ->
                    (sch.startTime == null || now.isAfter(sch.startTime)) &&
                        (sch.endTime == null || now.isBefore(sch.endTime))
                } ?: true
                val notExpiredByDate = msg.expiration?.let { now.isBefore(it) } ?: true
                val deviceAllowed = msg.audience.device.contains(DeviceType.ALL) || msg.audience.device.contains(deviceType)
                val osAllowed = msg.audience.os.contains(OSType.ALL) || msg.audience.os.contains(osType)
                notDismissed && notExpired && inSchedule && notExpiredByDate && deviceAllowed && osAllowed
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
                if (!activeMessages.contains(msg)) {
                    activeMessages.add(msg)
                }
            }
        }
    }

    override fun getActiveMessages(): List<InAppMessage> = activeMessages.toList()
}
