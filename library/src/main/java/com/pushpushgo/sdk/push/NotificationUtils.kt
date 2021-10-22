package com.pushpushgo.sdk.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.data.Action
import com.pushpushgo.sdk.data.NotificationJsonAdapter
import com.pushpushgo.sdk.data.PushPushNotification
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import timber.log.Timber

private val moshi = Moshi.Builder().build()

internal fun deserializeNotificationData(data: Bundle?): PushPushNotification? {
    val ppNotification = data?.getString("notification") ?: return null

    val notification = NotificationJsonAdapter(moshi)
    val actions = moshi.adapter<List<Action>>(Types.newParameterizedType(List::class.java, Action::class.java))

    return PushPushNotification(
        project = data.getString("project").orEmpty(),
        subscriber = data.getString("subscriber").orEmpty(),
        campaignId = data.getString("campaign").orEmpty(),
        notification = notification.fromJson(ppNotification)!!,
        actions = actions.fromJson(data.getString("actions")!!).orEmpty(),
        icon = data.getString("icon").orEmpty(),
        image = data.getString("image").orEmpty(),
        redirectLink = data.getString("redirectLink").orEmpty()
    )
}

internal fun handleNotificationLinkClick(context: Context, uri: String) {
    Intent.parseUri(uri, 0).let {
        it.addFlags(FLAG_ACTIVITY_NEW_TASK)
        if (it.resolveActivity(context.packageManager) != null) context.startActivity(it)
        else {
            Timber.tag(PushPushGo.TAG).e("Not found activity to open uri: %s", uri)
            Toast.makeText(context, uri, Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val id = context.getString(R.string.pushpushgo_notification_default_channel_id)
        val name = context.getString(R.string.pushpushgo_notification_default_channel_name)
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            setShowBadge(true)
            enableVibration(true)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
