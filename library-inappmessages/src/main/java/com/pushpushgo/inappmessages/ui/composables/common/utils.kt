package com.pushpushgo.inappmessages.ui.composables.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.pushpushgo.inappmessages.R
import com.pushpushgo.inappmessages.model.InAppMessage
import com.pushpushgo.inappmessages.model.InAppMessageAction
import com.pushpushgo.inappmessages.model.InAppMessageDescription
import com.pushpushgo.inappmessages.model.InAppMessageStyle
import com.pushpushgo.inappmessages.model.InAppMessageTitle


internal fun Color.Companion.fromHex(hex: String): Color {
    return try {
        Color(hex.toColorInt())
    } catch (_: IllegalArgumentException) {
        Unspecified
    }
}

internal fun removePxFromString(value: String): List<Float> {
    return value.replace("px", "", ignoreCase = true)
        .split(' ')
        .mapNotNull { it.trim().toFloatOrNull() }
}

@Composable
internal fun parseBorderRadius(borderRadius: String?): Shape {
    if (borderRadius.isNullOrBlank()) return RoundedCornerShape(0.pxToDp)
    val parts = removePxFromString(borderRadius)

    return when (parts.size) {
        1 -> RoundedCornerShape(parts[0].pxToDp)
        2 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[0].pxToDp,
            bottomStart = parts[1].pxToDp,
            bottomEnd = parts[1].pxToDp
        )

        3 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[1].pxToDp,
            bottomStart = parts[2].pxToDp,
            bottomEnd = parts[1].pxToDp
        )

        4 -> RoundedCornerShape(
            topStart = parts[0].pxToDp,
            topEnd = parts[1].pxToDp,
            bottomStart = parts[3].pxToDp,
            bottomEnd = parts[2].pxToDp
        )

        else -> RoundedCornerShape(0.pxToDp)
    }
}

@Composable
internal fun parsePadding(padding: String?): PaddingValues {
    if (padding.isNullOrBlank()) return PaddingValues(0.dp)
    val parts = removePxFromString(padding)

    return when (parts.size) {
        1 -> PaddingValues(parts[0].pxToDp)
        2 -> PaddingValues(vertical = parts[0].pxToDp, horizontal = parts[1].pxToDp)
        3 -> PaddingValues(
            top = parts[0].pxToDp,
            start = parts[1].pxToDp,
            end = parts[1].pxToDp,
            bottom = parts[2].pxToDp
        )

        4 -> PaddingValues(
            top = parts[0].pxToDp,
            end = parts[1].pxToDp,
            bottom = parts[2].pxToDp,
            start = parts[3].pxToDp
        )

        else -> PaddingValues(0.pxToDp)
    }
}

internal val InAppMessageTitle.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.3f).sp

internal val InAppMessageDescription.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.3f).sp

internal val InAppMessageAction.fontSizeSp: TextUnit
    get() = (this.fontSize * 1.3f).sp

internal val Int.pxToDp: Dp
    get() = (this * 1.333f).dp

internal val Float.pxToDp: Dp
    get() = (this * 1.333f).dp

internal fun TextAlign.Companion.fromString(value: String): TextAlign {
    return when (value.lowercase()) {
        "left" -> Left
        "right" -> Right
        "center" -> Center
        "justify" -> Justify
        "start" -> Start
        "end" -> End
        else -> Start
    }
}

internal fun borderAdjustments(styleSettings: InAppMessageStyle): Dp {
    val borderPadding: Dp = if (styleSettings.border) {
        styleSettings.borderWidth.pxToDp
    } else {
        0.dp
    }
    return borderPadding
}

internal fun createFontFamily(message: InAppMessage): FontFamily {
    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

    val fontName = when (message.style.fontFamily) {
        com.pushpushgo.inappmessages.model.FontFamily.ROBOTO -> "Roboto"
        com.pushpushgo.inappmessages.model.FontFamily.OPEN_SANS -> "Open Sans"
        com.pushpushgo.inappmessages.model.FontFamily.MONTSERRAT -> "Montserrat"
        com.pushpushgo.inappmessages.model.FontFamily.INTER -> "Inter"
        com.pushpushgo.inappmessages.model.FontFamily.POPPINS -> "Poppins"
        com.pushpushgo.inappmessages.model.FontFamily.LATO -> "Lato"
        com.pushpushgo.inappmessages.model.FontFamily.PLAYFAIR_DISPLAY -> "Playfair Display"
        com.pushpushgo.inappmessages.model.FontFamily.FIRA_SANS -> "Fira Sans"
        com.pushpushgo.inappmessages.model.FontFamily.ARIAL -> "Arimo"
        com.pushpushgo.inappmessages.model.FontFamily.GEORGIA -> "Gelasio"
    }

    val fontWeights = listOf(
        message.title.fontWeight,
        message.description.fontWeight
    ).plus(message.actions.map { it.fontWeight }).distinct()

    val fontStyles = listOf(
        message.title.style,
        message.description.style
    ).plus(message.actions.map { it.style }).distinct()

    val fonts = fontWeights.flatMap { weight ->
        fontStyles.map { style ->
            Font(
                googleFont = GoogleFont(fontName, true),
                fontProvider = provider,
                weight = FontWeight(weight),
                style = if (style == com.pushpushgo.inappmessages.model.FontStyle.NORMAL) {
                    FontStyle.Normal
                } else {
                    FontStyle.Italic
                }
            )
        }
    }

    return FontFamily(fonts)
}

@Composable
internal fun PaddingValues.add(
    add: Dp,
): PaddingValues {
    return PaddingValues(
        start = this.calculateStartPadding(LocalLayoutDirection.current) + add,
        top = this.calculateTopPadding() + add,
        end = this.calculateEndPadding(LocalLayoutDirection.current) + add,
        bottom = this.calculateBottomPadding() + add
    )
}
