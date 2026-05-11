package com.jagr.fridamusic.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.presentation.theme.*

fun Modifier.liquidGlassEffect(cornerRadius: Dp = 16.dp): Modifier = composed {
    val isDark = isSystemInDarkTheme()

    val bgColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.6f)
    val borderColorStart = if (isDark) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.8f)
    val borderColorEnd = if (isDark) Color.Transparent else Color.White.copy(alpha = 0.3f)

    this.then(
        Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(borderColorStart, borderColorEnd)
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
    )
}