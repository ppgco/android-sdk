package com.pushpushgo.sdk.inapp.manager

import android.content.Context
import android.util.Log
import com.pushpushgo.sdk.core.api.PushSubscriptionProvider
import com.pushpushgo.sdk.core.internal.NotificationPermissionProvider
import com.pushpushgo.sdk.inapp.InAppMessages
import com.pushpushgo.sdk.inapp.model.DeviceType
import com.pushpushgo.sdk.inapp.model.InAppMessage
import com.pushpushgo.sdk.inapp.model.OSType
import com.pushpushgo.sdk.inapp.model.PlatformType
import com.pushpushgo.sdk.inapp.model.ShowAgainType
import com.pushpushgo.sdk.inapp.model.TriggerType
import com.pushpushgo.sdk.inapp.model.UserAudienceType
import com.pushpushgo.sdk.inapp.persistence.InAppMessagePersistence
import com.pushpushgo.sdk.inapp.repository.InAppMessageRepository
import com.pushpushgo.sdk.inapp.utils.DeviceInfoProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

internal class InAppMessageManagerImpl(
  private val scope: CoroutineScope,
  private val repository: InAppMessageRepository,
  private val persistence: InAppMessagePersistence,
  private val context: Context,
  private val debug: Boolean = false,
  private val pushSubscriptionProvider: PushSubscriptionProvider? = null,
) : InAppMessageManager {
  // Schedule refresh configuration
  private var scheduleRefreshJob: Job? = null
  private val scheduleRefreshInterval = 60_000L // Check schedules every minute

  // Thread-safe collections for message management
  private val _messagesFlow = MutableStateFlow<List<InAppMessage>>(emptyList())
  override val messagesFlow: Flow<List<InAppMessage>> = _messagesFlow.asStateFlow()
  private val activeMessages = CopyOnWriteArrayList<InAppMessage>()
  private val allMessages = CopyOnWriteArrayList<InAppMessage>()
  private val triggerMap = mutableMapOf<String, MutableList<InAppMessage>>()

  @Volatile
  private var refreshJob: Job? = null
  private var currentRoute: String? = null

  // Mutex to ensure atomic updates to activeMessages from refresh and trigger operations
  private val messagesUpdateMutex = Mutex()
  private val refreshJobMutex = Mutex()

  private val hasInitialized = CompletableDeferred<Unit>()

  // Device info
  private val currentDeviceType by lazy { DeviceInfoProvider.getCurrentDeviceType(context) }
  private val currentOsType = DeviceInfoProvider.getCurrentOSType()

  override suspend fun initialize() {
    try {
      if (debug) {
        Log.d(InAppMessages.TAG, "[Manager] Initializing...")
      }

      // Fetch messages from API (with cache support)
      refreshMessagesFromApi()

      // Start periodic schedule checks
      startScheduleRefresh()

      hasInitialized.complete(Unit)

      if (debug) {
        Log.d(InAppMessages.TAG, "[Manager] initialized successfully")
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(InAppMessages.TAG, "[Manager] Error initializing: ${e.message}", e)
    }
  }

  /**
   * Refreshes messages from API and updates internal collections
   * Uses If-None-Match header for caching optimization
   */
  private suspend fun refreshMessagesFromApi() {
    try {
      val messages =
        withContext(Dispatchers.IO) {
          repository.fetchMessages()
        }

      if (debug) {
        Log.d(InAppMessages.TAG, "[Manager] Fetched ${messages.size} messages from API")
      }

      // Update collections
      allMessages.clear()
      allMessages.addAll(messages)

      // Rebuild trigger map with new messages
      buildTriggerMap(messages)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      Log.e(InAppMessages.TAG, "[Manager] Error refreshing messages from API: ${e.message}", e)
    }
  }

  /**
   * Start a periodic job to refresh messages from API and update active message schedules
   * This runs on a background thread to avoid blocking the main thread
   * Uses If-None-Match caching to minimize network overhead
   */
  private fun startScheduleRefresh() {
    // Cancel any existing job
    scheduleRefreshJob?.cancel()

    // Start a new job in the current scope to leverage its lifecycle
    scheduleRefreshJob =
      scope.launch {
        try {
          while (true) {
            delay(scheduleRefreshInterval)

            // Don't block on refresh, just log and continue if there's an error
            try {
              if (debug) {
                Log.d(InAppMessages.TAG, "[Manager] Periodic refresh: fetching messages from API...")
              }

              // Refresh messages from API (with cache support via If-None-Match)
              refreshMessagesFromApi()

              // Then refresh active messages based on updated data
              refreshActiveMessages(currentRoute)
            } catch (e: Exception) {
              Log.e(InAppMessages.TAG, "[Manager]  Error during periodic schedule refresh: ${e.message}")
            }
          }
        } catch (e: Exception) {
          Log.e(InAppMessages.TAG, "[Manager]  Schedule refresh job failed: ${e.message}")
        }
      }
  }

  /**
   * Builds a map of trigger keys to messages for fast lookup when triggers occur
   * Only messages with CUSTOM_TRIGGER trigger type are included in the map
   *
   * @param messages List of all available messages
   */
  private fun buildTriggerMap(messages: List<InAppMessage>) {
    synchronized(triggerMap) {
      triggerMap.clear()
      val customTriggerMessages =
        messages
          .filter { it.settings.triggerType == TriggerType.CUSTOM_TRIGGER && it.settings.customTriggerKey != null }

      customTriggerMessages.forEach { msg ->
        // msg.settings.customTriggerKey is non-null here due to the filter
        val key = msg.settings.customTriggerKey!!
        triggerMap.getOrPut(key) { mutableListOf() }.add(msg)
      }
    }
  }

  /**
   * Implementation of the InAppMessageManager interface method
   * Checks if a message is eligible to be shown based on cooldown state and dismissal status
   *
   * @param message The message to check eligibility for
   * @return true if the message is eligible to be shown, false otherwise
   */
  override suspend fun isMessageEligible(message: InAppMessage): Boolean =
    withContext(Dispatchers.IO) {
      // 0. Check if message is enabled
      if (!message.enabled) {
        if (debug) {
          Log.d(InAppMessages.TAG, "[Manager] Message [${message.id}] is disabled - not eligible")
        }
        return@withContext false
      }

      // 1. Check permanent dismissal (for one-time messages)
      if (message.settings.showAgain == ShowAgainType.NEVER &&
        persistence.isMessageDismissed(
          message.id,
        )
      ) {
        if (debug) {
          Log.d(InAppMessages.TAG, "[Manager] Message [${message.id}] permanently dismissed - not eligible")
        }
        return@withContext false
      }

      // 2. Check schedule window (absolute check)
      if (!isInScheduleWindow(message)) {
        if (debug) {
          Log.d(InAppMessages.TAG, "[Manager] Message [${message.id}] outside schedule window - not eligible")
        }
        return@withContext false
      }

      // 3. Check user audience type
      if (!userMatchesAudienceType(message.audience.userType)) {
        if (debug) {
          Log.d(
            InAppMessages.TAG,
            "[Manager] Message [${message.id}] audience mismatch (${message.audience.userType}) - not eligible",
          )
        }
        return@withContext false
      }

      val nowMillis = System.currentTimeMillis()

      // 4. 'showAfterDelay' is handled by InAppMessageDisplayerImpl.

      // 5. Check 'showAgain' (cooldown for repeatable messages)
      if (message.settings.showAgain == ShowAgainType.AFTER_TIME) {
        val lastDismissedAt = persistence.getLastDismissedAt(message.id)
        val requiredCooldownSec = message.settings.showAfterTime ?: 0L

        if (lastDismissedAt != null && requiredCooldownSec > 0) { // Check if it was ever dismissed and has a cooldown
          val elapsedSinceLastDismissal = nowMillis - lastDismissedAt
          val requiredCooldownMs = requiredCooldownSec * 1000L

          if (elapsedSinceLastDismissal < requiredCooldownMs) {
            if (debug) {
              Log.d(
                InAppMessages.TAG,
                "[Manager] Message [${message.id}] in cooldown: ${elapsedSinceLastDismissal}ms/${requiredCooldownMs}ms",
              )
            }
            return@withContext false // Still in cooldown since last dismissal
          }
        }
      }

      return@withContext true
    }

  override suspend fun refreshActiveMessages(route: String?) {
    hasInitialized.await()

    // If a new route is explicitly provided (on navigation), update the manager's internal state.
    this.currentRoute = route

    val newJob =
      scope.launch {
        try {
          if (debug) {
            Log.d(InAppMessages.TAG, "[Manager] Refreshing active messages for route: $route")
          }

          val eventBasedMessages =
            if (route != null) {
              allMessages.filter { msg ->
                // CUSTOM_TRIGGER triggers are handled by the `trigger` method, not by general refresh.
                if (msg.settings.triggerType == TriggerType.CUSTOM_TRIGGER) {
                  return@filter false
                }

                val displayOnRules = msg.settings.displayOn

                if (displayOnRules.isEmpty()) {
                  return@filter true
                }

                val (displayed, hidden) = displayOnRules.partition { it.display }
                val isDisplayed = displayed.any { it.path == route }
                val isHidden = hidden.any { it.path == route }

                if (displayed.isEmpty() && !isHidden) {
                  return@filter true
                }

                if (isDisplayed && !isHidden) {
                  return@filter true
                }

                false
              }
            } else {
              emptyList()
            }

          val initiallyFiltered =
            eventBasedMessages.filter { msg ->
              val enabled = msg.enabled
              val notExpired =
                msg.expiration == null || ZonedDateTime.now().isBefore(msg.expiration)
              val correctDeviceType =
                msg.audience.device.contains(currentDeviceType) ||
                  msg.audience.device.contains(
                    DeviceType.ALL,
                  )
              val correctOsType =
                msg.audience.osType.contains(currentOsType) || msg.audience.osType.contains(OSType.ALL)
              val correctPlatform = msg.audience.platform == PlatformType.MOBILE || msg.audience.platform == PlatformType.ALL
              enabled && notExpired && correctDeviceType && correctOsType && correctPlatform
            }

          val finalEligibleMessages = mutableListOf<InAppMessage>()
          for (msg in initiallyFiltered) {
            if (msg.settings.showAfterDelay > 0 && persistence.getFirstEligibleAt(msg.id) == null) {
              persistence.setFirstEligibleAt(msg.id, System.currentTimeMillis())
            }

            if (isInScheduleWindow(msg) && isMessageEligible(msg)) {
              finalEligibleMessages.add(msg)
            }
          }

          messagesUpdateMutex.withLock {
            val newActiveMessages =
              finalEligibleMessages.sortedWith(
                compareBy { message ->
                  when (val priority = message.settings.priority) {
                    0 -> Int.MAX_VALUE

                    // Lowest priority (0 = displayed last)
                    else -> priority // 1 = highest, 2 = second, etc.
                  }
                },
              )

            activeMessages.clear()
            activeMessages.addAll(newActiveMessages)
            _messagesFlow.value = activeMessages.toList()

            if (debug) {
              Log.d(InAppMessages.TAG, "[Manager] Active messages refreshed: ${newActiveMessages.size} eligible messages")
            }
          }
        } catch (e: Exception) {
          Log.e(InAppMessages.TAG, "[Manager] Error refreshing active messages for route: $route", e)
        }
      }

    refreshJobMutex.withLock {
      refreshJob?.cancel()
      refreshJob = newJob
    }
  }

  /**
   * Trigger custom messages by key and optional value
   * Only messages with matching key (and value if specified) will be triggered
   *
   * @param key The trigger key to match
   * @param value Optional value to match (null matches any value)
   */
  override suspend fun trigger(
    key: String,
    value: String?,
  ): InAppMessage? {
    val jobToWaitFor = refreshJobMutex.withLock { refreshJob }
    jobToWaitFor?.join()

    if (debug) {
      Log.d(InAppMessages.TAG, "[Manager] Triggering custom message: key='$key', value='$value'")
    }

    val potentialMessages =
      synchronized(triggerMap) {
        triggerMap[key]?.filter { msg ->
          val typeMatch = msg.settings.triggerType == TriggerType.CUSTOM_TRIGGER
          val keyMatch = msg.settings.customTriggerKey == key
          val valueMatch = (value == null || msg.settings.customTriggerValue == value)
          typeMatch && keyMatch && valueMatch
        } ?: emptyList()
      }

    if (potentialMessages.isEmpty()) {
      if (debug) {
        Log.d(InAppMessages.TAG, "[Manager] No messages found for trigger key='$key', value='$value'")
      }
      return null
    }

    for (msg in potentialMessages.sortedWith(
      compareBy { message ->
        when (val priority = message.settings.priority) {
          0 -> Int.MAX_VALUE

          // Lowest priority (0 = displayed last)
          else -> priority // 1 = highest, 2 = second, etc.
        }
      },
    )) {
      if (isInScheduleWindow(msg) && isMessageEligible(msg)) {
        // If the message has a showAfterDelay and its firstEligibleAt is not set, set it now.
        if (msg.settings.showAfterDelay > 0 && persistence.getFirstEligibleAt(msg.id) == null) {
          persistence.setFirstEligibleAt(msg.id, System.currentTimeMillis())
        }
        if (debug) {
          Log.d(InAppMessages.TAG, "[Manager] Found eligible message [${msg.id}] for trigger '$key'")
        }
        return msg
      }
    }

    return null
  }

  private fun isInScheduleWindow(msg: InAppMessage): Boolean {
    val schedule = msg.schedule ?: return true // No schedule means always in window

    // Get current time in system default zone
    val currentTime = ZonedDateTime.now()

    // If there's no schedule constraints, message is always in window
    if (schedule.startTime == null && schedule.endTime == null) {
      return true
    }

    // Normalize time zones for accurate comparison
    val normalizedStartTime = schedule.startTime?.withZoneSameInstant(currentTime.zone)
    val normalizedEndTime = schedule.endTime?.withZoneSameInstant(currentTime.zone)

    // Check if current time is within schedule bounds
    val afterStart = normalizedStartTime == null || !currentTime.isBefore(normalizedStartTime)
    val beforeEnd = normalizedEndTime == null || currentTime.isBefore(normalizedEndTime)
    val isInWindow = afterStart && beforeEnd

    return isInWindow
  }

  private fun userMatchesAudienceType(audienceType: UserAudienceType): Boolean {
    val isSubscribed = pushSubscriptionProvider?.isSubscribed() ?: false
    val canPostNotifications = NotificationPermissionProvider.canPostNotifications(context)
    val isNotificationChannelEnabled = pushSubscriptionProvider?.isNotificationChannelEnabled() ?: false

    return when (audienceType) {
      UserAudienceType.ALL -> true
      UserAudienceType.SUBSCRIBER -> isSubscribed && canPostNotifications && isNotificationChannelEnabled
      UserAudienceType.NON_SUBSCRIBER -> !isSubscribed || !canPostNotifications || !isNotificationChannelEnabled
      UserAudienceType.NOTIFICATIONS_BLOCKED -> !canPostNotifications || !isNotificationChannelEnabled
    }
  }

  /**
   * Get the current list of active messages
   * This method returns the cached list of active messages from the most recent refresh
   * For the most up-to-date list, call refreshActiveMessages() first
   *
   * @return List of active messages that are eligible to be shown
   */
  override fun getActiveMessages(): List<InAppMessage> = activeMessages.toList()

  override fun getRoute(): String? = currentRoute
}
