package com.pushpushgo.inappmessages.ui

import android.app.Activity
import com.pushpushgo.inappmessages.model.InAppMessage

interface InAppMessageDisplayer {
    fun showMessage(activity: Activity, message: InAppMessage)
    fun dismissMessage(messageId: String)
}
