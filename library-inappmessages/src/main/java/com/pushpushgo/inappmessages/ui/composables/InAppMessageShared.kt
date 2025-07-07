package com.pushpushgo.inappmessages.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.model.InAppMessageDescription
import com.pushpushgo.inappmessages.model.InAppMessageImage
import com.pushpushgo.inappmessages.model.InAppMessageStyle
import com.pushpushgo.inappmessages.model.InAppMessageTitle
import com.pushpushgo.inappmessages.model.FontStyle as ModelFontStyle

@Composable
internal fun MessageImage(
    image: InAppMessageImage,
    modifier: Modifier,
    templateStyle: InAppMessageStyle
) {
    val borderPadding = borderAdjustments(templateStyle)
    AsyncImage(
        model = image.url,
        contentDescription = "In-app message image",
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .padding(top = borderPadding, start = borderPadding, end = borderPadding),
        contentScale = ContentScale.Fit,
        alignment = Alignment.TopCenter
    )
}

@Composable
internal fun MessageText(
    title: InAppMessageTitle,
    fontFamily: FontFamily,
    templateStyle: InAppMessageStyle,
) {
    val borderPadding = borderAdjustments(templateStyle)
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = borderPadding, end = borderPadding),
        text = title.text,
        fontFamily = fontFamily,
        color = Color.fromHex(title.color),
        fontSize = title.fontSizeSp,
        fontWeight = FontWeight(title.fontWeight),
        textAlign = TextAlign.fromString(title.alignment.name),
        fontStyle = if (title.style == ModelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (title.style == ModelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None
    )
}

@Composable
internal fun MessageText(
    description: InAppMessageDescription,
    fontFamily: FontFamily,
    templateStyle: InAppMessageStyle,
) {
    val borderPadding = borderAdjustments(templateStyle)
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = borderPadding, end = borderPadding),
        text = description.text,
        fontFamily = fontFamily,
        color = Color.fromHex(description.color),
        fontSize = description.fontSizeSp,
        fontWeight = FontWeight(description.fontWeight),
        textAlign = TextAlign.fromString(description.alignment.name),
        fontStyle = if (description.style == ModelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (description.style == ModelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None
    )
}

@Composable
internal fun MessageButtons(
    actions: List<InAppMessageAction>,
    fontFamily: FontFamily,
    onAction: (InAppMessageAction) -> Unit,
    templateStyle: InAppMessageStyle
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
            textDecoration = if (action.style == ModelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
            fontStyle = if (action.style == ModelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
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
    val borderPadding = borderAdjustments(style)
    if (!style.closeIcon) return

    IconButton(
        onClick = onDismiss,
        modifier = modifier
            .size(style.closeIconWidth.pxToDp * 2)
            .padding(borderPadding)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.fromHex(style.closeIconColor),
            modifier = Modifier.size(style.closeIconWidth.pxToDp)
        )
    }
}

internal fun Color.Companion.fromHex(hex: String): Color {
    return try {
        Color(hex.toColorInt())
    } catch (_: IllegalArgumentException) {
        Unspecified
    }
}

internal fun removePxFromString(value: String): List<Float> {
    return value.replace("px", "", ignoreCase = true)
        .split(' ')
        .mapNotNull { it.trim().toFloatOrNull() }
}

@Composable
internal fun parseBorderRadius(borderRadius: String?): Shape {
    if (borderRadius.isNullOrBlank()) return RoundedCornerShape(0.pxToDp)
    val parts = removePxFromString(borderRadius)

    return when (parts.size) {
        1 -> RoundedCornerShape(parts[0].pxToDp)
        2 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[0].pxToDp,
            bottomStart = parts[1].pxToDp,
            bottomEnd = parts[1].pxToDp
        )

        3 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[1].pxToDp,
            bottomStart = parts[2].pxToDp,
            bottomEnd = parts[1].pxToDp
        )

        4 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[1].pxToDp,
            bottomStart = parts[3].pxToDp,
            bottomEnd = parts[2].pxToDp
        )

        else -> RoundedCornerShape(0.pxToDp)
    }
}

@Composable
internal fun parsePadding(padding: String?): PaddingValues {
    if (padding.isNullOrBlank()) return PaddingValues(0.dp)
    val parts = removePxFromString(padding)

    return when (parts.size) {
        1 -> PaddingValues(parts[0].pxToDp)
        2 -> PaddingValues(vertical = parts[0].pxToDp, horizontal = parts[1].pxToDp)
        3 -> PaddingValues(
            top = parts[0].pxToDp,
            start = parts[1].pxToDp,
            end = parts[1].pxToDp,
            bottom = parts[2].pxToDp
        )

        4 -> PaddingValues(
            top = parts[0].pxToDp,
            end = parts[1].pxToDp,
            bottom = parts[2].pxToDp,
            start = parts[3].pxToDp
        )

        else -> PaddingValues(0.pxToDp)
    }
}

val InAppMessageTitle.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.3f).sp

val InAppMessageDescription.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.3f).sp

val InAppMessageAction.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.3f).sp

val Int.pxToDp: Dp
    get() = (this * 1.333f).dp

val Float.pxToDp: Dp
    get() = (this * 1.333f).dp

internal fun TextAlign.Companion.fromString(value: String): TextAlign {
    return when (value.lowercase()) {
        "left" -> Left
        "right" -> Right
        "center" -> Center
        "justify" -> Justify
        "start" -> Start
        "end" -> End
        else -> Start
    }
}

internal fun borderAdjustments(styleSettings: InAppMessageStyle): Dp {
    val borderPadding: Dp = if (styleSettings.border) {
        styleSettings.borderWidth.pxToDp
    } else {
        0.dp
    }
    return borderPadding
}
