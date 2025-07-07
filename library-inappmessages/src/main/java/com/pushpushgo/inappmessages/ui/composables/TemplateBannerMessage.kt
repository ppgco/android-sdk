package com.pushpushgo.inappmessages.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.FontFamily as ModelFontFamily
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction


/**
 * Banner-style message template for EXIT_INTENT_ECOMM
 * This template creates a compact banner with an image, text, and buttons
 * Typically positioned at top-right of the screen
 */
@Composable
fun TemplateBannerMessage(
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

    // Matching backend unused values like top-right etc. (as we only support centered view)
    val alignment = when {
        message.layout.placement.toString().startsWith("TOP") == true -> Alignment.TopCenter
        message.layout.placement.toString().startsWith("BOTTOM") == true -> Alignment.BottomCenter
        else -> Alignment.Center // Default to center for "LEFT", "RIGHT", "CENTER" or null values
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f),
        contentAlignment = alignment
    ) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = 5.dp,
                    bottom = 5.dp
                )
                .wrapContentHeight()
                .shadow(
                    elevation = if (message.style.dropShadow) 8.dp else 0.dp,
                    shape = parseBorderRadius(message.style.borderRadius)
                ),
            shape = parseBorderRadius(message.style.borderRadius),
            colors = CardDefaults.cardColors(
                containerColor = Color.fromHex(message.style.backgroundColor)
            ),
            border = if (message.style.border) {
                BorderStroke(
                    width = message.style.borderWidth.pxToDp,
                    color = Color.fromHex(message.style.borderColor)
                )
            } else null
        ) {
            Box {

                Box(modifier = Modifier.padding(parsePadding(message.layout.padding))) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {

                        val hasVisibleImage = message.image != null && !message.image.hideOnMobile
                        if (hasVisibleImage) {
                            BannerImage(
                                message = message,
                                modifier = Modifier.weight(0.3f), // Image gets 30% of width
                            )
                            Spacer(modifier = Modifier.width(message.layout.spaceBetweenImageAndBody.pxToDp))
                        }

                        Column(
                            modifier = Modifier.weight(if (hasVisibleImage) 0.7f else 1f), // Content gets 70% when image present
                            horizontalAlignment = Alignment.End
                        ) {
                            MessageText(
                                title = message.title,
                                fontFamily = composeFontFamily,
                                templateStyle = message.style,
                            )

                            Spacer(
                                modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.pxToDp)
                            )

                            MessageText(
                                description = message.description,
                                fontFamily = composeFontFamily,
                                templateStyle = message.style,
                            )

                            Spacer(
                                modifier = Modifier.height(message.layout.spaceBetweenContentAndActions.pxToDp)
                            )

                            val actionsList = message.actions.reversed()

                            // Only display action buttons if there are any actions
                            if (actionsList.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.End)
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // First action button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(IntrinsicSize.Min)
                                            .fillMaxSize()
                                    ) {
                                        MessageButton(
                                            action = actionsList[0],
                                            fontFamily = composeFontFamily,
                                            onAction = onAction,
                                            style = message.style,
                                        )
                                    }

                                    if (actionsList.size > 1) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(IntrinsicSize.Min)
                                                .fillMaxSize()
                                        ) {
                                            MessageButton(
                                                action = actionsList[1],
                                                fontFamily = composeFontFamily,
                                                onAction = onAction,
                                                style = message.style,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                CloseButton(
                    style = message.style,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
        }
    }
}

@Composable
fun BannerImage(
    message: InAppMessage,
    modifier: Modifier = Modifier,
) {
    // Content scale fit for banners, that's why not using shared component
    message.image?.url?.let { imageUrl ->
        val borderPadding = borderAdjustments(message.style)
        AsyncImage(
            model = imageUrl,
            contentDescription = "Banner image",
            modifier = modifier
                .padding(top = borderPadding, start = borderPadding, end = borderPadding),
            contentScale = ContentScale.Fit,
            alignment = Alignment.TopCenter
        )
    }
}
