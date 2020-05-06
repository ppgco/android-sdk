package com.pushpushgo.sdk.exception

class PushPushException internal constructor(message: String) : RuntimeException(message)

internal class NoConnectivityException(message: String) : RuntimeException(message)
