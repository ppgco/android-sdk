package com.pushpushgo.inappmessages.network

import com.pushpushgo.inappmessages.data.event.InAppMessageEvent
import com.pushpushgo.inappmessages.model.network.InAppMessagesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface InAppListGetApi {
  @GET("/wi/v1/android/projects/{projectId}/popups")
  suspend fun getInAppMessages(
    @Path("projectId") projectId: String,
    @Header("X-Token") apiKey: String,
    @Header("If-None-Match") ifNoneMatch: String? = null,
    @Query("search") search: String = "",
    @Query("sortBy") sortBy: String = "newest",
    @Query("offset") offset: Int = 0,
    @Query("limit") limit: Int = 100,
  ): Response<InAppMessagesResponse>
}

interface InAppEventApi {
  @POST("/v1/android/{projectId}/inapp/event")
  suspend fun sendInAppEvent(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Body event: InAppMessageEvent,
  ): Response<Void>
}
