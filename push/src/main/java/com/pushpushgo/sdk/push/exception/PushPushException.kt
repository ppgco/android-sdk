package com.pushpushgo.sdk.push.exception

import java.io.IOException

private const val INACTIVE_SUBSCRIBER_MESSAGE = "Cannot perform operation on inactive subscriber"
private const val FOREIGN_SUBSCRIBER_MESSAGE = "Subscriber not belongs to given project"

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

internal fun PushPushException.isSubscriberInvalid(): Boolean =
  statusCode == 404 ||
    (statusCode == 400 && message == INACTIVE_SUBSCRIBER_MESSAGE) ||
    (statusCode == 403 && message == FOREIGN_SUBSCRIBER_MESSAGE)
