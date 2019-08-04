package com.pushpushgo.sdk.facade

import android.content.Context
import com.pushpushgo.sdk.network.ObjectResponseDataSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.kodein.di.KodeinAware
import org.kodein.di.android.closestKodein
import org.kodein.di.generic.instance

class PushPushGoFacade(context: Context) : KodeinAware {
    override val kodein by closestKodein(context)


    private val dataSource: ObjectResponseDataSource by instance()
    /**
     * function to register API Key
     * @param api key to pushpushgo API
     * **/
    fun registerApiKey(api: String) {
        GlobalScope.async(start = CoroutineStart.LAZY) {
            dataSource.registerApiKey(api)
        }
    }
}