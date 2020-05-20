package com.pushpushgo.sdk.utils

import com.pushpushgo.sdk.exception.PushPushException

fun String.withApiKeyValidation(): String {
    if ("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$".toRegex().matches(this))
        return this
    else throw PushPushException("Invalid API key! $this")
}

fun String.withProjectIdValidation(): String {
    if ("[a-z0-9]{24}".toRegex().matches(this))
        return this
    else throw PushPushException("Invalid project ID! $this")
}
