package com.pushpushgo.sample.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.pushpushgo.sample.R
import kotlinx.android.synthetic.main.activity_click.*

class ClickActivity : AppCompatActivity(R.layout.activity_click) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("PPGO_SAMPLE", "$intent")

        info.text = intent?.data.toString()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        Log.d("PPGO_SAMPLE", "$intent")

        info.text = intent?.data.toString()
    }
}
