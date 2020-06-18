package com.pushpushgo.sdk.utils

import com.pushpushgo.sdk.exception.PushPushException

internal fun validateApiKey(apiKey: String) {
    if (!"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$".toRegex().matches(apiKey)) {
        throw PushPushException("Invalid API key! Current API key: `$apiKey`")
    }
}

internal fun validateProjectId(projectId: String) {
    if (!"[a-z0-9]{24}".toRegex().matches(projectId)) {
        throw PushPushException("Invalid project ID! Current project ID: `$projectId`")
    }
}
