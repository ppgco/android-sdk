package com.pushpushgo.sdk.utils

import com.chuckerteam.chucker.api.ChuckerCollector
import timber.log.Timber

class TimberChuckerErrorTree(private val chucker: ChuckerCollector) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        t?.let {
            chucker.onError(tag.orEmpty(), t)
        }
    }
}
