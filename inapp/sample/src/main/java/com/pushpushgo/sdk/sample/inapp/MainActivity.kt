package com.pushpushgo.sdk.sample.inapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.pushpushgo.sdk.inapp.InAppMessages
import com.pushpushgo.sdk.sample.inapp.ui.MainNavHost
import com.pushpushgo.sdk.sample.inapp.ui.theme.AndroidsdkTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    InAppMessages.initialize(application)

    enableEdgeToEdge()
    setContent {
      AndroidsdkTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          Box(modifier = Modifier.padding(innerPadding)) {
            MainNavHost()
          }
        }
      }
    }
  }
}
