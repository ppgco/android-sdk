package com.pushpushgo.sdk.utils

internal fun getPlatformType() = when {
    hasFCMLibrary() -> PlatformType.FCM
    hasHMSPushKitLibrary() -> PlatformType.HCM
    else -> throw IllegalStateException("Can't find Firebase nor PushKit libraries!")
}

internal enum class PlatformType(val apiName: String) {
    FCM("android"),
    HCM("huawei")
}

private fun hasHMSPushKitLibrary(): Boolean = try {
    checkNotNull(com.huawei.hms.aaid.HmsInstanceId::class.java.name)
    checkNotNull(com.huawei.agconnect.AGConnectOptionsBuilder::class.java.name)
    true
} catch (e: NoClassDefFoundError) {
    false
}

private fun hasFCMLibrary() = try {
    checkNotNull(com.google.firebase.messaging.FirebaseMessaging::class.java.name)
    true
} catch (e: Throwable) {
    false
}
