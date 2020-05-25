package com.pushpushgo.sdk.utils

import com.google.firebase.iid.FirebaseInstanceId
import timber.log.Timber
import java.util.concurrent.CountDownLatch

internal val FirebaseInstanceId.deviceToken: String
    get() {
        val lock = CountDownLatch(1)
        var token = ""
        instanceId.addOnCompleteListener {
            if (it.isSuccessful) {
                token = it.result!!.token
            } else {
                Timber.w("Failed to get firebase token! ${it.exception}")
            }
            lock.countDown()
        }
        lock.await()
        return token
    }
