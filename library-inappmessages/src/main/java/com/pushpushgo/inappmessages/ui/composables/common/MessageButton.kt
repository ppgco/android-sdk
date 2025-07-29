package com.pushpushgo.inappmessages.ui.composables.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pushpushgo.inappmessages.model.FontStyle
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.model.InAppMessageStyle

@Composable
internal fun MessageButtons(
    actions: List<InAppMessageAction>,
    fontFamily: FontFamily,
    onAction: (InAppMessageAction) -> Unit,
    templateStyle: InAppMessageStyle,
) {
    val borderPadding = borderAdjustments(templateStyle)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = borderPadding, end = borderPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { action ->
            if (!action.enabled) return@forEach

            MessageButton(action, fontFamily, onAction, templateStyle)
        }
    }
}

@Composable
internal fun MessageButton(
    action: InAppMessageAction,
    fontFamily: FontFamily,
    onAction: (InAppMessageAction) -> Unit,
    style: InAppMessageStyle,
) {
    if (!action.enabled) return

    val borderPadding = borderAdjustments(style)

    Button(
        onClick = { onAction(action) },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = borderPadding, end = borderPadding),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.fromHex(action.backgroundColor),
            contentColor = Color.fromHex(action.textColor)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = Color.fromHex(action.borderColor)
        ),
        shape = parseBorderRadius(action.borderRadius),
        contentPadding = parsePadding(action.padding)
    ) {
        Text(
            text = action.text,
            color = Color.fromHex(action.textColor),
            fontFamily = fontFamily,
            fontWeight = FontWeight(action.fontWeight),
            fontSize = action.fontSizeSp,
            textDecoration = if (action.style == FontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
            fontStyle = if (action.style == FontStyle.ITALIC) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 1.5.em,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
internal fun CloseButton(
    style: InAppMessageStyle,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!style.closeIcon) return

    IconButton(
        onClick = onDismiss,
        modifier = modifier
            .size(style.closeIconWidth.pxToDp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.fromHex(style.closeIconColor),
            modifier = Modifier.size(style.closeIconWidth.pxToDp)
        )
    }
}
