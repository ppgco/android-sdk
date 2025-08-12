package com.pushpushgo.sample.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pushpushgo.inappmessages.InAppMessagesSDK
import com.pushpushgo.sample.ui.Screen

@Composable
internal fun MainScreen(navController: NavHostController) {
  LaunchedEffect(Screen.Main.route) {
    InAppMessagesSDK.getInstance().showActiveMessages(Screen.Main.route)
  }

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = "In-App Library Sample", style = MaterialTheme.typography.headlineLarge)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Button(modifier = Modifier.padding(4.dp), onClick = { navController.navigate(Screen.Offer.route) }) {
        Text(text = "Go to Offer Screen", style = MaterialTheme.typography.bodyLarge)
      }

      Button(modifier = Modifier.padding(4.dp), onClick = { navController.navigate(Screen.Info.route) }) {
        Text(text = "Go to Info Screen", style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}
