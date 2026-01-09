package com.pushpushgo.sdk.push.exception

import java.io.IOException

class PushPushException internal constructor(
  message: String,
) : IOException(message)
