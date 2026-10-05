package com.pushpushgo.sdk.push.exception

import java.io.IOException

class PushPushException internal constructor(
  message: String,
  internal val statusCode: Int? = null,
) : IOException(message)

/**
 * Whether an API call failed in a way worth retrying: a network error, rate limiting or a server
 * error. Other API errors (4xx) are final.
 */
internal fun Throwable.isTransientApiError(): Boolean {
  val statusCode = (this as? PushPushException)?.statusCode
  return statusCode == null || statusCode == 429 || statusCode >= 500
}
