package com.pushpushgo.inappmessages.ui.composables.templates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.ui.composables.common.CloseButton
import com.pushpushgo.inappmessages.ui.composables.common.MessageButtons
import com.pushpushgo.inappmessages.ui.composables.common.MessageDescription
import com.pushpushgo.inappmessages.ui.composables.common.MessageImage
import com.pushpushgo.inappmessages.ui.composables.common.MessageTitle
import com.pushpushgo.inappmessages.ui.composables.common.fromHex
import com.pushpushgo.inappmessages.ui.composables.common.parseBorderRadius
import com.pushpushgo.inappmessages.ui.composables.common.parsePadding
import com.pushpushgo.inappmessages.ui.composables.common.pxToDp

@Composable
fun InAppMessageDefaultTemplate(
    message: InAppMessage,
    onDismiss: () -> Unit,
    onAction: (InAppMessageAction) -> Unit,
) {
    // This template is a fallback and uses default system fonts.
    val fontFamily = FontFamily.Default

    val cardPadding = parsePadding(message.layout.padding)
    val bodyPadding = parsePadding(message.layout.paddingBody)
    val borderRadius = parseBorderRadius(message.style.borderRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(cardPadding)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = borderRadius,
            colors = CardDefaults.cardColors(
                containerColor = Color.fromHex(message.style.backgroundColor)
            ),
            border = if (message.style.border) {
                BorderStroke(
                    width = message.style.borderWidth.pxToDp,
                    color = Color.fromHex(message.style.borderColor)
                )
            } else null,
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (message.style.dropShadow) 10.dp else 0.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bodyPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                message.image?.let { it ->
                    if (it.hideOnMobile == true) {
                        MessageImage(image = it, modifier = Modifier, message.style)
                        Spacer(modifier = Modifier.height(message.layout.spaceBetweenImageAndBody.pxToDp))
                    }

                }
                MessageTitle(
                    title = message.title,
                    fontFamily = fontFamily,
                )
                Spacer(modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.pxToDp))
                MessageDescription(
                    description = message.description,
                    fontFamily = fontFamily,
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

        val closeButtonSize = message.style.closeIconWidth.pxToDp
        val offset = (closeButtonSize / 4)
        CloseButton(
            style = message.style,
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = offset, y = -offset)
        )
    }
}
