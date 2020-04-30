package com.pushpushgo.sdk.network

import android.content.Context
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

class SharedPreferencesHelper(context: Context) {

    companion object {
        internal const val SUBSCRIBER_ID = "_PushPushGoSDK_sub_id_"
        internal const val LAST_TOKEN = "_PushPushGoSDK_curr_token_"
    }

    private val sharedPreferences = getDefaultSharedPreferences(context)

    var subscriberId
        get() = sharedPreferences.getString(SUBSCRIBER_ID, "").orEmpty()
        set(value) {
            sharedPreferences.edit().putString(SUBSCRIBER_ID, value).apply()
        }

    var lastToken
        get() = sharedPreferences.getString(LAST_TOKEN, "").orEmpty()
        set(value) {
            sharedPreferences.edit().putString(LAST_TOKEN, value).apply()
        }
}
