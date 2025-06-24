package com.pushpushgo.inappmessages.ui.composables

import android.annotation.SuppressLint
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import androidx.core.graphics.toColorInt

@Composable
fun InAppMessageContent(
    message: InAppMessage,
    onDismiss: () -> Unit,
    onAction: (InAppMessageAction) -> Unit,
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent) // Outer box is transparent
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(message.style.borderRadius?.toFloatOrNull() ?: 0f),
            colors = CardDefaults.cardColors(
                containerColor = Color.fromHex(message.style.backgroundColor ?: "#FFFFFF")
            ),
            border = if (message.style.border) {
                BorderStroke(
                    width = (message.style.borderWidth ?: 1).dp,
                    color = Color.fromHex(message.style.borderColor ?: "#000000")
                )
            } else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(parsePadding(message.layout.paddingBody)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                message.image?.let {
                    MessageImage(image = it)
                    Spacer(modifier = Modifier.height(message.layout.spaceBetweenImageAndBody.dp))
                }
                MessageText(title = message.title)
                Spacer(modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.dp))
                MessageText(description = message.description)
                Spacer(modifier = Modifier.height(message.layout.spaceBetweenContentAndActions.dp))
                MessageButtons(actions = message.actions, onAction = onAction)
            }
        }

        CloseButton(style = message.style, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MessageImage(image: InAppMessageImage) {
    GlideImage(
        model = image.url,
        contentDescription = "In-App Message Image",
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp) // Default height, can be customized later
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun MessageText(title: InAppMessageTitle) {
    Text(
        text = title.text,
        color = Color.fromHex(title.color),
        fontSize = title.fontSize.sp,
        fontWeight = FontWeight(title.fontWeight),
        textAlign = TextAlign.fromString(title.alignment.name),
        fontStyle = if (title.style == "italic") FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (title.style == "underline") TextDecoration.Underline else TextDecoration.None
    )
}

@Composable
private fun MessageText(description: InAppMessageDescription) {
    Text(
        text = description.text,
        color = Color.fromHex(description.color),
        fontSize = description.fontSize.sp,
        fontWeight = FontWeight(description.fontWeight),
        textAlign = TextAlign.fromString(description.alignment.name),
        fontStyle = if (description.style == "italic") FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (description.style == "underline") TextDecoration.Underline else TextDecoration.None
    )
}

@Composable
private fun MessageButtons(
    actions: List<InAppMessageAction>,
    onAction: (InAppMessageAction) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        actions.forEach { action ->
            Button(
                onClick = { onAction(action) },
                shape = RoundedCornerShape(action.borderRadius?.toFloatOrNull() ?: 8f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.fromHex(action.backgroundColor)
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = parsePadding(action.padding)
            ) {
                Text(
                    text = action.text,
                    color = Color.fromHex(action.textColor),
                    fontSize = action.fontSize.sp,
                    fontWeight = FontWeight(action.fontWeight),
                    fontStyle = if (action.style == "italic") FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (action.style == "underline") TextDecoration.Underline else TextDecoration.None
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
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.fromHex(style.closeIconColor ?: "#000000"),
            modifier = Modifier.size((style.closeIconWidth ?: 16).dp)
        )
    }
}

// Helper to convert hex string to Color, with fallback
private fun Color.Companion.fromHex(hex: String): Color {
    return try {
        Color(hex.toColorInt())
    } catch (e: Exception) {
        Log.w("InAppMessageContent", "Invalid color hex: '$hex'. Using Black.", e)
        Black // Fallback color
    }
}

private fun parsePadding(padding: String?): PaddingValues {
    if (padding == null) return PaddingValues(0.dp)
    val parts = padding.replace("px", "", ignoreCase = true).split(' ').mapNotNull { it.trim().toFloatOrNull() }
    return when (parts.size) {
        1 -> PaddingValues(parts[0].dp)
        2 -> PaddingValues(vertical = parts[0].dp, horizontal = parts[1].dp)
        3 -> PaddingValues(top = parts[0].dp, horizontal = parts[1].dp, bottom = parts[2].dp)
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



