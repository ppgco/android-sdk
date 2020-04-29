package com.pushpushgo.sdk.network

internal interface ObjectResponseDataSource {

    suspend fun registerToken(token: String)

    suspend fun unregisterSubscriber(token: String)
}
