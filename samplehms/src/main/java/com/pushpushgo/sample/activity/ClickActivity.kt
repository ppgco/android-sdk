package com.pushpushgo.sample.activity

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pushpushgo.sample.R
import timber.log.Timber

class ClickActivity : AppCompatActivity(R.layout.activity_click) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.tag("PPGO_SAMPLE").d("$intent")

        findViewById<TextView>(R.id.info).text = intent?.data.toString()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Timber.tag("PPGO_SAMPLE").d("$intent")

        findViewById<TextView>(R.id.info).text = intent?.data.toString()
    }
}
