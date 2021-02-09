package com.pushpushgo.sdk.utils

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.aaid.HmsInstanceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.CountDownLatch

internal suspend fun getPlatformPushToken(context: Context) = when (getPlatformType()) {
    PlatformType.FCM -> getFcmPushToken()
    PlatformType.HCM -> getHcmPushToken(context)
}

private fun getFcmPushToken(): String {
    val lock = CountDownLatch(1)
    var deviceToken = ""
    FirebaseMessaging.getInstance().token.addOnCompleteListener {
        if (it.isSuccessful) {
            deviceToken = it.result!!
        } else {
            Timber.w(it.exception, "Fetching FCM registration token failed!")
        }
        lock.countDown()
    }
    lock.await()
    return deviceToken
}

private suspend fun getHcmPushToken(context: Context): String {
    return withContext(Dispatchers.IO) {
        val appId = AGConnectServicesConfig.fromContext(context).getString("client/app_id")

        HmsInstanceId.getInstance(context).getToken(appId, "HCM")
    }
}
