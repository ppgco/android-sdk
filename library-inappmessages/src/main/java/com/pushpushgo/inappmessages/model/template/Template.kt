package com.pushpushgo.inappmessages.model.template

import kotlinx.serialization.Serializable

@Serializable
data class TextStyles(
    val title: TextStyle? = null,
    val description: TextStyle? = null
)

@Serializable
data class InAppTemplate(
    val schemaVersion: String = "1.0",
    val containerStyle: ContainerStyle? = null,
    val imageStyle: ImageStyle? = null,
    val textStyles: TextStyles? = null,
    val buttonContainerStyle: ButtonContainerStyle? = null,
    val elementOrder: List<String>? = null,
    val closeButtonStyle: CloseButtonStyle? = null
)

@Serializable
data class ContainerStyle(
    val backgroundColor: String? = null,
    val cornerRadius: Double? = null,
    val padding: Padding? = null,
    val size: Size? = null
)

@Serializable
data class ImageStyle(
    val visibility: String? = "visible",
    val aspectRatio: Double? = null,
    val scaleType: String? = "fit_center",
    val cornerRadius: Double? = null,
    val marginTop: Int? = null,
    val marginBottom: Int? = null,
    val size: Size? = null,
    val padding: Padding? = null
)

@Serializable
data class TextStyle(
    val fontColor: String? = null,
    val fontSize: Int? = null,
    val isBold: Boolean? = false,
    val alignment: String? = "start",
    val maxLines: Int? = null,
    val marginTop: Int? = null,
    val marginBottom: Int? = null
)

@Serializable
data class ButtonContainerStyle(
    val orientation: String? = "vertical",
    val spacing: Int? = null,
    val marginTop: Int? = null,
    val padding: Padding? = null,
    val button: ButtonStyle? = null
)

@Serializable
data class ButtonStyle(
    val backgroundColor: String? = null,
    val fontColor: String? = null,
    val cornerRadius: Double? = null,
    val padding: Padding? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = "normal"
)

@Serializable
data class CloseButtonStyle(
    val visibility: String? = "visible",
    val color: String? = null,
    val position: String? = "top_end",
    val size: Size? = null,
    val padding: Padding? = null
)

@Serializable
data class Padding(
    val top: Int? = null,
    val bottom: Int? = null,
    val start: Int? = null,
    val end: Int? = null,
    val horizontal: Int? = null,
    val vertical: Int? = null
)

@Serializable
data class Size(
    val width: String? = null,
    val height: String? = null
)
