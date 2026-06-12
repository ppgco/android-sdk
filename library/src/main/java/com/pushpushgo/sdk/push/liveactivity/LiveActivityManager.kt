package com.pushpushgo.sdk.push.liveactivity

import com.pushpushgo.sdk.push.liveactivity.data.LiveActivity
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityPush
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityStatus
import com.pushpushgo.sdk.utils.logDebug
import com.pushpushgo.sdk.utils.logWarning
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

internal class LiveActivityManager(
  private val persistence: LiveActivityPersistence,
) {
  private val activeActivities = ConcurrentHashMap<String, LiveActivity>()

  fun restoreFromPersistence() {
    val ids = persistence.getActiveIds()
    logDebug("LiveActivityManager: restoring ${ids.size} activities from persistence")
    for (id in ids) {
      val status = persistence.getStatus(id)
      if (status == null || status == LiveActivityStatus.ENDED) {
        persistence.clearActivity(id)
        continue
      }
      val rebuilt = persistence.rebuild(id)
      if (rebuilt == null) {
        logWarning("LiveActivityManager: failed to rebuild activity $id, clearing")
        persistence.clearActivity(id)
        continue
      }
      activeActivities[id] = rebuilt
    }
  }

  /**
   * Start (or re-start) an activity from a `start` push. Requires both the
   * static [LiveActivityPush.configuration] and dynamic [LiveActivityPush.liveData].
   */
  fun startActivity(push: LiveActivityPush): LiveActivity? {
    val configuration = push.configuration
    val configurationJson = push.configurationJson
    val liveData = push.liveData
    val liveDataJson = push.liveDataJson
    if (configuration == null || configurationJson == null || liveData == null || liveDataJson == null) {
      logWarning("LiveActivityManager: cannot start activity ${push.id} — missing configuration/liveData")
      return null
    }

    val existingNotifId = persistence.getNotificationId(push.id)
    val notificationId = if (existingNotifId != -1) existingNotifId else generateNotificationId()
    val createdAt = persistence.getCreatedAt(push.id).takeIf { it > 0L } ?: System.currentTimeMillis()

    val activity =
      LiveActivity(
        id = push.id,
        projectId = push.projectId,
        subscriberId = push.subscriberId,
        template = push.template,
        status = LiveActivityStatus.ACTIVE,
        configuration = configuration,
        liveData = liveData,
        hotMessage = push.hotMessage,
        createdAt = createdAt,
      )
    activeActivities[push.id] = activity

    persistence.addActiveId(push.id)
    persistence.setNotificationId(push.id, notificationId)
    persistence.setStatus(push.id, LiveActivityStatus.ACTIVE)
    persistence.setTemplate(push.id, push.template)
    persistence.setCreatedAt(push.id, createdAt)
    persistence.setProjectId(push.id, push.projectId)
    persistence.setSubscriberId(push.id, push.subscriberId)
    persistence.setConfigurationJson(push.id, configurationJson)
    persistence.setLiveDataJson(push.id, liveDataJson)

    logDebug("LiveActivityManager: started activity ${push.id}, notifId=$notificationId")
    return activity
  }

  /**
   * Apply an `update` push: merge new [LiveActivityPush.liveData] (and optional
   * configuration / hotMessage) onto the current state, restoring from
   * persistence when the in-memory entry was lost (process death).
   */
  fun updateActivity(push: LiveActivityPush): LiveActivity? {
    val current = activeActivities[push.id] ?: persistence.rebuild(push.id)
    if (current == null) {
      logWarning("LiveActivityManager: cannot update unknown activity ${push.id}")
      return null
    }
    if (current.status == LiveActivityStatus.ENDED) {
      logWarning("LiveActivityManager: cannot update ended activity ${push.id}")
      return null
    }

    val updated =
      current.copy(
        configuration = push.configuration ?: current.configuration,
        liveData = push.liveData ?: current.liveData,
        hotMessage = push.hotMessage,
        status = LiveActivityStatus.ACTIVE,
        updatedAt = System.currentTimeMillis(),
      )
    activeActivities[push.id] = updated

    persistence.setStatus(push.id, LiveActivityStatus.ACTIVE)
    push.configurationJson?.let { persistence.setConfigurationJson(push.id, it) }
    push.liveDataJson?.let { persistence.setLiveDataJson(push.id, it) }

    logDebug("LiveActivityManager: updated activity ${push.id}")
    return updated
  }

  /**
   * Attach the pre-match countdown (from the GET document's `startPolicy`) to a
   * tracked activity and persist it for process-death rebuilds.
   */
  fun setCountdown(
    liveActivityId: String,
    message: String?,
    endAtMs: Long,
  ): LiveActivity? {
    val current = activeActivities[liveActivityId] ?: persistence.rebuild(liveActivityId) ?: return null
    val updated = current.copy(countdownMessage = message, countdownEndAtMs = endAtMs)
    activeActivities[liveActivityId] = updated
    persistence.setCountdown(liveActivityId, message, endAtMs)
    return updated
  }

  /** Apply an `end` push: merge any final live data and mark the activity ended. */
  fun endActivity(push: LiveActivityPush): LiveActivity? {
    val current = activeActivities[push.id] ?: persistence.rebuild(push.id)
    if (current == null) {
      logWarning("LiveActivityManager: cannot end unknown activity ${push.id}")
      return null
    }

    val ended =
      current.copy(
        configuration = push.configuration ?: current.configuration,
        liveData = push.liveData ?: current.liveData,
        hotMessage = push.hotMessage,
        status = LiveActivityStatus.ENDED,
        updatedAt = System.currentTimeMillis(),
      )
    activeActivities[push.id] = ended

    persistence.setStatus(push.id, LiveActivityStatus.ENDED)
    push.liveDataJson?.let { persistence.setLiveDataJson(push.id, it) }

    logDebug("LiveActivityManager: ended activity ${push.id}")
    return ended
  }

  fun removeActivity(id: String) {
    activeActivities.remove(id)
    persistence.clearActivity(id)
    logDebug("LiveActivityManager: removed activity $id")
  }

  fun markDismissedByUser(id: String) {
    persistence.markDismissedByUser(id)
    activeActivities.remove(id)
    logDebug("LiveActivityManager: activity $id dismissed by user")
  }

  fun getActivity(id: String): LiveActivity? = activeActivities[id]

  fun getNotificationId(id: String): Int = persistence.getNotificationId(id)

  fun getActiveActivities(): List<LiveActivity> = activeActivities.values.filter { it.status != LiveActivityStatus.ENDED }

  fun isActivityActive(id: String): Boolean = activeActivities[id]?.status == LiveActivityStatus.ACTIVE

  private fun generateNotificationId(): Int = Random.nextInt(100_000, Int.MAX_VALUE)
}
