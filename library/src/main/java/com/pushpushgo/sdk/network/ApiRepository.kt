package com.pushpushgo.sdk.network

interface ApiRepository {
    suspend fun unregisterSubscriber(token: String)
    suspend fun registerToken(token: String)
}