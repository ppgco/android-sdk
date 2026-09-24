package com.pushpushgo.sdk.push.network.interceptor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.exception.PushPushException
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class ResponseInterceptorTest {
  private val interceptor = ResponseInterceptor(Moshi.Builder().build())

  private fun response(
    code: Int,
    body: String,
  ): Response {
    val request = Request.Builder().url("https://api.example.com/").build()

    return Response
      .Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message("message")
      .body(body.toResponseBody("application/json".toMediaType()))
      .build()
  }

  private fun chainReturning(response: Response): Interceptor.Chain {
    val chain = mockk<Interceptor.Chain>()
    every { chain.request() } returns response.request
    every { chain.proceed(any()) } returns response
    return chain
  }

  @Test
  fun `throws PushPushException with message from a JSON error body (ISSUE-16)`() {
    val exception =
      assertThrows(PushPushException::class.java) {
        interceptor.intercept(chainReturning(response(400, """{"message":"Bad token"}""")))
      }

    assertEquals("Bad token", exception.message)
    assertEquals(400, exception.statusCode)
  }

  @Test
  fun `passes through a successful response untouched`() {
    val ok = response(200, """{"ok":true}""")

    assertEquals(ok, interceptor.intercept(chainReturning(ok)))
  }

  @Test
  fun `throws with fallback message for a non-JSON error body`() {
    val failedResponse = response(500, "<html>error</html>")

    val exception =
      assertThrows(PushPushException::class.java) {
        interceptor.intercept(chainReturning(failedResponse))
      }

    assertEquals("HTTP 500", exception.message)
    assertEquals(500, exception.statusCode)
  }

  @Test
  fun `throws with fallback message for an empty error body`() {
    val exception =
      assertThrows(PushPushException::class.java) {
        interceptor.intercept(chainReturning(response(503, "")))
      }

    assertEquals("HTTP 503", exception.message)
    assertEquals(503, exception.statusCode)
  }
}
