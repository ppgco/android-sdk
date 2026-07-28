package com.pushpushgo.sdk.sample.push.firebase.activity

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pushpushgo.sdk.push.Beacon
import com.pushpushgo.sdk.push.BeaconBuilder
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.sample.push.firebase.R
import kotlinx.coroutines.launch
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
      BeaconBuilder()
        .set("see_invoice", true)
        .setCustomId("SEEI")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon2).setOnClickListener {
      BeaconBuilder()
        .set("basket_price", 299)
        .setCustomId("BP299")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon3).setOnClickListener {
      BeaconBuilder()
        .set("basket_price", 301)
        .setCustomId("BP301")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon4).setOnClickListener {
      BeaconBuilder()
        .appendTag("demo")
        .appendTag("${Build.MANUFACTURER} ${Build.MODEL}", "phone_model")
        .setCustomId("ATAGS")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon5).setOnClickListener {
      BeaconBuilder()
        .removeTag("desktop", "test")
        .setCustomId("RTAGS")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon6).setOnClickListener {
      BeaconBuilder()
        .setCustomId("TEST1")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon7).setOnClickListener {
      BeaconBuilder()
        .assignToGroup("test-group-123")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon8).setOnClickListener {
      BeaconBuilder()
        .unassignFromGroup("test-group-123")
        .build()
        .also(::sendBeacon)
    }

    findViewById<Button>(R.id.beacon9).setOnClickListener {
      BeaconBuilder()
        .assignToGroup("group-to-join")
        .unassignFromGroup("group-to-leave")
        .setCustomId("GROUPS_TEST")
        .build()
        .also(::sendBeacon)
    }
  }

  private fun sendBeacon(beacon: Beacon) {
    lifecycleScope.launch {
      PushNotifications.sendBeacon(beacon)
    }
  }
}
