package com.pushpushgo.sample.activity

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pushpushgo.sample.R
import com.pushpushgo.sdk.PushPushGo
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class BeaconActivity : AppCompatActivity(R.layout.activity_beacon) {

    private val ppg by lazy { PushPushGo.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(object : Timber.Tree() {
            @SuppressLint("SetTextI18n", "SimpleDateFormat")
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority > Log.VERBOSE) with(findViewById<TextView>(R.id.logs)) {
                    post { text = "${SimpleDateFormat("HH:mm:ss").format(Date())}: $message\n$text" }
                }
            }
        })

        findViewById<Button>(R.id.beacon1).setOnClickListener {
            ppg.createBeacon()
                .set("see_invoice", true)
                .setCustomId("SEEI")
                .send()
        }

        findViewById<Button>(R.id.beacon2).setOnClickListener {
            ppg.createBeacon()
                .set("basket_price", 299)
                .setCustomId("BP299")
                .send()
        }

        findViewById<Button>(R.id.beacon3).setOnClickListener {
            ppg.createBeacon()
                .set("basket_price", 301)
                .setCustomId("BP301")
                .send()
        }

        findViewById<Button>(R.id.beacon4).setOnClickListener {
            ppg.createBeacon()
                .appendTag("demo")
                .appendTag("${Build.MANUFACTURER} ${Build.MODEL}", "phone_model")
                .setCustomId("ATAGS")
                .send()
        }

        findViewById<Button>(R.id.beacon5).setOnClickListener {
            ppg.createBeacon()
                .removeTag("desktop", "test")
                .setCustomId("RTAGS")
                .send()
        }

        findViewById<Button>(R.id.beacon6).setOnClickListener {
            ppg.createBeacon()
                .setCustomId("TEST1")
                .send()
        }
    }
}
