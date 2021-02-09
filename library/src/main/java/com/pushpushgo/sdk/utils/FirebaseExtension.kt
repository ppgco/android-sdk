package com.pushpushgo.sdk.utils

import com.google.firebase.messaging.FirebaseMessaging
import timber.log.Timber
import java.util.concurrent.CountDownLatch

internal val FirebaseMessaging.deviceToken: String
    get() {
        val lock = CountDownLatch(1)
        var deviceToken = ""
        token.addOnCompleteListener {
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
