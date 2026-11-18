package com.pushpushgo.sdk.push.network

import com.pushpushgo.sdk.push.data.Event
import com.pushpushgo.sdk.push.network.data.LiveActivityEndpointUpdateRequest
import com.pushpushgo.sdk.push.network.data.LiveActivityEventsRequest
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeRequest
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeResponse
import com.pushpushgo.sdk.push.network.data.TokenRequest
import com.pushpushgo.sdk.push.network.data.TokenResponse
import com.pushpushgo.sdk.push.network.data.TokenUpdateRequest
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

internal class FakeApiService : ApiService {
  var exception: Exception? = null
  val registeredTokens = mutableListOf<String>()
  val unregisteredSubscriberIds = mutableListOf<String>()
  val liveActivitySubscribeRequests = mutableListOf<LiveActivitySubscribeRequest>()
  val liveActivitySubscribeUrls = mutableListOf<String>()
  val unsubscribedLiveActivityUrls = mutableListOf<String>()
  val fetchedLiveActivityUrls = mutableListOf<String>()
  val updatedLiveActivityEndpoints = mutableListOf<Pair<String, LiveActivityEndpointUpdateRequest>>()
  val updatedTokens = mutableListOf<String>()
  val sentBeacons = mutableListOf<RequestBody>()
  val sentEvents = mutableListOf<Event>()

  override suspend fun registerSubscriber(
    token: String,
    projectId: String,
    body: TokenRequest,
  ) = respond {
    registeredTokens += body.token
    TokenResponse(id = SUBSCRIBER_ID)
  }

  override suspend fun unregisterSubscriber(
    token: String,
    projectId: String,
    subscriberId: String,
  ) = respond {
    unregisteredSubscriberIds += subscriberId
    Response.success<Void>(null)
  }

  override suspend fun updateSubscriberToken(
    token: String,
    projectId: String,
    subscriberId: String,
    body: TokenUpdateRequest,
  ) = respond {
    updatedTokens += body.token
    Response.success<Void>(null)
  }

  override suspend fun sendBeacon(
    token: String,
    projectId: String,
    subscriberId: String,
    beacon: RequestBody,
  ) = respond {
    sentBeacons += beacon
    Response.success<Void>(null)
  }

  override suspend fun sendEvent(
    token: String,
    projectId: String,
    event: Event,
  ) = respond {
    sentEvents += event
    Response.success<Void>(null)
  }

  override suspend fun getRawResponse(url: String): ResponseBody = error("not used")

  override suspend fun collectLiveActivityEvents(
    url: String,
    token: String,
    body: LiveActivityEventsRequest,
  ): Response<Void> = error("not used")

  override suspend fun subscribeLiveActivity(
    url: String,
    token: String,
    body: LiveActivitySubscribeRequest,
  ) = respond {
    liveActivitySubscribeRequests += body
    liveActivitySubscribeUrls += url
    LiveActivitySubscribeResponse(id = LIVE_ACTIVITY_SUBSCRIBER_ID)
  }

  override suspend fun updateLiveActivitySubscriberEndpoint(
    url: String,
    token: String,
    body: LiveActivityEndpointUpdateRequest,
  ) = respond {
    updatedLiveActivityEndpoints += url to body
    Response.success<Void>(null)
  }

  override suspend fun unsubscribeLiveActivity(
    url: String,
    token: String,
  ) = respond {
    unsubscribedLiveActivityUrls += url
    Response.success<Void>(null)
  }

  override suspend fun getLiveActivity(
    url: String,
    token: String,
  ): ResponseBody =
    respond {
      fetchedLiveActivityUrls += url
      "{}".toResponseBody()
    }

  private fun <T> respond(block: () -> T): T {
    exception?.let { throw it }
    return block()
  }

  companion object {
    const val SUBSCRIBER_ID = "subscriber-id"
    const val LIVE_ACTIVITY_SUBSCRIBER_ID = "live-activity-subscriber-id"
  }
}
