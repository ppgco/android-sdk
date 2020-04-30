package com.pushpushgo.sdk.utils

import com.google.firebase.iid.FirebaseInstanceId
import java.util.concurrent.CountDownLatch

internal val FirebaseInstanceId.deviceToken: String
    get() {
        val lock = CountDownLatch(1)
        var token = ""
        instanceId.addOnCompleteListener {
            if (it.isSuccessful) {
                token = it.result!!.token
            }
            lock.countDown()
        }
        lock.await()
        return token
    }
