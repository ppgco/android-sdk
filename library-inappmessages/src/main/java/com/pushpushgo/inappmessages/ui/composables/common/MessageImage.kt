package com.pushpushgo.inappmessages.ui.composables.common

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.pushpushgo.inappmessages.model.InAppMessageImage
import com.pushpushgo.inappmessages.model.InAppMessageStyle

@Composable
internal fun MessageImage(
    image: InAppMessageImage,
    modifier: Modifier,
    templateStyle: InAppMessageStyle,
) {
    val borderPadding = borderAdjustments(templateStyle)
    AsyncImage(
        model = image.url,
        contentDescription = "In-app message image",
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f)
            .padding(top = borderPadding, start = borderPadding, end = borderPadding),
        contentScale = ContentScale.Fit,
        alignment = Alignment.TopCenter
    )
}
