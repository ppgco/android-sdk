package com.pushpushgo.sdk.network

import android.content.Context
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.utils.PlatformType
import com.pushpushgo.sdk.utils.getPlatformType

internal class SharedPreferencesHelper(
  context: Context,
  prefsName: String? = null,
) {
  companion object {
    internal const val SUBSCRIBER_ID = "_PushPushGoSDK_sub_id_"
    internal const val LAST_FCM_TOKEN = "_PushPushGoSDK_curr_token_"
    internal const val LAST_HCM_TOKEN = "_PushPushGoSDK_curr_hms_token_"
    internal const val IS_SUBSCRIBED = "_PushPushGoSDK_is_subscribed_"
    internal const val ARE_NOTIFICATIONS_BLOCKED = "_PushPushGoSDK_notifications_blocked_"
    internal const val CUSTOM_INTENT_FLAGS = "_PushPushGoSDK_custom_intent_flags_"
    private const val MAX_KEYS = 1000
  }

  private val sharedPreferences =
    if (prefsName != null) {
      context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    } else {
      getDefaultSharedPreferences(context)
    }

  var isSubscribed
    get() =
      sharedPreferences.getBoolean(
        IS_SUBSCRIBED,
        PushPushGo.isInitialized().takeIf { it }?.let { PushPushGo.getInstance().defaultIsSubscribed } ?: false,
      )
    set(value) {
      sharedPreferences.edit().putBoolean(IS_SUBSCRIBED, value).apply()
    }

  var customIntentFlags
    get() =
      sharedPreferences.getInt(
        CUSTOM_INTENT_FLAGS,
        0,
      )
    set(value) {
      sharedPreferences.edit().putInt(CUSTOM_INTENT_FLAGS, value).apply()
    }

  var areNotificationsBlocked
    get() = sharedPreferences.getBoolean(ARE_NOTIFICATIONS_BLOCKED, false)
    set(value) {
      sharedPreferences.edit().putBoolean(ARE_NOTIFICATIONS_BLOCKED, value).apply()
    }

  var subscriberId
    get() = sharedPreferences.getString(SUBSCRIBER_ID, "").orEmpty()
    set(value) {
      sharedPreferences.edit().putString(SUBSCRIBER_ID, value).apply()
    }

  var lastFCMToken
    get() = sharedPreferences.getString(LAST_FCM_TOKEN, "").orEmpty()
    set(value) {
      sharedPreferences.edit().putString(LAST_FCM_TOKEN, value).apply()
    }

  var lastHCMToken
    get() = sharedPreferences.getString(LAST_HCM_TOKEN, "").orEmpty()
    set(value) {
      sharedPreferences.edit().putString(LAST_HCM_TOKEN, value).apply()
    }

  val lastToken
    get() =
      when (getPlatformType()) {
        PlatformType.FCM -> lastFCMToken
        PlatformType.HCM -> lastHCMToken
      }

  fun getNotificationId(key: String): Int = sharedPreferences.getInt(key, -1)

  fun setNotificationId(
    key: String,
    id: Int,
  ) {
    val allEntries = sharedPreferences.all
    if (allEntries.size >= MAX_KEYS) {
      val firstKey = allEntries.keys.firstOrNull()
      if (firstKey != null) {
        sharedPreferences.edit().remove(firstKey).apply()
      }
    }
    sharedPreferences.edit().putInt(key, id).apply()
  }
}
