package com.pushpushgo.sdk.push.network

import com.pushpushgo.sdk.push.data.Event
import com.pushpushgo.sdk.push.network.data.LiveActivityEventsRequest
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeRequest
import com.pushpushgo.sdk.push.network.data.LiveActivitySubscribeResponse
import com.pushpushgo.sdk.push.network.data.TokenRequest
import com.pushpushgo.sdk.push.network.data.TokenResponse
import com.pushpushgo.sdk.push.network.data.TokenUpdateRequest
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

internal interface ApiService {
  @POST("{projectId}/subscriber")
  suspend fun registerSubscriber(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Body body: TokenRequest,
  ): TokenResponse

  @DELETE("{projectId}/subscriber/{subscriberId}")
  suspend fun unregisterSubscriber(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Path("subscriberId") subscriberId: String,
  ): Response<Void>

  @PUT("{projectId}/subscriber/{subscriberId}/token")
  suspend fun updateSubscriberToken(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Path("subscriberId") subscriberId: String,
    @Body body: TokenUpdateRequest,
  ): Response<Void>

  @POST("{projectId}/subscriber/{subscriberId}/beacon")
  suspend fun sendBeacon(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Path("subscriberId") subscriberId: String,
    @Body beacon: RequestBody,
  ): Response<Void>

  @POST("{projectId}/event/")
  suspend fun sendEvent(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Body event: Event,
  ): Response<Void>

  @GET
  suspend fun getRawResponse(
    @Url url: String,
  ): ResponseBody

  // Live Activity statistics live under a dedicated `/statistics/v1/{platform}/`
  // base (different from the `/v1/{platform}/` API base), so the full URL is
  // built by the repository.
  @POST
  suspend fun collectLiveActivityEvents(
    @Url url: String,
    @Header("X-Token") token: String,
    @Body body: LiveActivityEventsRequest,
  ): Response<Void>

  // Live Activity (live notification) endpoints live under
  // `/core/v1/android/projects/{project}/...`, a different base path than `/v1/{platform}/`,
  // so they take a fully-qualified @Url built by the repository.
  @POST
  suspend fun subscribeLiveActivity(
    @Url url: String,
    @Header("X-Token") token: String,
    @Body body: LiveActivitySubscribeRequest,
  ): LiveActivitySubscribeResponse

  @PUT
  suspend fun updateLiveActivitySubscriberEndpoint(
    @Url url: String,
    @Header("X-Token") token: String,
    @Body body: LiveActivitySubscribeRequest,
  ): Response<Void>

  @DELETE
  suspend fun unsubscribeLiveActivity(
    @Url url: String,
    @Header("X-Token") token: String,
  ): Response<Void>

  @GET
  suspend fun getLiveActivity(
    @Url url: String,
    @Header("X-Token") token: String,
  ): ResponseBody
}
