package com.pushpushgo.sdk.utils

import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.hms.aaid.HmsInstanceId

internal fun getPlatformType() = when {
    hasHMSPushKitLibrary() -> PlatformType.HCM
    hasFCMLibrary() -> PlatformType.FCM
    else -> throw IllegalStateException("Can't find Firebase nor PushKit libraries!")
}

internal enum class PlatformType(val apiName: String) {
    FCM("android"),
    HCM("huawei")
}

private fun hasHMSPushKitLibrary() = try {
    @Suppress("SENSELESS_COMPARISON")
    HmsInstanceId::class.java != null
} catch (e: NoClassDefFoundError) {
    false
}

private fun hasFCMLibrary() = try {
    @Suppress("SENSELESS_COMPARISON")
    FirebaseMessaging::class.java != null
} catch (e: Throwable) {
    false
}
