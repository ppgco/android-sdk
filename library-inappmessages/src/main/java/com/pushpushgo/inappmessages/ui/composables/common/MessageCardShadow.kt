package com.pushpushgo.inappmessages.ui.composables.common

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.pushpushgo.inappmessages.model.InAppMessage

@Composable
internal fun MessageCardShadow(
  message: InAppMessage,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(modifier = if (message.style.dropShadow) modifier else Modifier) {
    Box(
      modifier =
        if (message.style.dropShadow) {
          Modifier
            .drawBehind {
              val blur = 40f
              val offsetY = 10f

              val paint =
                Paint().asFrameworkPaint().apply {
                  isAntiAlias = true
                  color = Color.Black.copy(alpha = 0.20f).toArgb()
                  maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                }

              drawContext.canvas.nativeCanvas.drawRoundRect(
                0f,
                offsetY,
                size.width,
                size.height + offsetY,
                0f,
                0f,
                paint,
              )
            }
        } else {
          Modifier
        },
    ) {
      content()
    }
  }
}
