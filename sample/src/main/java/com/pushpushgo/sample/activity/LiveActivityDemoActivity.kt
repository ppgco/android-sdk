package com.pushpushgo.sample.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.pushpushgo.sample.R
import com.pushpushgo.sdk.PushPushGo
import org.json.JSONObject
import timber.log.Timber

/**
 * Demonstrates the Live Activities (live notification) integration.
 *
 * Uses [PushPushGo.simulateLiveActivityPush] to feed the SDK the exact same
 * `Map<String, String>` envelope the FCM `data` message would carry, so the
 * full parse -> manage -> render pipeline is exercised without a backend.
 *
 * Live Activities require Android API 36+. On older devices the SDK ignores the
 * pushes and the buttons only update the local status readout.
 */
class LiveActivityDemoActivity : AppCompatActivity(R.layout.activity_live_activity) {

    private val ppg by lazy { PushPushGo.getInstance() }

    // Local mirror of the match state so the buttons can evolve it over time.
    private var homeScore = 0
    private var awayScore = 0
    private var phaseIndex = 0

    private val phases = listOf("FIRST_HALF", "HALF_TIME_BREAK", "SECOND_HALF", "FULL_TIME")
    private val currentPhase get() = phases[phaseIndex.coerceIn(phases.indices)]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBarInsets()
        requestNotificationPermission()

        val supported = ppg.isLiveActivitiesSupported()
        findViewById<TextView>(R.id.la_support).text =
            "Live Activities supported (API 36+): $supported"

        findViewById<EditText>(R.id.la_id_input).setText(LA_ID)

        findViewById<Button>(R.id.la_subscribe).setOnClickListener { subscribe() }
        findViewById<Button>(R.id.la_unsubscribe).setOnClickListener { unsubscribe() }

        findViewById<Button>(R.id.la_start).setOnClickListener {
            homeScore = 0; awayScore = 0; phaseIndex = 0
            simulate(event = "start", includeConfiguration = true)
        }
        findViewById<Button>(R.id.la_home_goal).setOnClickListener {
            homeScore++
            simulate(event = "update", hotMessage = "GOAL! ${HOME_TEAM} scores")
        }
        findViewById<Button>(R.id.la_away_goal).setOnClickListener {
            awayScore++
            simulate(event = "update", hotMessage = "GOAL! ${AWAY_TEAM} scores")
        }
        findViewById<Button>(R.id.la_next_phase).setOnClickListener {
            phaseIndex = (phaseIndex + 1).coerceAtMost(phases.lastIndex)
            simulate(event = "update")
        }
        findViewById<Button>(R.id.la_hot_message).setOnClickListener {
            simulate(event = "update", hotMessage = "Yellow card - 67'")
        }
        findViewById<Button>(R.id.la_end).setOnClickListener {
            phaseIndex = phases.lastIndex
            simulate(event = "end")
        }
        findViewById<Button>(R.id.la_refresh).setOnClickListener { renderStatus() }

        renderStatus()
    }

    /** Live notification id from the input field, falling back to the demo id. */
    private fun liveNotificationId(): String =
        findViewById<EditText>(R.id.la_id_input).text.toString().trim().ifEmpty { LA_ID }

    private fun subscribe() {
        val id = liveNotificationId()
        Futures.addCallback(ppg.subscribeToLiveActivity(id), object : FutureCallback<String> {
            override fun onSuccess(result: String) {
                Toast.makeText(this@LiveActivityDemoActivity, "Subscribed to $id (laSubscriberId=$result)", Toast.LENGTH_LONG).show()
            }

            override fun onFailure(t: Throwable) {
                Timber.tag("PPGO_SAMPLE").e(t, "subscribeToLiveActivity failed")
                Toast.makeText(this@LiveActivityDemoActivity, "Subscribe failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unsubscribe() {
        val id = liveNotificationId()
        Futures.addCallback(ppg.unsubscribeFromLiveActivity(id), object : FutureCallback<Unit> {
            override fun onSuccess(result: Unit) {
                Toast.makeText(this@LiveActivityDemoActivity, "Unsubscribed from $id", Toast.LENGTH_LONG).show()
            }

            override fun onFailure(t: Throwable) {
                Timber.tag("PPGO_SAMPLE").e(t, "unsubscribeFromLiveActivity failed")
                Toast.makeText(this@LiveActivityDemoActivity, "Unsubscribe failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun simulate(event: String, includeConfiguration: Boolean = false, hotMessage: String? = null) {
        val payload = buildEnvelope(event, includeConfiguration, hotMessage)
        Timber.tag("PPGO_SAMPLE").d("simulateLiveActivityPush: $payload")
        ppg.simulateLiveActivityPush(payload)

        if (!ppg.isLiveActivitiesSupported()) {
            Toast.makeText(this, "Device < API 36 - push ignored by SDK", Toast.LENGTH_SHORT).show()
        }
        // Give the SDK a moment to apply the change before reading state back.
        findViewById<TextView>(R.id.la_status).postDelayed({ renderStatus() }, 300)
    }

    private fun buildEnvelope(
        event: String,
        includeConfiguration: Boolean,
        hotMessage: String?,
    ): Map<String, String> {
        val envelope = mutableMapOf(
            "type" to "live_notification",
            "liveNotificationId" to liveNotificationId(),
            "event" to event,
            "template" to "FOOTBALL_MATCH_TRACKING",
            "project" to ppg.getProjectId(),
            "subscriber" to runCatching { ppg.getSubscriberId() }.getOrNull().orEmpty(),
            "liveData" to liveDataJson(),
        )
        if (includeConfiguration) envelope["configuration"] = CONFIGURATION_JSON
        if (hotMessage != null) envelope["hotMessage"] = hotMessageJson(hotMessage)
        return envelope
    }

    private fun liveDataJson(): String = JSONObject().apply {
        put("type", "FOOTBALL_MATCH_TRACKING")
        put("homeTeamScore", homeScore)
        put("awayTeamScore", awayScore)
        put("status", currentPhase)
        put("statusChangedAt", System.currentTimeMillis())
    }.toString()

    private fun hotMessageJson(text: String): String = JSONObject().apply {
        put("id", "hot-${System.currentTimeMillis()}")
        put("text", text)
        put("timestamp", System.currentTimeMillis() / 1000 + 30) // expires in ~30s
    }.toString()

    @SuppressLint("SetTextI18n")
    private fun renderStatus() {
        val active = ppg.getActiveLiveActivities()
        val text = if (active.isEmpty()) {
            "No active live activities"
        } else {
            active.joinToString("\n") { la ->
                val c = la.configuration.content
                "${la.id}\n  ${c.homeTeamName} ${la.liveData.scoreText} ${c.awayTeamName}" +
                    "\n  status=${la.liveData.status}  active=${ppg.isLiveActivityActive(la.id)}"
            }
        }
        findViewById<TextView>(R.id.la_status).text = text
    }

    /**
     * targetSdk 35+ enforces edge-to-edge, so the ScrollView is laid out behind
     * the status/navigation bars and the ActionBar overlaps its first buttons.
     * Pad the root by the system bar insets so the content stays clickable.
     */
    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.la_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    companion object {
        private const val LA_ID = "demo-match-1"
        private const val HOME_TEAM = "Arsenal"
        private const val AWAY_TEAM = "Chelsea"
        private const val REQ_NOTIFICATIONS = 1001

        private val CONFIGURATION_JSON = """
            {
              "type":"FOOTBALL_MATCH_TRACKING",
              "content":{
                "title":"Premier League",
                "homeTeamName":"$HOME_TEAM",
                "homeTeamImage":"https://crests.football-data.org/57.png",
                "awayTeamName":"$AWAY_TEAM",
                "awayTeamImage":"https://crests.football-data.org/61.png"
              },
              "design":{
                "android":{
                  "hasTrackerIcon":true,
                  "progressBarColor":{"lightMode":"#4CAF50","darkMode":"#2E7D32"},
                  "breakTimeBarColor":{"lightMode":"#FFC107","darkMode":"#FFA000"}
                }
              },
              "statusLabels":{
                "PRE_MATCH":"Starting soon",
                "FIRST_HALF":"1st half",
                "HALF_TIME_BREAK":"Half time",
                "SECOND_HALF":"2nd half",
                "FULL_TIME":"Full time",
                "OTHER":"Match"
              },
              "actions":[
                {"type":"OPEN_APP","name":"Open"},
                {"type":"CLOSE","name":"Dismiss"}
              ],
              "timeout":{"minutes":150},
              "url":"app://com.fourf.ecommerce/match/$LA_ID"
            }
        """.trimIndent()
    }
}
