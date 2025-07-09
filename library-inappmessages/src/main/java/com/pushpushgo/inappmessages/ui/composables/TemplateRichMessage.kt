package com.pushpushgo.inappmessages.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.FontFamily as ModelFontFamily
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction


/**
 * Banner-style message template for:
 * "WEBSITE_TO_HOME_SCREEN", "PAYWALL_PUBLISH"
 */
@Composable
fun TemplateRichMessage(
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
            .padding(parsePadding(message.layout.margin))
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(parsePadding(message.layout.padding)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val hasVisibleImage = message.image != null && !message.image.hideOnMobile
                    val statusBarPadding: Dp = if (message.style.border) {
                        parsePadding(message.layout.padding).calculateTopPadding()
                    } else {
                        0.dp
                    }

                    if (hasVisibleImage) {
                        // Layout for when an image is present
                        MessageImage(
                            image = message.image,
                            modifier = Modifier
                                // Need to add this because backend does not include top status bar paddings
                                .padding(top = statusBarPadding),
                            message.style
                        )
                        Spacer(modifier = Modifier.height(message.layout.spaceBetweenImageAndBody.pxToDp))

                        MessageBody(message, composeFontFamily, onAction)

                    } else {
                        // Layout for when no image is present
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MessageBody(message, composeFontFamily, onAction)
                        }
                    }
                }
                CloseButton(
                    style = message.style,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBody(
    message: InAppMessage,
    fontFamily: FontFamily,
    onAction: (InAppMessageAction) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(parsePadding(message.layout.paddingBody))
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .wrapContentHeight(Alignment.CenterVertically, true),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MessageText(title = message.title, fontFamily = fontFamily, message.style)
        Spacer(modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.pxToDp))
        MessageText(
            description = message.description,
            fontFamily = fontFamily,
            message.style
        )
        Spacer(modifier = Modifier.height(message.layout.spaceBetweenContentAndActions.pxToDp))
        MessageButtons(
            actions = message.actions,
            fontFamily = fontFamily,
            onAction = onAction,
            message.style
        )
    }
}
