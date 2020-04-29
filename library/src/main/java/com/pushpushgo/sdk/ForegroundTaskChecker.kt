package com.pushpushgo.sdk

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.Application
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import java.util.*

internal class ForegroundTaskChecker(
    private val application: Application,
    private val notifyTimer: InternalTimerTask
) : TimerTask() {
    private fun isAppOnForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false

        appProcesses.forEach {
            if (it.importance == IMPORTANCE_FOREGROUND && it.processName == context.packageName) {
                return true
            }
        }

        return false
    }

    override fun run() {
        if (isAppOnForeground(application)) {
            notifyTimer.cancelIfRunning()
            notifyTimer.scheduleAtFixedRate(NotificationTimerTask(application), Date(), 30000)
        } else {
            notifyTimer.cancelIfRunning()
        }
    }
}
