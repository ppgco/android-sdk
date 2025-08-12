package com.pushpushgo.sdk.utils

import android.os.Bundle

internal fun Map<String, String>.mapToBundle(): Bundle =
  Bundle().apply {
    forEach { (key, value) ->
      putString(key, value)
    }
  }
