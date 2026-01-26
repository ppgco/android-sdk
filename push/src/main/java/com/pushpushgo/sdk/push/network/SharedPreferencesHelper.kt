package com.pushpushgo.sdk.push.network

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.utils.PlatformType
import com.pushpushgo.sdk.push.utils.getPlatformType

internal class SharedPreferencesHelper(
  context: Context,
  prefsName: String? = null,
) {
  companion object {
    private const val SUBSCRIBER_ID = "_PushPushGoSDK_sub_id_"
    private const val LAST_FCM_TOKEN = "_PushPushGoSDK_curr_token_"
    private const val LAST_HCM_TOKEN = "_PushPushGoSDK_curr_hms_token_"
    private const val IS_SUBSCRIBED = "_PushPushGoSDK_is_subscribed_"
    private const val CUSTOM_INTENT_FLAGS = "_PushPushGoSDK_custom_intent_flags_"
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
        PushNotifications.isInitialized().takeIf { it }?.let { PushNotifications.getInstance().defaultIsSubscribed } ?: false,
      )
    set(value) {
      sharedPreferences.edit { putBoolean(IS_SUBSCRIBED, value) }
    }

  var customIntentFlags
    get() =
      sharedPreferences.getInt(
        CUSTOM_INTENT_FLAGS,
        0,
      )
    set(value) {
      sharedPreferences.edit { putInt(CUSTOM_INTENT_FLAGS, value) }
    }

  var subscriberId
    get() = sharedPreferences.getString(SUBSCRIBER_ID, null)?.ifBlank { null }
    set(value) {
      sharedPreferences.edit { putString(SUBSCRIBER_ID, value) }
    }

  private var lastFCMToken
    get() = sharedPreferences.getString(LAST_FCM_TOKEN, null)?.ifBlank { null }
    set(value) {
      sharedPreferences.edit { putString(LAST_FCM_TOKEN, value) }
    }

  private var lastHCMToken
    get() = sharedPreferences.getString(LAST_HCM_TOKEN, null)?.ifBlank { null }
    set(value) {
      sharedPreferences.edit { putString(LAST_HCM_TOKEN, value) }
    }

  var lastToken
    get() =
      when (getPlatformType()) {
        PlatformType.FCM -> lastFCMToken
        PlatformType.HCM -> lastHCMToken
      }
    set(value) {
      when (getPlatformType()) {
        PlatformType.FCM -> lastFCMToken = value
        PlatformType.HCM -> lastHCMToken = value
      }
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
        sharedPreferences.edit { remove(firstKey) }
      }
    }
    sharedPreferences.edit { putInt(key, id) }
  }
}
