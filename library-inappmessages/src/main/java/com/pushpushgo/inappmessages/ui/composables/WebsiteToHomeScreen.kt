package com.pushpushgo.inappmessages.ui.composables

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.FontFamily as ModelFontFamily
import com.pushpushgo.inappmessages.model.FontStyle as ModelFontStyle
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.model.InAppMessageDescription
import com.pushpushgo.inappmessages.model.InAppMessageImage
import com.pushpushgo.inappmessages.model.InAppMessageStyle
import com.pushpushgo.inappmessages.model.InAppMessageTitle
import androidx.core.graphics.toColorInt

@Composable
fun WebsiteToHomeScreen(
    message: InAppMessage,
    onDismiss: () -> Unit,
    onAction: (InAppMessageAction) -> Unit,
) {
    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

    val fontName = when (message.style.fontFamily) {
        ModelFontFamily.ROBOTO -> "Roboto"
        ModelFontFamily.OPEN_SANS -> "Open Sans"
        ModelFontFamily.MONTSERRAT -> "Montserrat"
        ModelFontFamily.INTER -> "Inter"
        ModelFontFamily.POPPINS -> "Poppins"
        ModelFontFamily.LATO -> "Lato"
        ModelFontFamily.PLAYFAIR_DISPLAY -> "Playfair Display"
        ModelFontFamily.FIRA_SANS -> "Fira Sans"
        ModelFontFamily.ARIAL -> "Arial"
        ModelFontFamily.GEORGIA -> "Georgia"
    }

    val composeFontFamily = FontFamily(
        Font(googleFont = GoogleFont(name = fontName), fontProvider = provider)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (message.style.overlay) Color.Black.copy(alpha = 0.6f) else Color.Transparent)
            .padding(parsePadding(message.layout.margin)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = parseBorderRadius(message.style.borderRadius),
            colors = CardDefaults.cardColors(
                containerColor = Color.fromHex(message.style.backgroundColor)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (message.style.dropShadow) 8.dp else 0.dp
            ),
            border = if (message.style.border) {
                BorderStroke(
                    width = message.style.borderWidth.pxToDp,
                    color = Color.fromHex(message.style.borderColor)
                )
            } else null
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(parsePadding(message.layout.padding)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        message.image?.let { image ->
                            if (!image.hideOnMobile) {
                                MessageImage(image = image)
                                Spacer(modifier = Modifier.height(message.layout.spaceBetweenImageAndBody.pxToDp))
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(parsePadding(message.layout.paddingBody)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MessageText(title = message.title, fontFamily = composeFontFamily)
                        Spacer(modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.pxToDp))
                        MessageText(description = message.description, fontFamily = composeFontFamily)
                        Spacer(modifier = Modifier.height(message.layout.spaceBetweenContentAndActions.pxToDp))
                        MessageButtons(actions = message.actions, fontFamily = composeFontFamily, onAction = onAction)
                    }
                }
                // The IconButton has a large touch area. To align the visible icon to the corner,
                // offset the button by the size of its invisible padding, minus a visual
                // padding to pull it away from the clipped corner.
                val iconSize = message.style.closeIconWidth.pxToDp
                val padding = 30.dp
                val offset = iconSize - padding
                CloseButton(
                    style = message.style,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = offset, y = -offset)
                )
            }
        }
    }
}

@Composable
private fun MessageImage(image: InAppMessageImage) {
    AsyncImage(
        model = image.url,
        contentDescription = "In-app message image",
        modifier = Modifier
            .fillMaxWidth(),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun MessageText(title: InAppMessageTitle, fontFamily: FontFamily) {
    Text(
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
private fun MessageText(description: InAppMessageDescription, fontFamily: FontFamily) {
    Text(
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
private fun MessageButtons(
    actions: List<InAppMessageAction>,
    fontFamily: FontFamily,
    onAction: (InAppMessageAction) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        actions.forEach { action ->
            Button(
                onClick = { onAction(action) },
                shape = parseBorderRadius(action.borderRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.fromHex(action.backgroundColor),
                    contentColor = Color.fromHex(action.textColor)
                ),
                border = BorderStroke(1.dp, Color.fromHex(action.borderColor)),
                contentPadding = parsePadding(action.padding),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = action.text,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight(action.fontWeight),
                    fontSize = action.fontSizeSp,
                    textDecoration = if (action.style == ModelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
                    fontStyle = if (action.style == ModelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal
                )
            }
        }
    }
}

@Composable
private fun CloseButton(
    style: InAppMessageStyle,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!style.closeIcon) return

    IconButton(
        onClick = onDismiss,
        modifier = modifier
            .size(style.closeIconWidth.pxToDp * 2) // Make clickable area larger
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.fromHex(style.closeIconColor),
            modifier = Modifier.size(style.closeIconWidth.pxToDp)
        )
    }
}

// Helper to convert hex string to Color, with fallback
private fun Color.Companion.fromHex(hex: String): Color {
    return try {
        Color(hex.toColorInt())
    } catch (e: IllegalArgumentException) {
        Log.w("Color.fromHex", "Invalid color hex: '$hex'. Using Black.", e)
        Black
    }
}

private fun parseBorderRadius(borderRadius: String?): Shape {
    if (borderRadius.isNullOrBlank()) return RoundedCornerShape(0.pxToDp)
    val parts = borderRadius.replace("px", "", ignoreCase = true)
        .split(' ')
        .mapNotNull { it.trim().toFloatOrNull() }

    return when (parts.size) {
        1 -> RoundedCornerShape(parts[0].pxToDp)
        2 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[1].pxToDp,
            bottomEnd = parts[0].pxToDp,
            bottomStart = parts[1].pxToDp
        )
        3 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[1].pxToDp,
            bottomEnd = parts[2].pxToDp,
            bottomStart = parts[1].pxToDp
        )
        4 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[1].pxToDp,
            bottomEnd = parts[2].pxToDp,
            bottomStart = parts[3].pxToDp
        )
        else -> RoundedCornerShape(0.pxToDp)
    }
}

private fun parsePadding(padding: String?): PaddingValues {
    if (padding.isNullOrBlank()) return PaddingValues(0.pxToDp)
    val parts = padding.replace("px", "", ignoreCase = true).split(' ').mapNotNull { it.trim().toFloatOrNull() }
    return when (parts.size) {
        1 -> PaddingValues(parts[0].pxToDp)
        2 -> PaddingValues(vertical = parts[0].pxToDp, horizontal = parts[1].pxToDp)
        3 -> PaddingValues(top = parts[0].pxToDp, start = parts[1].pxToDp, end = parts[1].pxToDp, bottom = parts[2].pxToDp)
        4 -> PaddingValues(top = parts[0].pxToDp, end = parts[1].pxToDp, bottom = parts[2].pxToDp, start = parts[3].pxToDp)
        else -> PaddingValues(0.pxToDp)
    }
}

private val InAppMessageTitle.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.6f).sp

private val InAppMessageDescription.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.6f).sp

private val InAppMessageAction.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.6f).sp

private val Int.pxToDp: Dp
    get() = (this * 1.6f).dp

private val Float.pxToDp: Dp
    get() = (this * 1.6f).dp

// Helper for TextAlign from string
private fun TextAlign.Companion.fromString(value: String): TextAlign = when (value.lowercase()) {
    "start" -> Start
    "end" -> End
    "center" -> Center
    else -> Center
}
