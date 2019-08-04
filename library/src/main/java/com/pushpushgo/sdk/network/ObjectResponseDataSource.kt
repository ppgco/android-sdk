package com.pushpushgo.sdk.network

interface ObjectResponseDataSource {

    suspend fun sendToken(apiKey: String, token: String)
    suspend fun registerApiKey(apiKey: String)

}