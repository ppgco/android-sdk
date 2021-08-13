package com.pushpushgo.sdk.network

import android.content.Context
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.utils.PlatformType
import com.pushpushgo.sdk.utils.getPlatformType

internal class SharedPreferencesHelper(context: Context) {

    companion object {
        internal const val SUBSCRIBER_ID = "_PushPushGoSDK_sub_id_"
        internal const val LAST_FCM_TOKEN = "_PushPushGoSDK_curr_token_"
        internal const val LAST_HCM_TOKEN = "_PushPushGoSDK_curr_hms_token_"
        internal const val IS_SUBSCRIBED = "_PushPushGoSDK_is_subscribed_"
    }

    private val sharedPreferences = getDefaultSharedPreferences(context)

    var isSubscribed
        get() = sharedPreferences.getBoolean(
            IS_SUBSCRIBED,
            PushPushGo.isInitialized().takeIf { it }?.let { PushPushGo.getInstance().defaultIsSubscribed } ?: false
        )
        set(value) {
            sharedPreferences.edit().putBoolean(IS_SUBSCRIBED, value).apply()
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
        get() = when (getPlatformType()) {
            PlatformType.FCM -> lastFCMToken
            PlatformType.HCM -> lastHCMToken
        }
}
