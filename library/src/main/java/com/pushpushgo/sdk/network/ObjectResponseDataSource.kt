package com.pushpushgo.sdk.network

internal interface ObjectResponseDataSource {

    suspend fun sendToken(apiKey: String, token: String)
    suspend fun registerApiKey(apiKey: String)
    suspend fun unregisterSubscriber(apiKey: String, token: String)

}