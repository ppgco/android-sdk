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
import com.pushpushgo.inappmessages.model.Placement
import com.pushpushgo.inappmessages.ui.composables.common.CloseButton
import com.pushpushgo.inappmessages.ui.composables.common.MessageButton
import com.pushpushgo.inappmessages.ui.composables.common.MessageCard
import com.pushpushgo.inappmessages.ui.composables.common.MessageCardShadow
import com.pushpushgo.inappmessages.ui.composables.common.MessageDescription
import com.pushpushgo.inappmessages.ui.composables.common.MessageTitle
import com.pushpushgo.inappmessages.ui.composables.common.add
import com.pushpushgo.inappmessages.ui.composables.common.createFontFamily
import com.pushpushgo.inappmessages.ui.composables.common.parsePadding
import com.pushpushgo.inappmessages.ui.composables.common.pxToDp

/**
 * Banner-style message template for:
 * "REVIEW_FOR_DISCOUNT"
 */
@Composable
internal fun TemplateReviewForDiscount(
  message: InAppMessage,
  onAction: (InAppMessageAction) -> Unit,
  onDismiss: () -> Unit,
) {
  val composeFontFamily = createFontFamily(message)

  MessageCardShadow(
    message,
    modifier =
      Modifier.padding(
        top =
          if (listOf(Placement.TOP, Placement.TOP_LEFT, Placement.TOP_RIGHT).contains(message.layout.placement)) {
            0.dp
          } else {
            10.pxToDp
          },
        bottom =
          if (listOf(Placement.BOTTOM, Placement.BOTTOM_LEFT, Placement.BOTTOM_RIGHT).contains(message.layout.placement)) {
            0.dp
          } else {
            15.pxToDp
          },
      ),
  ) {
    MessageCard(message, modifier = Modifier.fillMaxWidth()) {
      Box(modifier = Modifier.padding(if (message.style.border) message.style.borderWidth.pxToDp else 0.dp)) {
        Box(modifier = Modifier.align(Alignment.TopEnd).zIndex(1f)) {
          CloseButton(
            style = message.style,
            onDismiss = onDismiss,
          )
        }

        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(parsePadding(message.layout.padding).add(message.style.borderWidth.pxToDp)),
        ) {
          if (message.image != null && !message.image.hideOnMobile) {
            Box(
              modifier =
                Modifier
                  .width(48.pxToDp)
                  .height(48.pxToDp),
            ) {
              AsyncImage(
                model = message.image.url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
              )
            }

            Spacer(modifier = Modifier.width(message.layout.spaceBetweenImageAndBody.pxToDp))
          }

          Row(
            modifier =
              Modifier
                .padding(parsePadding(message.layout.paddingBody))
                .weight(1f),
          ) {
            // Second column: Text content (45% width)
            Column(modifier = Modifier.weight(0.45f)) {
              if (message.title.text.isNotEmpty()) {
                MessageTitle(message.title, composeFontFamily)
              }

              Spacer(modifier = Modifier.height(message.layout.spaceBetweenTitleAndDescription.pxToDp))

              if (message.description.text.isNotEmpty()) {
                MessageDescription(message.description, composeFontFamily)
              }
            }

            Spacer(modifier = Modifier.width(message.layout.spaceBetweenContentAndActions.pxToDp))

            val actionsList = message.actions.filter { it.enabled }

            if (actionsList.isNotEmpty()) {
              // Third column: Action buttons (35% width)
              Column(
                modifier = Modifier.weight(0.35f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                actionsList.map {
                  Box(modifier = Modifier.fillMaxWidth()) {
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
