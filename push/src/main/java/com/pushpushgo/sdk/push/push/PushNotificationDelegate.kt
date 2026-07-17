package com.pushpushgo.sdk.push.push

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.PushNotificationsCallbacks
import com.pushpushgo.sdk.push.R
import com.pushpushgo.sdk.push.data.Action
import com.pushpushgo.sdk.push.data.EventType
import com.pushpushgo.sdk.push.data.PushPushNotification
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPayloadParser
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.utils.PendingIntentCompat
import com.pushpushgo.sdk.push.utils.logDebug
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.logWarning
import com.pushpushgo.sdk.push.utils.mapToBundle
import com.pushpushgo.sdk.push.work.UploadManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

internal class PushNotificationDelegate(
  private val sharedPreferencesHelper: SharedPreferencesHelper,
  private val apiRepository: ApiRepository,
  private val uploadManager: UploadManager,
  private val callbacks: PushNotificationsCallbacks,
) {
  private val errorHandler = CoroutineExceptionHandler { _, throwable -> logError(throwable) }

  private val job = SupervisorJob()
  private val delegateScope = CoroutineScope(job + Dispatchers.Main)

  fun onMessageReceived(
    pushMessage: PushMessage,
    context: Context,
  ) {
    logDebug("From: ${pushMessage.from}")

    // Live Activity pushes use a dedicated envelope (type=live_notification) and
    // don't carry the standard `project`/`subscriber` keys, so route them before
    // the regular PPGo-push gate (and its project-id match check).
    if (LiveActivityPayloadParser.isLiveActivityPush(pushMessage.data)) {
      if (!areNotificationsEnabled(context)) {
        return logWarning("Push notifications are disabled by user")
      }
      PushNotifications.liveActivities.handler
        ?.handlePush(pushMessage.data)
        ?: logWarning("LiveActivityHandler not initialized, ignoring LA push")
      return
    }

    if (!PushNotifications.isPushPushGoNotification(pushMessage.data)) {
      return logWarning("Push is not from PPGo")
    }

    val pushProjectId = pushMessage.data["project"].orEmpty()
    val pushSubscriberId = pushMessage.data["subscriber"].orEmpty()
    val initializedProjectId = PushNotifications.getProjectId()
    if (pushProjectId != initializedProjectId) {
      callbacks.invalidProjectIdHandler.onInvalidProjectId(pushProjectId, pushSubscriberId, initializedProjectId)
    } else {
      processPushMessage(pushMessage, context)
    }
  }

  @SuppressLint("MissingPermission")
  private fun processPushMessage(
    pushMessage: PushMessage,
    context: Context,
  ) {
    if (!areNotificationsEnabled(context)) {
      return logWarning("Push notifications are disabled by user")
    }

    delegateScope.launch(errorHandler) {
      val notificationManager = NotificationManagerCompat.from(context)

      val notificationId = getNotificationId(pushMessage.data["nId"] ?: "default")
      logDebug("Notification ID: $notificationId")

      val notification =
        when {
          pushMessage.data.isNotEmpty() ->
            getDataNotification(
              context = context,
              remoteMessage = pushMessage,
              notificationId = notificationId,
            )

          pushMessage.notification != null ->
            getSimpleNotification(
              context = context,
              remoteMessage = pushMessage,
              notificationId = notificationId,
            )

          else -> throw IllegalStateException("Unknown notification type")
        }

      notificationManager.notify(notificationId, notification)
      logDebug("Notification sent: $notificationId => $notification")
    }
  }

  fun onNewToken(token: String) {
    logDebug("Refreshed token: $token")

    if (!PushNotifications.isInitialized()) return
    if (!PushNotifications.isSubscribed()) return
    if (!PushNotifications.areNotificationsEnabled()) return logDebug("Notifications are disabled. Skipping")

    val subscriberId = sharedPreferencesHelper.subscriberId

    if (subscriberId != null) {
      uploadManager.syncToken(subscriberId, token)
    }
  }

  fun onDestroy() {
    job.cancelChildren()
  }

  private fun getUniqueNotificationId() = Random.nextInt(0, Int.MAX_VALUE)

  private fun getNotificationId(data: String): Int {
    val existingId = sharedPreferencesHelper.getNotificationId(data)
    return if (existingId != -1) {
      existingId
    } else {
      val randomId = getUniqueNotificationId()
      sharedPreferencesHelper.setNotificationId(data, randomId)
      randomId
    }
  }

  private suspend fun getDataNotification(
    context: Context,
    remoteMessage: PushMessage,
    notificationId: Int,
  ): Notification {
    val pushPushNotification = deserializeNotificationData(remoteMessage.data.mapToBundle())

    if (pushPushNotification == null) {
      // Malformed / incomplete inner JSON: still report DELIVERED from the raw
      // data payload (don't lose the delivery metric) and render a best-effort
      // notification from the available fields.

      reportDelivered(
        subscriber = remoteMessage.data["subscriber"].orEmpty(),
        campaign = remoteMessage.data["campaign"].orEmpty(),
      )

      return getSimpleNotification(context, remoteMessage, notificationId)
    }

    reportDelivered(
      subscriber = pushPushNotification.subscriber,
      campaign = pushPushNotification.campaignId,
    )

    return createDataNotification(context, notificationId, pushPushNotification)
  }

  private fun getSimpleNotification(
    context: Context,
    remoteMessage: PushMessage,
    notificationId: Int,
  ): Notification {
    val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
    val content = remoteMessage.notification?.body ?: remoteMessage.data["body"].orEmpty()
    logDebug("Message notification title: $title")

    return createNotification(
      id = notificationId,
      context = context,
      projectId = PushNotifications.getProjectId(),
      subscriberId = remoteMessage.data["subscriber"].orEmpty(),
      title = title?.ifBlank { null } ?: context.getString(R.string.app_name),
      content = content,
      priority = translateFirebasePriority(remoteMessage.notification?.priority),
    )
  }

  private fun translateFirebasePriority(priority: Int?) =
    when (priority) {
      1 -> NotificationCompat.PRIORITY_HIGH
      2, 0 -> NotificationCompat.PRIORITY_DEFAULT
      else -> NotificationCompat.PRIORITY_DEFAULT
    }

  /**
   * Reports a DELIVERED event for the push. The event is enqueued as a durable
   * WorkManager job (survives process death / transient network loss) and is
   * gated on the payload subscriber rather than the locally stored subscriber id,
   * so deliveries are still reported when local state is momentarily out of sync.
   */
  private fun reportDelivered(
    subscriber: String,
    campaign: String,
  ) {
    uploadManager.sendEvent(
      type = EventType.DELIVERED,
      buttonId = 0,
      campaign = campaign,
      subscriberId = subscriber,
    )
  }

  private suspend fun createDataNotification(
    context: Context,
    notificationId: Int,
    notification: PushPushNotification,
  ): Notification =
    coroutineScope {
      // Fetch image and icon concurrently so a slow image download doesn't
      // serialize behind the icon (each is independently time-boxed).
      val bigPicture = async { getBitmapFromUrl(notification.image) }
      val iconPicture = async { getBitmapFromUrl(notification.icon) }

      createNotification(
        id = notificationId,
        context = context,
        notify = notification,
        playSound = true,
        ongoing = false,
        projectId = notification.project,
        subscriberId = notification.subscriber,
        bigPicture = bigPicture.await(),
        iconPicture = iconPicture.await(),
      )
    }

  private suspend fun getBitmapFromUrl(url: String?): Bitmap? {
    try {
      return withTimeoutOrNull(5000) {
        withContext(Dispatchers.IO) {
          apiRepository.getBitmapFromUrl(url)
        }
      }
    } catch (e: Throwable) {
      logError("Failed to download bitmap picture", e)
    }

    return null
  }

  private fun createNotification(
    id: Int,
    context: Context,
    notify: PushPushNotification,
    playSound: Boolean,
    ongoing: Boolean,
    projectId: String,
    subscriberId: String,
    iconPicture: Bitmap?,
    bigPicture: Bitmap?,
  ) = createNotification(
    id = id,
    context = context,
    playSound = playSound,
    ongoing = ongoing,
    projectId = projectId,
    subscriberId = subscriberId,
    title = notify.notification.title.orEmpty(),
    content = notify.notification.body.orEmpty(),
    sound = notify.notification.sound ?: "default",
    vibrate = notify.notification.vibrate.toBoolean(),
    priority = notify.notification.priority,
    badge = notify.notification.badge,
    campaignId = notify.campaignId,
    actionLink = notify.redirectLink,
    clickAction = notify.notification.click_action.orEmpty(),
    actions = notify.actions,
    iconPicture = iconPicture,
    bigPicture = bigPicture,
  )

  private fun createNotification(
    id: Int,
    context: Context,
    title: String = context.getString(R.string.app_name),
    projectId: String,
    subscriberId: String,
    content: String,
    playSound: Boolean = false,
    sound: String = "default",
    vibrate: Boolean = false,
    ongoing: Boolean = false,
    priority: Int = 0,
    badge: Int = 0,
    campaignId: String = "",
    actionLink: String = "",
    clickAction: String = "",
    actions: List<Action> = emptyList(),
    iconPicture: Bitmap? = null,
    bigPicture: Bitmap? = null,
  ) = NotificationCompat
    .Builder(context, context.getString(R.string.pushpushgo_notification_default_channel_id))
    .setContentTitle(title)
    .setContentText(content)
    .setOngoing(ongoing)
    .setPriority(priority)
    .setWhen(System.currentTimeMillis())
    .setLargeIcon(iconPicture)
    .setSmallIcon(R.drawable.ic_stat_pushpushgo_default)
    .setColor(ContextCompat.getColor(context, R.color.pushpushgo_notification_color_default))
    .apply {
      val launcherIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

      if (clickAction.isNotBlank() && clickAction == "APP_PUSH_CLICK" && launcherIntent != null) {
        setContentIntent(
          getClickActionIntent(
            context = context,
            campaignId = campaignId,
            buttonId = 0,
            link = actionLink,
            id = id,
            projectId = projectId,
            subscriberId = subscriberId,
            launcherIntent = launcherIntent,
          ),
        )
      }

      if (badge > 0) setNumber(badge)

      if (vibrate) setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

      if (playSound) {
        setAutoCancel(true)
        if (sound == "default") {
          setDefaults(Notification.DEFAULT_ALL)
        } else {
          setSound(Uri.parse(sound))
        }
      }

      setStyle(NotificationCompat.BigTextStyle().bigText(content))

      bigPicture?.let {
        setStyle(
          NotificationCompat
            .BigPictureStyle()
            .bigPicture(bigPicture)
            .bigLargeIcon(null as Bitmap?),
        )
      }

      launcherIntent?.let {
        actions.forEachIndexed { index, action ->
          val intent =
            getClickActionIntent(
              context = context,
              campaignId = campaignId,
              buttonId = index + 1,
              link = action.link,
              id = id,
              projectId = projectId,
              subscriberId = subscriberId,
              launcherIntent = it,
            )

          addAction(NotificationCompat.Action.Builder(0, action.title, intent).build())
        }
      }
    }.build()

  private fun getClickActionIntent(
    context: Context,
    campaignId: String,
    buttonId: Int,
    link: String?,
    id: Int,
    projectId: String,
    subscriberId: String,
    launcherIntent: Intent,
  ) = PendingIntent.getActivity(
    context,
    getUniqueNotificationId(),
    launcherIntent.apply {
      putExtra(NOTIFICATION_ID_EXTRA, id)
      putExtra(CAMPAIGN_ID_EXTRA, campaignId)
      putExtra(BUTTON_ID_EXTRA, buttonId)
      putExtra(PROJECT_ID_EXTRA, projectId)
      putExtra(SUBSCRIBER_ID_EXTRA, subscriberId)
      putExtra(LINK_EXTRA, link)

      logDebug("launcher intenet flags before override: $flags")

      if (PushNotifications.isInitialized()) {
        val customFlags = PushNotifications.customClickIntentFlags
        logDebug("launcher intent flags restored: $customFlags")
        if (customFlags > 0) {
          flags = customFlags
        }
      }
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
  )

  companion object {
    const val NOTIFICATION_ID_EXTRA = "notification_id"
    const val CAMPAIGN_ID_EXTRA = "campaign"
    const val BUTTON_ID_EXTRA = "button"
    const val PROJECT_ID_EXTRA = "project"
    const val SUBSCRIBER_ID_EXTRA = "subscriber"
    const val LINK_EXTRA = "link"
  }
}
