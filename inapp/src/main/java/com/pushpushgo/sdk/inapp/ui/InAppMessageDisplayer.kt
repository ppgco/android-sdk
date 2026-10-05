package com.pushpushgo.sdk.inapp.ui

import android.app.Activity
import com.pushpushgo.sdk.inapp.model.InAppMessage

internal interface InAppMessageDisplayer {
  fun showMessage(
    activity: Activity,
    message: InAppMessage,
  )

  fun dismissMessage(message: InAppMessage)

  fun cancelPendingMessages(isActivityPaused: Boolean = false)

  /** Hides the displayed message and stops displaying new ones. Must be called on the main thread. */
  fun release()
}
