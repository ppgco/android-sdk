package com.pushpushgo.sdk.push.utils

import android.util.Log
import com.pushpushgo.sdk.push.PushNotifications

internal fun logDebug(text: String) {
  if (!PushNotifications.getInstance().config.isDebug) return

  Log.d(PushNotifications.TAG, text)
}

internal fun logWarning(text: String) {
  Log.w(PushNotifications.TAG, text)
}

internal fun logError(
  text: String,
  exception: Throwable? = null,
) {
  Log.e(PushNotifications.TAG, text, exception)
}

internal fun logError(exception: Throwable?) {
  Log.e(PushNotifications.TAG, exception?.message, exception)
}
