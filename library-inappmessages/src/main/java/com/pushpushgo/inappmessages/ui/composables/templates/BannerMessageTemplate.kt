package com.pushpushgo.inappmessages.ui.composables.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.ui.composables.common.CloseButton
import com.pushpushgo.inappmessages.ui.composables.common.MessageButton
import com.pushpushgo.inappmessages.ui.composables.common.MessageCard
import com.pushpushgo.inappmessages.ui.composables.common.MessageCardShadow
import com.pushpushgo.inappmessages.ui.composables.common.MessageDescription
import com.pushpushgo.inappmessages.ui.composables.common.MessageTitle
import com.pushpushgo.inappmessages.ui.composables.common.createFontFamily
import com.pushpushgo.inappmessages.ui.composables.common.parsePadding
import com.pushpushgo.inappmessages.ui.composables.common.pxToDp

/**
 * Banner-style message template for:
 * "EXIT_INTENT_ECOMM", "PUSH_NOTIFICATION_OPT_IN", "EXIT_INTENT_TRAVEL",
 * "UNBLOCK_NOTIFICATIONS", "LOW_STOCK"
 */
@Composable
internal fun TemplateBannerMessage(
  message: InAppMessage,
  onDismiss: () -> Unit,
  onAction: (InAppMessageAction) -> Unit,
) {
  val composeFontFamily = createFontFamily(message)

  MessageCardShadow(message, modifier = Modifier.padding(top = 10.pxToDp, bottom = 15.pxToDp)) {
    MessageCard(
      message,
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.pxToDp, vertical = 4.pxToDp),
    ) {
      Box(modifier = Modifier.padding(if (message.style.border) message.style.borderWidth.pxToDp else 0.dp)) {
        Box(modifier = Modifier.align(Alignment.TopEnd).zIndex(1f)) {
          CloseButton(
            style = message.style,
            onDismiss = onDismiss,
          )
        }

        Box(modifier = Modifier.padding(parsePadding(message.layout.padding))) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val hasVisibleImage = message.image != null && !message.image.hideOnMobile

            if (hasVisibleImage) {
              AsyncImage(
                model = message.image.url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                  Modifier
                    .width(72.pxToDp)
                    .height(72.pxToDp)
                    .align(
                      Alignment.Top,
                    ),
              )

              Spacer(modifier = Modifier.width(message.layout.spaceBetweenImageAndBody.pxToDp))
            }

            Column(
              modifier = Modifier.weight(1f).padding(parsePadding(message.layout.paddingBody)),
              horizontalAlignment = Alignment.End,
            ) {
              if (message.title.text.isNotEmpty()) {
                MessageTitle(message.title, composeFontFamily)
              }

              Spacer(
                modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.pxToDp),
              )

              if (message.description.text.isNotEmpty()) {
                MessageDescription(message.description, composeFontFamily)
              }

              Spacer(
                modifier = Modifier.height(message.layout.spaceBetweenContentAndActions.pxToDp),
              )

              val actionsList =
                message.actions
                  .filter { it.enabled }
                  .reversed()

              if (actionsList.isNotEmpty()) {
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .align(Alignment.End),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  actionsList.map {
                    Box(
                      modifier =
                        Modifier
                          .weight(1f),
                    ) {
                      MessageButton(
                        action = it,
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
      }
    }
  }
}
