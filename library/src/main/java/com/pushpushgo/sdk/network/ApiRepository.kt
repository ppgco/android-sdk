package com.pushpushgo.sdk.network

internal interface ApiRepository {

    suspend fun unregisterSubscriber(token: String)

    suspend fun registerToken(token: String)
}
