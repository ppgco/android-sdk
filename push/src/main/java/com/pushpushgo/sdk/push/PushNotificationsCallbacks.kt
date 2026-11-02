package com.pushpushgo.sdk.push

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface NotificationClickHandler {
  /** Called on the main thread. */
  fun onNotificationClick(
    context: Context,
    url: String,
    overrideFlags: Int,
  )
}

fun interface InvalidProjectIdHandler {
  /** Called on the main thread. */
  fun onInvalidProjectId(
    pushProjectId: String,
    pushSubscriberId: String,
    currentProjectId: String,
  )
}

fun interface PushNotificationsErrorCallback {
  /** Called on the main thread. */
  fun onError(throwable: Throwable)
}

/** Receives confirmed backend subscription status changes on the main thread. */
fun interface SubscriptionStatusCallback {
  fun onSubscriptionStatusChanged(status: SubscriptionStatus)
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
  private val mainHandler = Handler(Looper.getMainLooper())

  @Volatile
  var notificationClickHandler: NotificationClickHandler = DefaultNotificationClickHandler()

  @Volatile
  var invalidProjectIdHandler: InvalidProjectIdHandler = DefaultInvalidProjectIdHandler()

  @Volatile
  var errorCallback: PushNotificationsErrorCallback? = null

  @Volatile
  var subscriptionStatusCallback: SubscriptionStatusCallback? = null

  private val subscriptionStatus = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Unsubscribed)

  val subscriptionStatusFlow: StateFlow<SubscriptionStatus> = subscriptionStatus.asStateFlow()

  fun notifyNotificationClick(
    context: Context,
    url: String,
    overrideFlags: Int,
  ) {
    val handler = notificationClickHandler
    dispatchToMain("Notification click handler") {
      handler.onNotificationClick(context, url, overrideFlags)
    }
  }

  fun notifyInvalidProjectId(
    pushProjectId: String,
    pushSubscriberId: String,
    currentProjectId: String,
  ) {
    val handler = invalidProjectIdHandler
    dispatchToMain("Invalid project ID handler") {
      handler.onInvalidProjectId(pushProjectId, pushSubscriberId, currentProjectId)
    }
  }

  fun notifyError(throwable: Throwable) {
    val callback = errorCallback ?: return
    dispatchToMain("Error callback") {
      callback.onError(throwable)
    }
  }

  fun publishSubscriptionStatus(status: SubscriptionStatus) {
    if (subscriptionStatus.value == status) return

    subscriptionStatus.value = status

    val callback = subscriptionStatusCallback ?: return
    dispatchToMain("Subscription status callback") {
      callback.onSubscriptionStatusChanged(status)
    }
  }

  private fun dispatchToMain(
    callbackName: String,
    callback: () -> Unit,
  ) {
    val safeCallback =
      Runnable {
        try {
          callback()
        } catch (exception: Exception) {
          Log.e(PushNotifications.TAG, "$callbackName failed", exception)
        }
      }

    if (Looper.myLooper() == Looper.getMainLooper()) {
      safeCallback.run()
    } else {
      mainHandler.post(safeCallback)
    }
  }
}
