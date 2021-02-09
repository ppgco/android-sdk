package com.pushpushgo.sdk.utils

import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.hms.aaid.HmsInstanceId

internal fun getPlatformType() = when {
    hasFCMLibrary() -> PlatformType.FCM
    hasHMSPushKitLibrary() -> PlatformType.HCM
    else -> throw IllegalStateException("Can't find Firebase nor PushKit libraries!")
}

internal enum class PlatformType {
    FCM, HCM
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
