package com.pushpushgo.inappmessages.ui.composables

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (message.style.overlay) Color.Black.copy(alpha = 0.6f) else Color.Transparent
            )
            .padding(parsePadding(message.layout.margin))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.Center),
            shape = parseBorderRadius(message.style.borderRadius),
            colors = CardDefaults.cardColors(
                containerColor = Color.fromHex(message.style.backgroundColor)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (message.style.dropShadow) 8.dp else 0.dp
            ),
            border = if (message.style.border) {
                BorderStroke(
                    width = message.style.borderWidth.dp,
                    color = Color.fromHex(message.style.borderColor)
                )
            } else null
        ) {
            Column(
                modifier = Modifier.padding(parsePadding(message.layout.padding)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                message.image?.let { image ->
                    if (!image.hideOnMobile) {
                        MessageImage(image = image)
                        Spacer(modifier = Modifier.height(message.layout.spaceBetweenImageAndBody.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(parsePadding(message.layout.paddingBody)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MessageText(title = message.title, fontFamily = composeFontFamily)
                    Spacer(modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.dp))
                    MessageText(description = message.description, fontFamily = composeFontFamily)
                    Spacer(modifier = Modifier.height(message.layout.spaceBetweenContentAndActions.dp))
                    MessageButtons(actions = message.actions, fontFamily = composeFontFamily, onAction = onAction)
                }
            }
        }

        CloseButton(style = message.style, onDismiss = onDismiss)
    }
}

@Composable
private fun MessageImage(image: InAppMessageImage) {
    AsyncImage(
        model = image.url,
        contentDescription = "In-app message image",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun MessageText(title: InAppMessageTitle, fontFamily: FontFamily) {
    Text(
        text = title.text,
        fontFamily = fontFamily,
        color = Color.fromHex(title.color),
        fontSize = title.fontSize.sp,
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
        fontSize = description.fontSize.sp,
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = action.text,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight(action.fontWeight),
                    fontSize = action.fontSize.sp,
                    textDecoration = if (action.style == ModelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
                    fontStyle = if (action.style == ModelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CloseButton(style: InAppMessageStyle, onDismiss: () -> Unit) {
    if (!style.closeIcon) return

    IconButton(
        onClick = onDismiss,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(style.closeIconWidth.dp * 2) // Make clickable area larger
            .background(Color.Black.copy(alpha = 0.3f), shape = CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.fromHex(style.closeIconColor),
            modifier = Modifier.size(style.closeIconWidth.dp)
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
    if (borderRadius == null) return RoundedCornerShape(0.dp)
    val radius = borderRadius.removeSuffix("px").toFloatOrNull() ?: 0f
    return RoundedCornerShape(radius.dp)
}

private fun parsePadding(padding: String?): PaddingValues {
    if (padding == null) return PaddingValues(0.dp)
    val parts = padding.replace("px", "", ignoreCase = true).split(' ').mapNotNull { it.trim().toFloatOrNull() }
    return when (parts.size) {
        1 -> PaddingValues(parts[0].dp)
        2 -> PaddingValues(vertical = parts[0].dp, horizontal = parts[1].dp)
        3 -> PaddingValues(top = parts[0].dp, start = parts[1].dp, end = parts[1].dp, bottom = parts[2].dp)
        4 -> PaddingValues(top = parts[0].dp, end = parts[1].dp, bottom = parts[2].dp, start = parts[3].dp)
        else -> PaddingValues(0.dp)
    }
}

// Helper for TextAlign from string
private fun TextAlign.Companion.fromString(value: String): TextAlign = when (value.lowercase()) {
    "start" -> Start
    "end" -> End
    "center" -> Center
    else -> Center
}
