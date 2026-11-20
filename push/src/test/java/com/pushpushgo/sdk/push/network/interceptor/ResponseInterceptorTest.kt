package com.pushpushgo.sdk.push.network.interceptor

import com.pushpushgo.sdk.push.exception.PushPushException
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResponseInterceptorTest {
  private fun callRespondingWith(
    code: Int,
    body: String,
  ): Response {
    val client =
      OkHttpClient
        .Builder()
        .addInterceptor(ResponseInterceptor(Moshi.Builder().build()))
        .addInterceptor { chain ->
          Response
            .Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("message")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
        }.build()
    val request = Request.Builder().url("https://api.example.com/").build()

    return client.newCall(request).execute()
  }

  @Test
  fun `throws PushPushException with message from a JSON error body`() {
    val exception =
      assertFailsWith<PushPushException> {
        callRespondingWith(400, """{"message":"Bad token"}""")
      }

    assertEquals("Bad token", exception.message)
    assertEquals(400, exception.statusCode)
  }

  @Test
  fun `passes through a successful response untouched`() {
    val response = callRespondingWith(200, """{"ok":true}""")

    assertEquals(200, response.code)
    assertEquals("""{"ok":true}""", response.body?.string())
  }

  @Test
  fun `throws with HTTP status as message when the error body has no readable message`() {
    val nonJson = assertFailsWith<PushPushException> { callRespondingWith(500, "<html>error</html>") }
    val empty = assertFailsWith<PushPushException> { callRespondingWith(503, "") }

    assertEquals("HTTP 500", nonJson.message)
    assertEquals(500, nonJson.statusCode)
    assertEquals("HTTP 503", empty.message)
    assertEquals(503, empty.statusCode)
  }
}
