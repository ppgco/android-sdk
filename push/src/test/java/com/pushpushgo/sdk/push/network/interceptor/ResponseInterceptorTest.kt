package com.pushpushgo.sdk.push.network.interceptor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.exception.PushPushException
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class ResponseInterceptorTest {
  private val interceptor = ResponseInterceptor()

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

  @Test(expected = PushPushException::class)
  fun `throws PushPushException with message from a JSON error body (ISSUE-16)`() {
    interceptor.intercept(chainReturning(response(400, """{"message":"Bad token"}""")))
  }

  @Test
  fun `passes through a successful response untouched`() {
    val ok = response(200, """{"ok":true}""")

    assertEquals(ok, interceptor.intercept(chainReturning(ok)))
  }

  @Test
  fun `does not throw on a non-JSON error body (ISSUE-16)`() {
    val result = interceptor.intercept(chainReturning(response(500, "<html>error</html>")))

    assertEquals(500, result.code)
  }
}
