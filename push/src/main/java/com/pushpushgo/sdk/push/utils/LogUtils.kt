package com.pushpushgo.sdk.push.utils

import android.util.Log
import com.pushpushgo.sdk.push.PushNotifications

internal fun logDebug(text: String) {
  // Safe before initialize() completes: the SDK logs during construction (e.g.
  // Live Activity restore on API 36+), when the singleton isn't published yet.
  if (!PushNotifications.isInitialized()) return
  if (!PushNotifications.config.isDebug) return

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
  reportError(exception)
}

internal fun logError(exception: Throwable?) {
  Log.e(PushNotifications.TAG, exception?.message, exception)
  reportError(exception)
}

/**
 * Forwards an SDK error to the integrator-provided callback (if any) so swallowed
 * failures are observable beyond Logcat. Best-effort: never throws back into the
 * logging caller, and is a no-op before the SDK is initialized.
 */
private fun reportError(exception: Throwable?) {
  val throwable = exception ?: return
  if (!PushNotifications.isInitialized()) return
  runCatching { PushNotifications.errorCallback?.onError(throwable) }
}
