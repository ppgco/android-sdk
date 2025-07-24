package com.pushpushgo.inappmessages.ui.composables.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.ui.composables.common.CloseButton
import com.pushpushgo.inappmessages.ui.composables.common.MessageButtons
import com.pushpushgo.inappmessages.ui.composables.common.MessageCard
import com.pushpushgo.inappmessages.ui.composables.common.MessageDescription
import com.pushpushgo.inappmessages.ui.composables.common.MessageTitle
import com.pushpushgo.inappmessages.ui.composables.common.createFontFamily
import com.pushpushgo.inappmessages.ui.composables.common.parsePadding
import com.pushpushgo.inappmessages.ui.composables.common.pxToDp

/**
 * Banner-style message template for:
 * "WEBSITE_TO_HOME_SCREEN", "PAYWALL_PUBLISH"
 */
@Composable
fun RichMessageTemplate(
    message: InAppMessage,
    onDismiss: () -> Unit,
    onAction: (InAppMessageAction) -> Unit,
) {
    val composeFontFamily = createFontFamily(message)

    MessageCard(message, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(if (message.style.border) message.style.borderWidth.pxToDp else 0.dp)) {
            CloseButton(
                style = message.style,
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4 - message.style.borderWidth).dp, y = (2 + message.style.borderWidth).dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(parsePadding(message.layout.padding))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (message.image != null && !message.image.hideOnMobile) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AsyncImage(
                                model = message.image.url,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(message.layout.spaceBetweenImageAndBody.pxToDp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(parsePadding(message.layout.paddingBody)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MessageTitle(message.title, composeFontFamily)

                        Spacer(modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.pxToDp))

                        MessageDescription(message.description, composeFontFamily)

                        Spacer(modifier = Modifier.height(message.layout.spaceBetweenContentAndActions.pxToDp))

                        MessageButtons(
                            actions = message.actions,
                            fontFamily = composeFontFamily,
                            onAction = onAction,
                            message.style
                        )
                    }
                }
            }
        }
    }
}

