package com.pushpushgo.sdk.push.utils

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.aaid.HmsInstanceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun interface PushTokenProvider {
  suspend fun getToken(): String
}

internal class PlatformPushTokenProvider(
  private val context: Context,
) : PushTokenProvider {
  override suspend fun getToken(): String =
    withContext(Dispatchers.IO) {
      when (getPlatformType()) {
        PlatformType.FCM -> getFcmPushToken()
        PlatformType.HCM -> getHcmPushToken()
      }
    }

  private suspend fun getFcmPushToken() =
    suspendCancellableCoroutine { cont ->
      FirebaseMessaging.getInstance().token.addOnCompleteListener {
        if (!it.isSuccessful) {
          val exception = it.exception ?: IllegalArgumentException("Fetching FCM registration token failed")
          cont.resumeWithException(exception)
        } else {
          val token = it.result.orEmpty()
          logDebug("FCM token length: ${token.length}")
          cont.resume(token)
        }
      }
    }

  private fun getHcmPushToken(): String {
    val appId = AGConnectOptionsBuilder().build(context).getString("client/app_id")

    return HmsInstanceId.getInstance(context).getToken(appId, "HCM")
  }
}
