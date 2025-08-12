package com.pushpushgo.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.pushpushgo.inappmessages.InAppMessagesSDK
import com.pushpushgo.sample.ui.MainNavHost
import com.pushpushgo.sample.ui.theme.AndroidsdkTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    InAppMessagesSDK.initialize(
      application = application,
      projectId = BuildConfig.PPG_PROJECT_ID,
      apiKey = BuildConfig.PPG_API_KEY,
      debug = true,
    )

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
