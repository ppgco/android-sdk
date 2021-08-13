package com.pushpushgo.sdk.utils

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hms.aaid.HmsInstanceId
import com.pushpushgo.sdk.PushPushGo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal suspend fun getPlatformPushToken(context: Context) = when (getPlatformType()) {
    PlatformType.FCM -> getFcmPushToken()
    PlatformType.HCM -> getHcmPushToken(context)
}

private suspend fun getFcmPushToken() = suspendCoroutine<String> { cont ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener {
        if (!it.isSuccessful) {
            val exception = it.exception ?: IllegalArgumentException("Fetching FCM registration token failed")
            cont.resumeWithException(exception)
        } else {
            val token = it.result.orEmpty()
            Timber.tag(PushPushGo.TAG).d("FCM token length: ${token.length}")
            cont.resumeWith(Result.success(token))
        }
    }
}

private suspend fun getHcmPushToken(context: Context) = withContext(Dispatchers.IO) {
    val appId = AGConnectOptionsBuilder().build(context).getString("client/app_id")

    HmsInstanceId.getInstance(context).getToken(appId, "HCM")
}
