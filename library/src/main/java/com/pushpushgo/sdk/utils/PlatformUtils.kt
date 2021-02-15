package com.pushpushgo.sdk.utils

import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.aaid.HmsInstanceId

internal fun getPlatformType() = when {
    hasFCMLibrary() -> PlatformType.FCM
    hasHMSPushKitLibrary() -> PlatformType.HCM
    else -> throw IllegalStateException("Can't find Firebase nor PushKit libraries!")
}

internal enum class PlatformType(val apiName: String) {
    FCM("android"),
    HCM("huawei")
}

private fun hasHMSPushKitLibrary() = try {
    checkNotNull(HmsInstanceId::class.java)
    checkNotNull(AGConnectServicesConfig::class.java)
    true
} catch (e: NoClassDefFoundError) {
    false
}

private fun hasFCMLibrary() = try {
    checkNotNull(FirebaseMessaging::class.java)
    true
} catch (e: Throwable) {
    false
}
