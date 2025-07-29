package com.pushpushgo.inappmessages.ui.composables.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.pushpushgo.inappmessages.model.FontStyle
import com.pushpushgo.inappmessages.model.InAppMessageDescription
import com.pushpushgo.inappmessages.model.InAppMessageTitle

@Composable
internal fun MessageTitle(
    title: InAppMessageTitle,
    fontFamily: FontFamily,
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = title.text,
        fontFamily = fontFamily,
        color = Color.fromHex(title.color),
        fontSize = title.fontSizeSp,
        fontWeight = FontWeight(title.fontWeight),
        textAlign = TextAlign.fromString(title.alignment.name),
        fontStyle = if (title.style == FontStyle.ITALIC) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
        textDecoration = if (title.style == FontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None
    )
}


@Composable
internal fun MessageDescription(
    description: InAppMessageDescription,
    fontFamily: FontFamily,
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = description.text,
        fontFamily = fontFamily,
        color = Color.fromHex(description.color),
        fontSize = description.fontSizeSp,
        fontWeight = FontWeight(description.fontWeight),
        textAlign = TextAlign.fromString(description.alignment.name),
        fontStyle = if (description.style == FontStyle.ITALIC) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
        textDecoration = if (description.style == FontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None
    )
}
