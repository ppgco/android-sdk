package com.pushpushgo.sdk.utils

import android.util.Log
import com.pushpushgo.sdk.PushPushGo

internal fun logDebug(text: String) {
    if (!PushPushGo.getInstance().isDebug) return

    Log.d(PushPushGo.TAG, text)
}

internal fun logError(text: String, exception: Throwable?) {
    if (!PushPushGo.getInstance().isDebug) return

    Log.e(PushPushGo.TAG, text, exception)
}

internal fun logError(exception: Throwable?) {
    if (!PushPushGo.getInstance().isDebug) return

    Log.e(PushPushGo.TAG, "", exception)
}
