package com.pushpushgo.sdk.sample.push.hms.activity

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.sample.push.hms.R
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date

class BeaconActivity : AppCompatActivity(R.layout.activity_beacon) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Timber.plant(
      object : Timber.Tree() {
        @SuppressLint("SetTextI18n", "SimpleDateFormat")
        override fun log(
          priority: Int,
          tag: String?,
          message: String,
          t: Throwable?,
        ) {
          if (priority > Log.VERBOSE) {
            with(findViewById<TextView>(R.id.logs)) {
              post { text = "${SimpleDateFormat("HH:mm:ss").format(Date())}: $message\n$text" }
            }
          }
        }
      },
    )

    findViewById<Button>(R.id.beacon1).setOnClickListener {
      PushNotifications
        .createBeacon()
        .set("see_invoice", true)
        .setCustomId("SEEI")
        .send()
    }

    findViewById<Button>(R.id.beacon2).setOnClickListener {
      PushNotifications
        .createBeacon()
        .set("basket_price", 299)
        .setCustomId("BP299")
        .send()
    }

    findViewById<Button>(R.id.beacon3).setOnClickListener {
      PushNotifications
        .createBeacon()
        .set("basket_price", 301)
        .setCustomId("BP301")
        .send()
    }

    findViewById<Button>(R.id.beacon4).setOnClickListener {
      PushNotifications
        .createBeacon()
        .appendTag("demo")
        .appendTag("${Build.MANUFACTURER} ${Build.MODEL}", "phone_model")
        .setCustomId("ATAGS")
        .send()
    }

    findViewById<Button>(R.id.beacon5).setOnClickListener {
      PushNotifications
        .createBeacon()
        .removeTag("desktop", "test")
        .setCustomId("RTAGS")
        .send()
    }

    findViewById<Button>(R.id.beacon6).setOnClickListener {
      PushNotifications
        .createBeacon()
        .setCustomId("TEST1")
        .send()
    }

    findViewById<Button>(R.id.beacon7).setOnClickListener {
      PushNotifications
        .createBeacon()
        .assignToGroup("test-group-123")
        .send()
    }

    findViewById<Button>(R.id.beacon8).setOnClickListener {
      PushNotifications
        .createBeacon()
        .unassignFromGroup("test-group-123")
        .send()
    }

    findViewById<Button>(R.id.beacon9).setOnClickListener {
      PushNotifications
        .createBeacon()
        .assignToGroup("group-to-join")
        .unassignFromGroup("group-to-leave")
        .setCustomId("GROUPS_TEST")
        .send()
    }
  }
}
