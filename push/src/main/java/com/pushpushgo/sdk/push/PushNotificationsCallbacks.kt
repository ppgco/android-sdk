package com.pushpushgo.sdk.push

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError

fun interface NotificationClickHandler {
  fun onNotificationClick(
    context: Context,
    url: String,
    overrideFlags: Int,
  )
}

fun interface InvalidProjectIdHandler {
  fun onInvalidProjectId(
    pushProjectId: String,
    pushSubscriberId: String,
    currentProjectId: String,
  )
}

fun interface PushNotificationsErrorCallback {
  fun onError(throwable: Throwable)
}

internal class DefaultNotificationClickHandler : NotificationClickHandler {
  override fun onNotificationClick(
    context: Context,
    url: String,
    overrideFlags: Int,
  ) {
    Intent.parseUri(url, 0).let {
      it.addFlags(overrideFlags)
      try {
        context.startActivity(it)
      } catch (e: ActivityNotFoundException) {
        logError("Not found activity to open uri: $url", e)
        Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
      }
    }
  }
}

internal class DefaultInvalidProjectIdHandler : InvalidProjectIdHandler {
  override fun onInvalidProjectId(
    pushProjectId: String,
    pushSubscriberId: String,
    currentProjectId: String,
  ) {
    logDebug(
      "Project ID inconsistency detected! " +
        "Project ID from push is $pushProjectId while SDK is configured with $currentProjectId",
    )
  }
}

internal class PushNotificationsCallbacks {
  @Volatile
  var notificationClickHandler: NotificationClickHandler = DefaultNotificationClickHandler()

  @Volatile
  var invalidProjectIdHandler: InvalidProjectIdHandler = DefaultInvalidProjectIdHandler()

  @Volatile
  var errorCallback: PushNotificationsErrorCallback? = null
}
