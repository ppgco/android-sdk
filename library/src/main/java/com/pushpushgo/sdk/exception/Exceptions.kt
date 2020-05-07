package com.pushpushgo.sdk.exception

import java.io.IOException

class PushPushException internal constructor(message: String) : IOException(message)

internal class NoConnectivityException(message: String) : IOException(message)
