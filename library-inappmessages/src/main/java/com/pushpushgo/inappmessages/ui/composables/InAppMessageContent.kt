package com.pushpushgo.inappmessages.ui.composables

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.pushpushgo.inappmessages.model.InAppAction
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.template.*
import kotlinx.serialization.json.Json
import androidx.core.graphics.toColorInt

private val jsonParser = Json { ignoreUnknownKeys = true }

// Element type constants
private const val ELEMENT_IMAGE = "IMAGE"
private const val ELEMENT_TITLE = "TITLE"
private const val ELEMENT_DESCRIPTION = "DESCRIPTION"
private const val ELEMENT_BUTTONS = "BUTTONS"

@Composable
fun InAppMessageContent(
    message: InAppMessage,
    onDismiss: () -> Unit,
    onAction: (InAppAction) -> Unit,
) {
    val template = remember(message.template) {
        message.template?.takeIf { it.isNotBlank() }?.let {
            try {
                jsonParser.decodeFromString<InAppTemplate>(it)
            } catch (e: Exception) {
                Log.e("InAppMessageContent", "Failed to parse template JSON", e)
                null // Fallback to default if parsing fails
            }
        } ?: defaultTemplate // Use default if template string is null or blank
    }

    val currentContainerStyle = template.containerStyle ?: defaultTemplate.containerStyle!!
    val currentImageStyle = template.imageStyle ?: defaultTemplate.imageStyle!!
    val currentTextStyles = template.textStyles ?: defaultTemplate.textStyles!!
    val currentButtonContainerStyle = template.buttonContainerStyle ?: defaultTemplate.buttonContainerStyle!!
    val currentCloseButtonStyle = template.closeButtonStyle ?: defaultTemplate.closeButtonStyle!!

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent) // Outer box is transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp) // Overall padding for the message card
                .background(
                    color = Color.fromHex(currentContainerStyle.backgroundColor ?: "#FFFFFF"),
                    shape = RoundedCornerShape((currentContainerStyle.cornerRadius ?: 0.0).dp)
                )
                .clip(RoundedCornerShape((currentContainerStyle.cornerRadius ?: 0.0).dp))
                .then(createPaddingModifier(currentContainerStyle.padding)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            (template.elementOrder ?: defaultTemplate.elementOrder)?.forEach { elementName ->
                when (elementName) {
                    ELEMENT_IMAGE -> MessageImage(message, currentImageStyle)
                    ELEMENT_TITLE -> MessageText(message.title, currentTextStyles.title ?: defaultTemplate.textStyles!!.title!!, isTitle = true)
                    ELEMENT_DESCRIPTION -> MessageText(message.description, currentTextStyles.description ?: defaultTemplate.textStyles!!.description!!)
                    ELEMENT_BUTTONS -> MessageButtons(message.actions, currentButtonContainerStyle, onAction)
                }
            }
        }

        if (message.dismissible && currentCloseButtonStyle.visibility == "visible") {
            CloseButton(currentCloseButtonStyle, onDismiss)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MessageImage(message: InAppMessage, style: ImageStyle) {
    if (style.visibility == "visible" && !message.image.isNullOrBlank() && message.image != "null") {
        GlideImage(
            model = message.image,
            contentDescription = "In-app message image",
            modifier = Modifier
                .height((style.size?.height?.toIntOrNull() ?: defaultTemplate.imageStyle!!.size!!.height!!.toIntOrNull()!!).dp)
                .fillMaxWidth()
                .then(createPaddingModifier(style.padding ?: defaultTemplate.imageStyle!!.padding!!))
                .clip(RoundedCornerShape((style.cornerRadius ?: 0.0).dp)),
            contentScale = style.scaleType?.let { ContentScale.fromString(it) } ?: ContentScale.Crop
        )
    }
}

@Composable
private fun MessageText(text: String?, style: TextStyle, isTitle: Boolean = false) {
    if (!text.isNullOrEmpty()) {
        val defaultStyle = if (isTitle) defaultTemplate.textStyles!!.title!! else defaultTemplate.textStyles!!.description!!
        Text(
            text = text,
            color = Color.fromHex(style.fontColor ?: defaultStyle.fontColor!!),
            fontSize = (style.fontSize ?: defaultStyle.fontSize!!).sp,
            fontWeight = if (style.isBold == true) FontWeight.Bold else FontWeight.Normal,
            textAlign = style.alignment?.let { TextAlign.fromString(it) } ?: TextAlign.Center,
            maxLines = style.maxLines ?: Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = (style.marginTop ?: 0).dp, bottom = (style.marginBottom ?: 0).dp)
        )
    }
}

@Composable
private fun MessageButtons(
    actions: List<InAppAction>,
    styles: ButtonContainerStyle,
    onAction: (InAppAction) -> Unit
) {
    val defaultButtonContainerStyle = defaultTemplate.buttonContainerStyle!!
    val defaultButtonStyle = defaultButtonContainerStyle.button!!

    val columnModifierPadding = styles.padding ?: defaultButtonContainerStyle.padding!!
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = (styles.marginTop ?: 0).dp)
            .then(createPaddingModifier(columnModifierPadding)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((styles.spacing ?: 0).dp)
    ) {
        actions.forEach { action ->
            val buttonStyle = styles.button ?: defaultButtonStyle
            val currentButtonPadding = buttonStyle.padding ?: defaultButtonStyle.padding!!
            Button(
                onClick = { onAction(action) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(createPaddingModifier(currentButtonPadding)),
                shape = RoundedCornerShape((buttonStyle.cornerRadius ?: defaultButtonStyle.cornerRadius!!).dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.fromHex(buttonStyle.backgroundColor ?: defaultButtonStyle.backgroundColor!!),
                    contentColor = Color.fromHex(buttonStyle.fontColor ?: defaultButtonStyle.fontColor!!)
                )
            ) {
                Text(
                    text = action.title ?: "Action",
                    fontSize = (buttonStyle.fontSize ?: defaultButtonStyle.fontSize!!).sp,
                    fontWeight = buttonStyle.fontWeight?.let { FontWeight.fromString(it) } ?: FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CloseButton(style: CloseButtonStyle, onDismiss: () -> Unit) {
    val defaultStyle = defaultTemplate.closeButtonStyle!!
    IconButton(
        onClick = onDismiss,
        modifier = Modifier
            .align(style.position?.let { Alignment.fromString(it) } ?: Alignment.TopEnd)
            .then(createPaddingModifier(style.padding ?: defaultStyle.padding!!))
            .size(
                width = (style.size?.width?.toIntOrNull() ?: defaultStyle.size!!.width!!.toIntOrNull()!!).dp,
                height = (style.size?.height?.toIntOrNull() ?: defaultStyle.size!!.height!!.toIntOrNull()!!).dp
            )
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.fromHex(style.color ?: defaultStyle.color!!)
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

// Helper to create padding modifier
@SuppressLint("ModifierFactoryExtensionFunction")
private fun createPaddingModifier(paddingData: Padding?): Modifier {
    if (paddingData == null) return Modifier // Return empty Modifier if paddingData is null

    val startDp = (paddingData.start ?: paddingData.horizontal ?: 0).dp
    val topDp = (paddingData.top ?: paddingData.vertical ?: 0).dp
    val endDp = (paddingData.end ?: paddingData.horizontal ?: 0).dp
    val bottomDp = (paddingData.bottom ?: paddingData.vertical ?: 0).dp

    return Modifier.padding(
        start = startDp,
        top = topDp,
        end = endDp,
        bottom = bottomDp
    )
}

// Helper for TextAlign from string
private fun TextAlign.Companion.fromString(value: String): TextAlign = when (value.lowercase()) {
    "start" -> Start
    "end" -> End
    "center" -> Center
    else -> Center
}

// Helper for ContentScale from string
private fun ContentScale.Companion.fromString(value: String): ContentScale = when (value.lowercase()) {
    "fit" -> Fit
    "fillwidth" -> FillWidth
    "fillheight" -> FillHeight
    "crop" -> Crop
    "inside" -> Inside
    else -> Crop
}

// Helper for FontWeight from string
private fun FontWeight.Companion.fromString(value: String): FontWeight = when (value.lowercase()) {
    "bold" -> Bold
    "normal" -> Normal
    else -> Normal
}

// Helper for Alignment from string (simplified for BoxScope)
private fun Alignment.Companion.fromString(value: String): Alignment = when (value.lowercase()) {
    "topstart" -> TopStart
    "topcenter" -> TopCenter
    "topend" -> TopEnd
    "centerstart" -> CenterStart
    "center" -> Center
    "centerend" -> CenterEnd
    "bottomstart" -> BottomStart
    "bottomcenter" -> BottomCenter
    "bottomend" -> BottomEnd
    else -> TopEnd // Default for close button
}


private val defaultTemplate = InAppTemplate(
    schemaVersion = "1.0",
    elementOrder = listOf(ELEMENT_IMAGE, ELEMENT_TITLE, ELEMENT_DESCRIPTION, ELEMENT_BUTTONS),
    containerStyle = ContainerStyle(
        backgroundColor = "#FFFFFF",
        cornerRadius = 12.0,
        padding = Padding(start = 16, top = 16, end = 16, bottom = 16)
    ),
    imageStyle = ImageStyle(
        visibility = "visible",
        scaleType = "crop",
        cornerRadius = 8.0,
        size = Size(height = "150", width = "0"),
        padding = Padding(bottom = 16),
        marginTop = 0,
        marginBottom = 0
    ),
    textStyles = TextStyles(
        title = TextStyle(
            fontColor = "#000000",
            fontSize = 20,
            isBold = true,
            alignment = "center",
            maxLines = 2,
            marginTop = 0,
            marginBottom = 8
        ),
        description = TextStyle(
            fontColor = "#333333",
            fontSize = 16,
            isBold = false,
            alignment = "center",
            maxLines = 5,
            marginTop = 0,
            marginBottom = 16
        )
    ),
    buttonContainerStyle = ButtonContainerStyle(
        orientation = "vertical",
        spacing = 8,
        marginTop = 0,
        padding = Padding(horizontal = 16),
        button = ButtonStyle(
            fontColor = "#FFFFFF",
            fontSize = 16,
            backgroundColor = "#007AFF",
            cornerRadius = 8.0,
            padding = Padding(vertical = 12),
            fontWeight = "bold"
        )
    ),
    closeButtonStyle = CloseButtonStyle(
        visibility = "visible",
        color = "#888888",
        position = "topend",
        size = Size(width = "24", height = "24"),
        padding = Padding(top = 8, end = 8)
    )
)
