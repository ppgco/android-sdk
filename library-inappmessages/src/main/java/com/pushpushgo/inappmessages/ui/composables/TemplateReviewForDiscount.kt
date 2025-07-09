package com.pushpushgo.inappmessages.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.model.FontFamily as ModelFontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.pushpushgo.inappmessages.model.FontStyle as ModelFontStyle


/**
 * Banner-style message template for:
 * "REVIEW_FOR_DISCOUNT"
 */
@Composable
fun TemplateReviewForDiscount(
    message: InAppMessage,
    onAction: (InAppMessageAction) -> Unit,
    onDismiss: () -> Unit,
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

    val alignment = when {
        message.layout.placement.toString().startsWith("TOP") == true -> Alignment.TopCenter
        message.layout.placement.toString().startsWith("BOTTOM") == true -> Alignment.BottomCenter
        else -> Alignment.Center
    }


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = 8.dp),
        contentAlignment = alignment
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
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
                border = BorderStroke(
                    width = message.style.borderWidth.dp,
                    color = Color.fromHex(message.style.borderColor)
                )
            ) {

                CloseButton(
                    style = message.style.copy(closeIcon = true),
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .zIndex(1f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {


                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.20f) // Takes 20% of width
                                .padding(end = 8.dp),
                        ) {
                            // Banner image (if available)
                            message.image?.url?.let { imageUrl ->
                                val borderPadding = borderAdjustments(message.style)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = borderPadding,
                                            top = borderPadding,
                                            end = borderPadding
                                        )
                                        .clip(parseBorderRadius(message.style.borderRadius))
                                        .background(Color.fromHex(message.style.backgroundColor))
                                ) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                    )
                                }
                            }
                        }

                        // Second column: Text content (50% width)
                        Column(
                            modifier = Modifier
                                .weight(0.50f) // Takes 50% of width
                                .wrapContentHeight()
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            message.title.let { title ->
                                if (title.text.isNotEmpty()) {
                                    Text(
                                        text = title.text,
                                        color = Color.fromHex(title.color),
                                        fontFamily = composeFontFamily,
                                        fontSize = title.fontSizeSp,
                                        fontWeight = FontWeight(title.fontWeight),
                                        textDecoration = if (title.style == ModelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
                                        fontStyle = if (title.style == ModelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            message.description.let { description ->
                                if (description.text.isNotEmpty()) {
                                    Text(
                                        text = description.text,
                                        color = Color.fromHex(description.color),
                                        fontFamily = composeFontFamily,
                                        fontSize = description.fontSizeSp,
                                        fontWeight = FontWeight(description.fontWeight),
                                        textDecoration = if (description.style == ModelFontStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None,
                                        fontStyle = if (description.style == ModelFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Third column: Action buttons (30% width)
                        val actionsList = message.actions.filter { it.enabled }

                        if (actionsList.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .weight(0.3f) // Takes 30% of width
                                    .wrapContentHeight()
                                    .padding(start = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // First action button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                ) {
                                    MessageButton(
                                        action = actionsList[0],
                                        fontFamily = composeFontFamily,
                                        onAction = onAction,
                                        style = message.style
                                    )
                                }

                                // Second action button (if available)
                                if (actionsList.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                    ) {
                                        MessageButton(
                                            action = actionsList[1],
                                            fontFamily = composeFontFamily,
                                            onAction = onAction,
                                            style = message.style
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
