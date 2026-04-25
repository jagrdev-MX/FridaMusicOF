package com.jagr.fridamusic.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.presentation.theme.*

fun Modifier.liquidGlassEffect(
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = GlassBackgroundTint
) : Modifier {
    val shape = RoundedCornerShape(cornerRadius)

    val borderBrush = Brush.linearGradient(
        colors = listOf(GlassBorderLight, GlassBorderDark)
    )

    return this
        .clip(shape)
        .background(backgroundColor)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}