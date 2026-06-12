package com.pushpushgo.sdk.push.liveactivity

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivity
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPayloadParser
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityStatus
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityTemplate

internal class LiveActivityPersistence(context: Context) {

  companion object {
    private const val PREFS_NAME = "ppg_live_activities"
    private const val KEY_ACTIVE_IDS = "active_ids"
    private const val KEY_PREFIX_NOTIFICATION_ID = "notif_id_"
    private const val KEY_PREFIX_STATUS = "status_"
    private const val KEY_PREFIX_TEMPLATE = "template_"
    private const val KEY_PREFIX_CREATED_AT = "created_at_"
    private const val KEY_PREFIX_DISMISSED = "dismissed_"
    private const val KEY_PREFIX_PROJECT = "project_"
    private const val KEY_PREFIX_SUBSCRIBER = "subscriber_"
    private const val KEY_PREFIX_CONFIG_JSON = "config_json_"
    private const val KEY_PREFIX_LIVE_DATA_JSON = "live_data_json_"
    private const val KEY_PREFIX_COUNTDOWN_MESSAGE = "countdown_msg_"
    private const val KEY_PREFIX_COUNTDOWN_END_AT = "countdown_end_"
  }

  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getActiveIds(): Set<String> =
    prefs.getStringSet(KEY_ACTIVE_IDS, emptySet()).orEmpty()

  fun addActiveId(liveActivityId: String) {
    val current = getActiveIds().toMutableSet()
    current.add(liveActivityId)
    prefs.edit { putStringSet(KEY_ACTIVE_IDS, current) }
  }

  fun removeActiveId(liveActivityId: String) {
    val current = getActiveIds().toMutableSet()
    current.remove(liveActivityId)
    prefs.edit { putStringSet(KEY_ACTIVE_IDS, current) }
  }

  fun getNotificationId(liveActivityId: String): Int =
    prefs.getInt("$KEY_PREFIX_NOTIFICATION_ID$liveActivityId", -1)

  fun setNotificationId(liveActivityId: String, notificationId: Int) {
    prefs.edit { putInt("$KEY_PREFIX_NOTIFICATION_ID$liveActivityId", notificationId) }
  }

  fun getStatus(liveActivityId: String): LiveActivityStatus? =
    prefs.getString("$KEY_PREFIX_STATUS$liveActivityId", null)
      ?.let { LiveActivityStatus.fromValue(it) }

  fun setStatus(liveActivityId: String, status: LiveActivityStatus) {
    prefs.edit { putString("$KEY_PREFIX_STATUS$liveActivityId", status.value) }
  }

  fun getTemplate(liveActivityId: String): LiveActivityTemplate? =
    prefs.getString("$KEY_PREFIX_TEMPLATE$liveActivityId", null)
      ?.let { LiveActivityTemplate.fromValue(it) }

  fun setTemplate(liveActivityId: String, template: LiveActivityTemplate) {
    prefs.edit { putString("$KEY_PREFIX_TEMPLATE$liveActivityId", template.value) }
  }

  fun getConfigurationJson(liveActivityId: String): String? =
    prefs.getString("$KEY_PREFIX_CONFIG_JSON$liveActivityId", null)

  fun setConfigurationJson(liveActivityId: String, json: String) {
    prefs.edit { putString("$KEY_PREFIX_CONFIG_JSON$liveActivityId", json) }
  }

  fun getLiveDataJson(liveActivityId: String): String? =
    prefs.getString("$KEY_PREFIX_LIVE_DATA_JSON$liveActivityId", null)

  fun setLiveDataJson(liveActivityId: String, json: String) {
    prefs.edit { putString("$KEY_PREFIX_LIVE_DATA_JSON$liveActivityId", json) }
  }

  fun getCountdownMessage(liveActivityId: String): String? =
    prefs.getString("$KEY_PREFIX_COUNTDOWN_MESSAGE$liveActivityId", null)

  fun getCountdownEndAt(liveActivityId: String): Long? =
    prefs.getLong("$KEY_PREFIX_COUNTDOWN_END_AT$liveActivityId", 0L).takeIf { it > 0L }

  fun setCountdown(liveActivityId: String, message: String?, endAtMs: Long) {
    prefs.edit {
      putString("$KEY_PREFIX_COUNTDOWN_MESSAGE$liveActivityId", message)
      putLong("$KEY_PREFIX_COUNTDOWN_END_AT$liveActivityId", endAtMs)
    }
  }

  /**
   * Rebuild a [LiveActivity] from persisted JSON (e.g. after process death) so
   * that subsequent `update` pushes can be merged onto the last known state.
   * The transient `hotMessage` is intentionally not restored.
   */
  fun rebuild(liveActivityId: String): LiveActivity? {
    val template = getTemplate(liveActivityId) ?: return null
    val status = getStatus(liveActivityId) ?: return null
    val configJson = getConfigurationJson(liveActivityId) ?: return null
    val liveDataJson = getLiveDataJson(liveActivityId) ?: return null
    val configuration = LiveActivityPayloadParser.parseConfiguration(configJson) ?: return null
    val liveData = LiveActivityPayloadParser.parseLiveData(liveDataJson) ?: return null

    return LiveActivity(
      id = liveActivityId,
      projectId = getProjectId(liveActivityId),
      subscriberId = getSubscriberId(liveActivityId),
      template = template,
      status = status,
      configuration = configuration,
      liveData = liveData,
      hotMessage = null,
      createdAt = getCreatedAt(liveActivityId),
      countdownMessage = getCountdownMessage(liveActivityId),
      countdownEndAtMs = getCountdownEndAt(liveActivityId),
    )
  }

  fun getCreatedAt(liveActivityId: String): Long =
    prefs.getLong("$KEY_PREFIX_CREATED_AT$liveActivityId", 0L)

  fun setCreatedAt(liveActivityId: String, createdAt: Long) {
    prefs.edit { putLong("$KEY_PREFIX_CREATED_AT$liveActivityId", createdAt) }
  }

  fun isDismissedByUser(liveActivityId: String): Boolean =
    prefs.getBoolean("$KEY_PREFIX_DISMISSED$liveActivityId", false)

  fun markDismissedByUser(liveActivityId: String) {
    prefs.edit { putBoolean("$KEY_PREFIX_DISMISSED$liveActivityId", true) }
  }

  fun getProjectId(liveActivityId: String): String =
    prefs.getString("$KEY_PREFIX_PROJECT$liveActivityId", "").orEmpty()

  fun setProjectId(liveActivityId: String, projectId: String) {
    prefs.edit { putString("$KEY_PREFIX_PROJECT$liveActivityId", projectId) }
  }

  fun getSubscriberId(liveActivityId: String): String =
    prefs.getString("$KEY_PREFIX_SUBSCRIBER$liveActivityId", "").orEmpty()

  fun setSubscriberId(liveActivityId: String, subscriberId: String) {
    prefs.edit { putString("$KEY_PREFIX_SUBSCRIBER$liveActivityId", subscriberId) }
  }

  fun clearActivity(liveActivityId: String) {
    removeActiveId(liveActivityId)
    prefs.edit {
      remove("$KEY_PREFIX_NOTIFICATION_ID$liveActivityId")
      remove("$KEY_PREFIX_STATUS$liveActivityId")
      remove("$KEY_PREFIX_TEMPLATE$liveActivityId")
      remove("$KEY_PREFIX_CREATED_AT$liveActivityId")
      remove("$KEY_PREFIX_DISMISSED$liveActivityId")
      remove("$KEY_PREFIX_PROJECT$liveActivityId")
      remove("$KEY_PREFIX_SUBSCRIBER$liveActivityId")
      remove("$KEY_PREFIX_CONFIG_JSON$liveActivityId")
      remove("$KEY_PREFIX_LIVE_DATA_JSON$liveActivityId")
      remove("$KEY_PREFIX_COUNTDOWN_MESSAGE$liveActivityId")
      remove("$KEY_PREFIX_COUNTDOWN_END_AT$liveActivityId")
    }
  }

  fun clearAll() {
    prefs.edit { clear() }
  }
}

