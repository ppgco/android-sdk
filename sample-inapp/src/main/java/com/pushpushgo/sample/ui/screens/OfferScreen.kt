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
internal fun OfferScreen(navController: NavHostController) {
  LaunchedEffect(Screen.Offer.route) {
    InAppMessagesSDK.getInstance().showActiveMessages(Screen.Offer.route)
  }

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = "Offer screen", style = MaterialTheme.typography.headlineLarge)

    Button(modifier = Modifier.padding(4.dp), onClick = {
      if (navController.previousBackStackEntry != null) {
        navController.popBackStack()
      }
    }) {
      Text(text = "Go back", style = MaterialTheme.typography.bodyLarge)
    }
  }
}
