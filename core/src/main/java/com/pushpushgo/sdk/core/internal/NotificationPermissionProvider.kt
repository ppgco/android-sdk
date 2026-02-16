package com.pushpushgo.sdk.core.internal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RestrictTo
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object NotificationPermissionProvider {
  fun canPostNotifications(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)

    if (!manager.areNotificationsEnabled()) {
      return false
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return true
    }

    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }
}
