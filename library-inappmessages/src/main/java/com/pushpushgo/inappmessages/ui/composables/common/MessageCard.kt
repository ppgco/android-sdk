package com.pushpushgo.inappmessages.ui.composables.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pushpushgo.inappmessages.model.InAppMessage

@Composable
internal fun MessageCard(message: InAppMessage, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = parseBorderRadius(message.style.borderRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color.fromHex(message.style.backgroundColor)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (message.style.dropShadow) 8.dp else 0.dp
        ),
        border = if (message.style.border && message.style.borderWidth > 0) {
            BorderStroke(
                width = message.style.borderWidth.pxToDp,
                color = Color.fromHex(message.style.borderColor)
            )
        } else null,
    ) {
        content()
    }
}
