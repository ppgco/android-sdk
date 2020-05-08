package com.pushpushgo.sample.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.pushpushgo.sample.R
import com.pushpushgo.sdk.PushPushGo
import kotlinx.android.synthetic.main.activity_beacon.*
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class BeaconActivity : AppCompatActivity(R.layout.activity_beacon) {

    private val ppg by lazy { PushPushGo.getInstance() }

    private var lastLogs = mutableListOf<String>()

    @SuppressLint("SimpleDateFormat")
    @Synchronized
    private fun log(message: String) {
        lastLogs.add(0, "${SimpleDateFormat("HH:mm:ss").format(Date())}: $message")

        with(logs) {
            post { text = lastLogs.joinToString("\n") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority > Log.VERBOSE) log(message)
            }
        })

        beacon1.setOnClickListener {
            ppg.createBeacon()
                .appendTag("jacek")
                .appendTag("sabina", "dziewczyna")
                .set("see_invoice", true)
                .setCustomId("CRMCI")
                .removeTag("marek", "janek")
                .send()
        }

        beacon2.setOnClickListener {
            ppg.createBeacon()
                .set("basket_price", 299)
                .setCustomId("CRMCI")
                .send()
        }

        beacon3.setOnClickListener {
            ppg.createBeacon()
                .set("basket_price", 301)
                .setCustomId("CRMCI")
                .send()
        }
    }
}
