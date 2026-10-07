package com.pushpushgo.sdk.inapp.ui.composables.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Window width above which a message stops filling the whole width and gets centered instead. */
private val WIDE_WINDOW_THRESHOLD = 600.dp

/** Largest width a message may occupy, no matter how wide the window is. */
private val MAX_MESSAGE_WIDTH = 560.dp

/**
 * Keeps a message readable on large screens and reachable in short windows.
 *
 * The dialog window itself stays MATCH_PARENT so banner placement (top/bottom gravity) keeps
 * working; the content is constrained here instead. Needed because Android 17 (API 37) ignores
 * orientation and resizability restrictions on displays wider than 600dp, so the host activity
 * can be rotated or resized at any time.
 *
 * @param scrollContent whether to wrap the content in a vertical scroll. Templates that scroll
 * internally pass false, so the two scrolls don't nest.
 */
@Composable
internal fun AdaptiveMessageContainer(
  scrollContent: Boolean = true,
  content: @Composable () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val availableHeight = maxHeight
    val isWideWindow = maxWidth > WIDE_WINDOW_THRESHOLD

    Box(
      modifier =
        Modifier
          .align(Alignment.Center)
          .then(if (isWideWindow) Modifier.widthIn(max = MAX_MESSAGE_WIDTH) else Modifier)
          .fillMaxWidth()
          .then(
            if (scrollContent) {
              Modifier
                .heightIn(max = availableHeight)
                .verticalScroll(rememberScrollState())
            } else {
              Modifier
            },
          ),
    ) {
      content()
    }
  }
}
