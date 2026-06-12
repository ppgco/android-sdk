package com.pushpushgo.sdk.push.liveactivity.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.annotation.RequiresApi
import com.pushpushgo.sdk.R
import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchConfiguration
import com.pushpushgo.sdk.push.liveactivity.data.FootballMatchLiveData
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivity
import com.pushpushgo.sdk.push.liveactivity.data.LiveActivityColorSet
import com.pushpushgo.sdk.push.liveactivity.data.MatchPhase

/**
 * Renders a football match [LiveActivity] into an Android `ProgressStyle`
 * ongoing notification. Static text/images come from [FootballMatchConfiguration]
 * and the live score / status / minute come from [FootballMatchLiveData].
 */
@RequiresApi(36)
internal class ProgressStyleBuilder(
  private val context: Context,
) {
  companion object {
    private const val TOTAL_MATCH_DURATION = 90
    private const val HALF_DURATION = 45
    private const val EXTRA_TIME_DURATION = 30
    private const val EXTRA_TIME_HALF = EXTRA_TIME_DURATION / 2

    /** Visual width of each break bar, in match-minutes of bar length. */
    private const val BREAK_BAR = 12

    /** Real-world break durations, used to animate the tracker across the break bar. */
    private const val HALF_TIME_BREAK_SECONDS = 15 * 60
    private const val ET_HALF_TIME_BREAK_SECONDS = 5 * 60

    private const val COLOR_PLAYED = 0xFF4CAF50.toInt()
    private const val COLOR_BREAK = 0xFFFFC107.toInt()

    private fun applyPromotedOngoing(builder: Notification.Builder) {
      try {
        val method =
          Notification.Builder::class.java.getMethod(
            "setRequestPromotedOngoing",
            Boolean::class.javaPrimitiveType,
          )
        method.invoke(builder, true)
      } catch (_: Exception) {
        // API not available in this SDK revision
      }
    }
  }

  fun buildMatchNotification(
    activity: LiveActivity,
    homeTeamBitmap: Bitmap?,
    awayTeamBitmap: Bitmap?,
    contentIntent: PendingIntent?,
    deleteIntent: PendingIntent?,
    actions: List<Notification.Action>,
    showHotMessage: Boolean,
    largeIconBitmap: Bitmap? = homeTeamBitmap,
  ): Notification {
    val config = activity.configuration
    val liveData = activity.liveData
    val channelId = LiveActivityNotificationChannel.getChannelId(context)

    val elapsedSeconds = computeElapsedSeconds(liveData)
    val progressStyle = buildProgressStyle(config, liveData, elapsedSeconds)

    val contentTitle = teamLine(config)
    val contentText = buildContentText(activity, elapsedSeconds, showHotMessage)

    val builder =
      Notification
        .Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_stat_pushpushgo_default)
        .setContentTitle(contentTitle)
        .setContentText(contentText)
        .setSubText(config.content.title)
        .setStyle(progressStyle)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_STATUS)
        .setVisibility(Notification.VISIBILITY_PUBLIC)

    applyPromotedOngoing(builder)

    buildChipText(liveData, elapsedSeconds)?.let { builder.setShortCriticalText(it) }
    if (largeIconBitmap != null) builder.setLargeIcon(largeIconBitmap)
    if (contentIntent != null) builder.setContentIntent(contentIntent)
    if (deleteIntent != null) builder.setDeleteIntent(deleteIntent)
    actions.forEach { builder.addAction(it) }

    return builder.build()
  }

  fun buildEndedNotification(
    activity: LiveActivity,
    homeTeamBitmap: Bitmap?,
    awayTeamBitmap: Bitmap?,
    contentIntent: PendingIntent?,
  ): Notification {
    val config = activity.configuration
    val liveData = activity.liveData
    val channelId = LiveActivityNotificationChannel.getChannelId(context)

    // Reuse the live bar so the ended state stays visually consistent (full bar,
    // tracker parked at the end).
    val progressStyle = buildProgressStyle(config, liveData, computeElapsedSeconds(liveData))

    return Notification
      .Builder(context, channelId)
      .setSmallIcon(R.drawable.ic_stat_pushpushgo_default)
      .setContentTitle(teamLine(config))
      .setContentText("${liveData.homeTeamScore} : ${liveData.awayTeamScore} · ${config.label(liveData.status)}")
      .setSubText(config.content.title)
      .setStyle(progressStyle)
      .setOngoing(false)
      .setAutoCancel(true)
      .setOnlyAlertOnce(true)
      .setCategory(Notification.CATEGORY_STATUS)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .apply {
        if (homeTeamBitmap != null) setLargeIcon(homeTeamBitmap)
        if (contentIntent != null) setContentIntent(contentIntent)
      }.build()
  }

  private enum class BandKind { HALF, BREAK }

  /** One section of the progress bar: a match half/penalty run, or a break. */
  private data class Band(
    val length: Int,
    val kind: BandKind,
  )

  private fun buildProgressStyle(
    config: FootballMatchConfiguration,
    liveData: FootballMatchLiveData,
    elapsedSeconds: Int,
  ): Notification.ProgressStyle {
    val halfColor = resolveColor(config.design?.progressBarColor, COLOR_PLAYED)
    // A null breakTimeBarColor means the campaign opted out of the break bar:
    // the bar is one continuous run with no break band or squares.
    val showBreak = config.design?.breakTimeBarColor != null
    val breakColor = resolveColor(config.design?.breakTimeBarColor, COLOR_BREAK)

    val bands = bandsFor(liveData.status, showBreak)
    val total = bands.sumOf { it.length }
    val tracker = trackerPosition(liveData.status, elapsedSeconds, showBreak).coerceIn(0, total)

    // Segments always keep their full length and styledByProgress lets the
    // system de-emphasize the not-yet-reached part. Splitting bands at the
    // tracker instead would create tiny sub-segments, which the renderer
    // inflates to a minimum visual width — parking the tracker ahead of the
    // real position until the elapsed time caught up.
    val progressStyle =
      Notification
        .ProgressStyle()
        .setStyledByProgress(true)
        .setProgress(tracker)
        .setProgressSegments(
          bands.map { band ->
            Notification.ProgressStyle
              .Segment(band.length)
              .setColor(if (band.kind == BandKind.BREAK) breakColor else halfColor)
          },
        ).setProgressPoints(buildBreakPoints(bands, breakColor))

    applyTrackerIcon(progressStyle, config, halfColor)
    return progressStyle
  }

  /**
   * Tracker indicator: the system ⚽ emoji when the backend design requests it
   * (`design.android.hasTrackerIcon == true`), otherwise a plain dot tinted
   * with the progress bar colour (the system renders no indicator on its own).
   */
  private fun applyTrackerIcon(
    progressStyle: Notification.ProgressStyle,
    config: FootballMatchConfiguration,
    barColor: Int,
  ) {
    val bitmap = if (config.design?.hasTrackerIcon == true) trackerBallBitmap else dotBitmap(barColor)
    progressStyle.setProgressTrackerIcon(Icon.createWithBitmap(bitmap))
  }

  private val trackerBallBitmap: Bitmap by lazy { renderEmoji("⚽", sizePx = 64) }

  /** Cached single-colour dot; re-rendered only when the bar colour changes. */
  private var cachedDot: Pair<Int, Bitmap>? = null

  private fun dotBitmap(color: Int): Bitmap {
    cachedDot?.let { (cachedColor, bitmap) -> if (cachedColor == color) return bitmap }
    return renderDot(color, sizePx = 64).also { cachedDot = color to it }
  }

  private fun renderDot(
    color: Int,
    sizePx: Int,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
      }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.32f, paint)
    return bitmap
  }

  private fun renderEmoji(
    emoji: String,
    sizePx: Int,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sizePx * 0.85f }
    val x = (sizePx - paint.measureText(emoji)) / 2f
    val y = sizePx / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    canvas.drawText(emoji, x, y, paint)
    return bitmap
  }

  /** Phases rendered on the dedicated extra-time bar instead of the regulation one. */
  private fun isExtraTimeBar(status: MatchPhase): Boolean =
    when (status) {
      MatchPhase.EXTRA_TIME_FIRST_HALF, MatchPhase.EXTRA_TIME_FIRST_HALF_ADDED_TIME,
      MatchPhase.EXTRA_TIME_HALF_TIME_BREAK,
      MatchPhase.EXTRA_TIME_SECOND_HALF, MatchPhase.EXTRA_TIME_SECOND_HALF_ADDED_TIME,
      MatchPhase.PENALTY_SHOOTOUT,
      -> true
      else -> false
    }

  /**
   * Ordered bands of the bar: `half | break | half`, expressed in seconds so the
   * tracker can move smoothly between re-renders. Regulation time uses 45'
   * halves; once the match enters extra time the bar resets and is reused for
   * the two 15' ET halves (it is not appended to the regulation bar). When the
   * campaign opted out of the break bar ([showBreak] false) the bar is one
   * continuous run.
   */
  private fun bandsFor(
    status: MatchPhase,
    showBreak: Boolean,
  ): List<Band> {
    val halfSec = (if (isExtraTimeBar(status)) EXTRA_TIME_HALF else HALF_DURATION) * 60
    if (!showBreak) return listOf(Band(halfSec * 2, BandKind.HALF))
    return listOf(
      Band(halfSec, BandKind.HALF),
      Band(BREAK_BAR * 60, BandKind.BREAK),
      Band(halfSec, BandKind.HALF),
    )
  }

  /**
   * Tracker position (in bar seconds) for the current phase / elapsed match
   * time. Added-time phases park the tracker at the end of their half (the
   * break square / end of bar); breaks animate it from the left break square to
   * the right one over the real break duration (15' regulation, 5' in ET).
   */
  private fun trackerPosition(
    status: MatchPhase,
    elapsedSeconds: Int,
    showBreak: Boolean,
  ): Int {
    val halfSec = (if (isExtraTimeBar(status)) EXTRA_TIME_HALF else HALF_DURATION) * 60
    val breakSec = if (showBreak) BREAK_BAR * 60 else 0
    val firstHalfEnd = halfSec
    val secondHalfStart = halfSec + breakSec
    val barEnd = secondHalfStart + halfSec

    val h1EndSec = HALF_DURATION * 60
    val ftSec = TOTAL_MATCH_DURATION * 60
    val et2StartSec = (TOTAL_MATCH_DURATION + EXTRA_TIME_HALF) * 60

    return when (status) {
      MatchPhase.PRE_MATCH -> 0
      MatchPhase.FIRST_HALF -> elapsedSeconds.coerceIn(0, h1EndSec)
      MatchPhase.FIRST_HALF_ADDED_TIME -> firstHalfEnd
      MatchPhase.HALF_TIME_BREAK ->
        breakProgress(firstHalfEnd, breakSec, elapsedSeconds - h1EndSec, HALF_TIME_BREAK_SECONDS)
      MatchPhase.SECOND_HALF ->
        secondHalfStart + (elapsedSeconds - h1EndSec).coerceIn(0, halfSec)
      MatchPhase.SECOND_HALF_ADDED_TIME -> barEnd
      MatchPhase.FULL_TIME, MatchPhase.MATCH_ENDED, MatchPhase.EXTRA_TIME_BREAK -> barEnd
      MatchPhase.OTHER ->
        when {
          elapsedSeconds <= h1EndSec -> elapsedSeconds.coerceAtLeast(0)
          elapsedSeconds <= ftSec -> secondHalfStart + (elapsedSeconds - h1EndSec)
          else -> barEnd
        }
      MatchPhase.EXTRA_TIME_FIRST_HALF ->
        (elapsedSeconds - ftSec).coerceIn(0, halfSec)
      MatchPhase.EXTRA_TIME_FIRST_HALF_ADDED_TIME -> firstHalfEnd
      MatchPhase.EXTRA_TIME_HALF_TIME_BREAK ->
        breakProgress(firstHalfEnd, breakSec, elapsedSeconds - et2StartSec, ET_HALF_TIME_BREAK_SECONDS)
      MatchPhase.EXTRA_TIME_SECOND_HALF ->
        secondHalfStart + (elapsedSeconds - et2StartSec).coerceIn(0, halfSec)
      MatchPhase.EXTRA_TIME_SECOND_HALF_ADDED_TIME -> barEnd
      MatchPhase.PENALTY_SHOOTOUT -> barEnd
    }
  }

  /**
   * Tracker position inside a break band: moves linearly from the left break
   * square to the right one as the real break time passes.
   */
  private fun breakProgress(
    breakStart: Int,
    breakLength: Int,
    secondsIntoBreak: Int,
    breakDurationSeconds: Int,
  ): Int {
    val fraction = secondsIntoBreak.coerceAtLeast(0).toFloat() / breakDurationSeconds
    return breakStart + (fraction * breakLength).toInt().coerceIn(0, breakLength)
  }

  /** A break-coloured marker (square) at each end of every break band. */
  private fun buildBreakPoints(
    bands: List<Band>,
    breakColor: Int,
  ): List<Notification.ProgressStyle.Point> {
    val points = mutableListOf<Notification.ProgressStyle.Point>()
    var pos = 0
    for (band in bands) {
      if (band.kind == BandKind.BREAK) {
        points.add(Notification.ProgressStyle.Point(pos).setColor(breakColor))
        points.add(Notification.ProgressStyle.Point(pos + band.length).setColor(breakColor))
      }
      pos += band.length
    }
    return points
  }

  /**
   * Elapsed match time in seconds. When the clock is running (playing phases and
   * the generic [MatchPhase.OTHER]) and the backend supplied `statusChangedAt`,
   * it is `phase base + seconds elapsed since the status change`; otherwise the
   * phase's [MatchPhase.baseMinute] is used (e.g. FULL_TIME = 90:00).
   */
  private fun computeElapsedSeconds(liveData: FootballMatchLiveData): Int {
    val baseSeconds = liveData.status.baseMinute * 60
    val changedAt = liveData.statusChangedAt
    if (!clockRuns(liveData.status) || changedAt == null) return baseSeconds
    val elapsed = ((System.currentTimeMillis() - changedAt) / 1000L).toInt().coerceAtLeast(0)
    return baseSeconds + elapsed
  }

  /**
   * Phases where elapsed time keeps ticking from `statusChangedAt`. Breaks tick
   * too — the clock isn't displayed for them (see [showsClock]) but the tracker
   * animates across the break bar.
   */
  private fun clockRuns(status: MatchPhase): Boolean = status.isPlaying || status == MatchPhase.OTHER || status.isBreak

  /** Whether the `mm:ss` clock is shown at all for this phase. */
  private fun showsClock(status: MatchPhase): Boolean = status.isPlaying || status == MatchPhase.OTHER || status == MatchPhase.FULL_TIME

  private fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "${safe / 60}:${"%02d".format(safe % 60)}"
  }

  /**
   * Second notification line: `score \u00b7 status [\u00b7 mm:ss']`. A transient hot
   * message temporarily takes over this line (the title keeps the team names).
   * PRE_MATCH shows the remaining countdown (`m:ss \u00b7 message`) when the
   * campaign scheduled one, otherwise only the status label (no score yet);
   * breaks and MATCH_ENDED omit the clock.
   */
  private fun buildContentText(
    activity: LiveActivity,
    elapsedSeconds: Int,
    showHotMessage: Boolean,
  ): String {
    val hot = activity.hotMessage
    if (showHotMessage && hot != null) return hot.text

    val config = activity.configuration
    val liveData = activity.liveData
    val status = liveData.status
    val label = config.label(status)

    if (status == MatchPhase.PRE_MATCH) {
      val endAt = activity.countdownEndAtMs ?: return label
      val remainingSeconds = ((endAt - System.currentTimeMillis()) / 1000L).toInt()
      if (remainingSeconds <= 0) return label
      return "${formatClock(remainingSeconds)} \u00b7 ${activity.countdownMessage ?: label}"
    }

    val score = "${liveData.homeTeamScore} : ${liveData.awayTeamScore}"
    return if (showsClock(status)) {
      "$score \u00b7 $label \u00b7 ${formatClock(elapsedSeconds)}'"
    } else {
      "$score \u00b7 $label"
    }
  }

  private fun buildChipText(
    liveData: FootballMatchLiveData,
    elapsedSeconds: Int,
  ): String? {
    if (!liveData.status.isPlaying) return null
    return "${formatClock(elapsedSeconds)} ${liveData.scoreText}"
  }

  private fun teamLine(config: FootballMatchConfiguration): String = "${config.content.homeTeamName} - ${config.content.awayTeamName}"

  private fun resolveColor(
    colorSet: LiveActivityColorSet?,
    fallback: Int,
  ): Int {
    val hex = colorSet?.resolve(isDarkMode()) ?: return fallback
    return runCatching { Color.parseColor(normalizeHex(hex)) }.getOrDefault(fallback)
  }

  private fun normalizeHex(hex: String): String {
    val trimmed = hex.trim()
    return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
  }

  private fun isDarkMode(): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
}
