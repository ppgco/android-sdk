package com.pushpushgo.inappmessages.network

import com.pushpushgo.inappmessages.model.network.InAppMessagesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface InAppApi {

    @GET("/wi/projects/{projectId}/popups")
    suspend fun getInAppMessages(
        @Path("projectId") projectId: String,
        @Header("X-Token") apiKey: String,
        @Query("search") search: String = "",
        @Query("sortBy") sortBy: String = "newest",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20,
    ): Response<InAppMessagesResponse>
}
