package com.pushpushgo.sdk.utils

import android.os.Bundle

internal fun Map<String, String>.mapToBundle(): Bundle {
    return Bundle().apply {
        forEach { (key, value) ->
            putString(key, value)
        }
    }
}
