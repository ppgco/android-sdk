package com.pushpushgo.inappmessages.repository

import android.content.Context
import android.util.Log
import com.pushpushgo.inappmessages.model.ActionType
import com.pushpushgo.inappmessages.model.InAppAction
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.Audience
import com.pushpushgo.inappmessages.model.Schedule
import com.pushpushgo.inappmessages.model.UserAudienceType
import com.pushpushgo.inappmessages.model.DeviceType
import com.pushpushgo.inappmessages.model.MessageType
import com.pushpushgo.inappmessages.model.OSType
import com.pushpushgo.inappmessages.model.TimeSettings
import com.pushpushgo.inappmessages.model.Trigger
import com.pushpushgo.inappmessages.model.TriggerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class InAppMessageRepositoryImpl(private val context: Context, private val sourceFileName: String) : InAppMessageRepository {
    override suspend fun fetchMessages(): List<InAppMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<InAppMessage>()
        val inputStream: InputStream = context.assets.open(sourceFileName)
        val json = inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            messages.add(parseMessage(obj))
        }
        messages
    }

    private fun parseMessage(obj: JSONObject): InAppMessage {
        val audienceObj = obj.getJSONObject("audience")
        val settingsObj = obj.getJSONObject("timeSettings")
        val actionsArray = obj.getJSONArray("actions")
        val actions = mutableListOf<InAppAction>()
        
        // Parse actions
        for (i in 0 until actionsArray.length()) {
            val actionObj = actionsArray.getJSONObject(i)
            val actionType = ActionType.valueOf(actionObj.getString("actionType").uppercase())
            val payload = mutableMapOf<String, Any?>()
            for (key in actionObj.keys()) {
                if (key != "actionType") {
                    payload[key] = actionObj.get(key)
                }
            }
            actions.add(InAppAction(actionType, payload))
        }
        
        // Parse trigger
        val triggerObj = obj.getJSONObject("trigger")
        val triggerType = TriggerType.valueOf(triggerObj.getString("type"))
        val trigger = when (triggerType) {
            TriggerType.APP_OPEN -> Trigger(type = TriggerType.APP_OPEN)
            TriggerType.ROUTE -> Trigger(type = TriggerType.ROUTE, route = triggerObj.getString("route"))
            TriggerType.CUSTOM -> Trigger(type = TriggerType.CUSTOM, key = triggerObj.getString("key"), value = triggerObj.optString("value"))
        }
        
        // Parse schedule if present
        val schedule = if (obj.has("schedule")) {
            try {
                val scheduleObj = obj.getJSONObject("schedule")
                val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
                
                val startTime = if (scheduleObj.has("startTime") && !scheduleObj.isNull("startTime")) {
                    try {
                        val startTimeStr = scheduleObj.getString("startTime")
                        ZonedDateTime.parse(startTimeStr, formatter)
                    } catch (e: DateTimeParseException) {
                        Log.e("InAppMsgRepo", "Error parsing startTime: ${e.message}")
                        null
                    }
                } else null
                
                val endTime = if (scheduleObj.has("endTime") && !scheduleObj.isNull("endTime")) {
                    try {
                        val endTimeStr = scheduleObj.getString("endTime")
                        ZonedDateTime.parse(endTimeStr, formatter)
                    } catch (e: DateTimeParseException) {
                        Log.e("InAppMsgRepo", "Error parsing endTime: ${e.message}")
                        null
                    }
                } else null
                
                Schedule(startTime, endTime).also {
                    Log.d("InAppMsgRepo", "Parsed schedule for message ${obj.getString("id")}: " +
                            "startTime=$startTime, endTime=$endTime")
                }
            } catch (e: Exception) {
                Log.e("InAppMsgRepo", "Error parsing schedule: ${e.message}")
                null
            }
        } else null
        
        // Parse expiration
        val expiration = if (obj.has("expiration") && !obj.isNull("expiration")) {
            try {
                val expirationStr = obj.getString("expiration")
                ZonedDateTime.parse(expirationStr, DateTimeFormatter.ISO_ZONED_DATE_TIME)
            } catch (e: DateTimeParseException) {
                Log.e("InAppMsgRepo", "Error parsing expiration: ${e.message}")
                null
            }
        } else null
        
        // Parse message type
        val type = if (obj.has("type") && !obj.isNull("type")) {
            try {
                MessageType.valueOf(obj.getString("type"))
            } catch (e: IllegalArgumentException) {
                MessageType.BANNER
            }
        } else MessageType.BANNER
        
        return InAppMessage(
            id = obj.getString("id"),
            name = obj.optString("name", ""),
            title = obj.optString("title", ""),
            description = obj.optString("description", ""),
            image = obj.optString("image", ""),
            template = obj.optString("template", "default"),
            actions = actions,
            audience = Audience(
                users = UserAudienceType.valueOf(audienceObj.getString("users")),
                device = audienceObj.getJSONArray("device").let { arr ->
                    List(arr.length()) { DeviceType.valueOf(arr.getString(it)) }
                },
                os = audienceObj.getJSONArray("os").let { arr ->
                    List(arr.length()) { OSType.valueOf(arr.getString(it)) }
                }
            ),
            timeSettings = TimeSettings(
                showAfterDelay = settingsObj.optLong("showAfterDelay", 0L),
                showAgain = settingsObj.optBoolean("showAgain", false),
                showAgainTime = settingsObj.optLong("showAgainTime", 0L)
            ),
            trigger = trigger,
            dismissible = obj.optBoolean("dismissible", true),
            type = type,
            priority = obj.optInt("priority", 0),
            schedule = schedule,
            expiration = expiration
        )
    }
}
