package com.pushpushgo.sdk.utils

import timber.log.Timber


class NotLoggingTree: Timber.Tree(){
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    }

}