package com.pushpushgo.sdk

import java.util.*

internal class InternalTimerTask : Timer() {

    private var hasStarted = false

    override fun scheduleAtFixedRate(task: TimerTask?, delay: Long, period: Long) {
        hasStarted = true
        super.scheduleAtFixedRate(task, delay, period)
    }

    override fun cancel() {
        hasStarted = false
        super.cancel()
    }

    fun cancelIfRunning() {
        if (isRunning()) cancel()
    }

    private fun isRunning() = this.hasStarted
}
