package com.pushpushgo.sdk.push.exception

import java.io.IOException

class PushPushException internal constructor(
  message: String,
  internal val statusCode: Int? = null,
) : IOException(message)
