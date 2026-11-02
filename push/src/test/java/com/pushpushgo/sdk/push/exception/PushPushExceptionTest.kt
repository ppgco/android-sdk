package com.pushpushgo.sdk.push.exception

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PushPushExceptionTest {
  @Test
  fun `network errors, rate limiting and server errors are transient`() {
    assertTrue(IOException("timeout").isTransientApiError())
    assertTrue(PushPushException("Too many requests", 429).isTransientApiError())
    assertTrue(PushPushException("HTTP 500", 500).isTransientApiError())
    assertTrue(PushPushException("HTTP 503", 503).isTransientApiError())
  }

  @Test
  fun `client errors other than rate limiting are not transient`() {
    assertFalse(PushPushException("Bad request", 400).isTransientApiError())
    assertFalse(PushPushException("Not found", 404).isTransientApiError())
  }

  @Test
  fun `subscriber is invalid when not found, inactive or belonging to another project`() {
    assertTrue(PushPushException("Not found", 404).isSubscriberInvalid())
    assertTrue(PushPushException("Cannot perform operation on inactive subscriber", 400).isSubscriberInvalid())
    assertTrue(PushPushException("Subscriber not belongs to given project", 403).isSubscriberInvalid())
  }

  @Test
  fun `other bad request and forbidden errors do not invalidate the subscriber`() {
    assertFalse(PushPushException("Bad token", 400).isSubscriberInvalid())
    assertFalse(PushPushException("Forbidden", 403).isSubscriberInvalid())
    assertFalse(PushPushException("HTTP 500", 500).isSubscriberInvalid())
  }
}
