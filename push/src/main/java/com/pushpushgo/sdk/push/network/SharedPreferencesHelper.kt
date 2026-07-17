package com.pushpushgo.sdk.push.network

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.utils.PlatformType
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import java.util.UUID

internal class SharedPreferencesHelper(
  context: Context,
  prefsName: String? = null,
) {
  companion object {
    private const val SUBSCRIBER_ID = "_PushPushGoSDK_sub_id_"
    private const val LAST_FCM_TOKEN = "_PushPushGoSDK_curr_token_"
    private const val LAST_HCM_TOKEN = "_PushPushGoSDK_curr_hms_token_"
    private const val IS_SUBSCRIBED = "_PushPushGoSDK_is_subscribed_"
    private const val CUSTOM_INTENT_FLAGS = "_PushPushGoSDK_custom_intent_flags_"
    private const val LA_SUBSCRIBER_PREFIX = "_PushPushGoSDK_la_sub_"
    private const val INSTALLATION_ID = "_PushPushGoSDK_installation_id_"

    // Notification-id de-duplication cache. Kept in a DEDICATED prefs file so its
    // bounded-size eviction can never delete subscription state (subscriberId /
    // token / isSubscribed), which previously shared the default prefs file.
    private const val NOTIFICATION_IDS_PREFS = "_PushPushGoSDK_notification_ids_"
    private const val NOTIFICATION_IDS_ORDER = "_PushPushGoSDK_nid_order_"
    private const val MAX_NOTIFICATION_IDS = 1000

    // Unit-separator control char, unlikely to appear in a server-generated
    // notification id; encodes the insertion-order list in one prefs string value.
    private val ORDER_SEPARATOR = Char(0x1F).toString()
  }

  private val sharedPreferences =
    if (prefsName != null) {
      context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    } else {
      getDefaultSharedPreferences(context)
    }

  private val notificationIdsPreferences =
    context.getSharedPreferences(
      if (prefsName != null) "${prefsName}_notification_ids" else NOTIFICATION_IDS_PREFS,
      Context.MODE_PRIVATE,
    )

  var isSubscribed
    get() =
      sharedPreferences.getBoolean(
        IS_SUBSCRIBED,
        PushNotifications.isInitialized().takeIf { it }?.let { PushNotifications.defaultIsSubscribed } ?: false,
      )
    set(value) {
      sharedPreferences.edit { putBoolean(IS_SUBSCRIBED, value) }
    }

  var customIntentFlags
    get() =
      sharedPreferences.getInt(
        CUSTOM_INTENT_FLAGS,
        0,
      )
    set(value) {
      sharedPreferences.edit { putInt(CUSTOM_INTENT_FLAGS, value) }
    }

  var subscriberId
    get() = sharedPreferences.getString(SUBSCRIBER_ID, null)?.ifBlank { null }
    set(value) {
      sharedPreferences.edit { putString(SUBSCRIBER_ID, value) }
    }

  private var lastFCMToken
    get() = sharedPreferences.getString(LAST_FCM_TOKEN, null)?.ifBlank { null }
    set(value) {
      sharedPreferences.edit { putString(LAST_FCM_TOKEN, value) }
    }

  private var lastHCMToken
    get() = sharedPreferences.getString(LAST_HCM_TOKEN, null)?.ifBlank { null }
    set(value) {
      sharedPreferences.edit { putString(LAST_HCM_TOKEN, value) }
    }

  var lastToken
    get() =
      when (getPlatformType()) {
        PlatformType.FCM -> lastFCMToken
        PlatformType.HCM -> lastHCMToken
      }
    set(value) {
      when (getPlatformType()) {
        PlatformType.FCM -> lastFCMToken = value
        PlatformType.HCM -> lastHCMToken = value
      }
    }

  /**
   * Stable per-installation UUID, generated and persisted on first access.
   * Used as `installationId` for Live Activity subscriber registration (backend
   * requires a UUID, unlike the Mongo-style subscriberId).
   */
  val installationId: String
    get() =
      sharedPreferences.getString(INSTALLATION_ID, null)
        ?: UUID.randomUUID().toString().also {
          sharedPreferences.edit { putString(INSTALLATION_ID, it) }
        }

  fun onPushTokenUpdated(
    subscriberId: String,
    pushToken: String,
  ) {
    if (!isSubscribed) {
      return logDebug("Token update skipped - not subscribed")
    }

    if (subscriberId != this.subscriberId) {
      return logDebug("Token update skipped - subscriberId mismatch")
    }

    this.lastToken = pushToken
  }

  /** LA subscriber id returned by the backend, keyed by live notification id. */
  fun getLiveActivitySubscriberId(liveNotificationId: String): String =
    sharedPreferences.getString(LA_SUBSCRIBER_PREFIX + liveNotificationId, "").orEmpty()

  fun setLiveActivitySubscriberId(
    liveNotificationId: String,
    subscriberId: String,
  ) {
    sharedPreferences.edit { putString(LA_SUBSCRIBER_PREFIX + liveNotificationId, subscriberId) }
  }

  fun removeLiveActivitySubscriberId(liveNotificationId: String) {
    sharedPreferences.edit { remove(LA_SUBSCRIBER_PREFIX + liveNotificationId) }
  }

  fun clearProjectData() {
    val liveActivitySubscriberKeys = sharedPreferences.all.keys.filter { it.startsWith(LA_SUBSCRIBER_PREFIX) }

    sharedPreferences.edit {
      remove(SUBSCRIBER_ID)
      remove(LAST_FCM_TOKEN)
      remove(LAST_HCM_TOKEN)
      remove(IS_SUBSCRIBED)
      liveActivitySubscriberKeys.forEach(::remove)
    }

    notificationIdsPreferences.edit { clear() }
  }

  fun getNotificationId(key: String): Int = notificationIdsPreferences.getInt(key, -1)

  /**
   * Stores a notification id under [key], evicting the oldest entries once the
   * cache exceeds [MAX_NOTIFICATION_IDS]. Eviction is deterministic FIFO driven
   * by an explicit insertion-order list (the previous implementation evicted an
   * arbitrary `keys.firstOrNull()`, which on the shared default prefs file could
   * delete the subscriber id or token).
   */
  fun setNotificationId(
    key: String,
    id: Int,
  ) {
    val order = readOrder().apply { remove(key) }
    order.addLast(key)

    notificationIdsPreferences.edit {
      while (order.size > MAX_NOTIFICATION_IDS) {
        remove(order.removeFirst())
      }
      putInt(key, id)
      putString(NOTIFICATION_IDS_ORDER, order.joinToString(ORDER_SEPARATOR))
    }
  }

  private fun readOrder(): ArrayDeque<String> {
    val raw = notificationIdsPreferences.getString(NOTIFICATION_IDS_ORDER, "").orEmpty()
    return if (raw.isEmpty()) ArrayDeque() else ArrayDeque(raw.split(ORDER_SEPARATOR))
  }
}
