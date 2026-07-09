package com.jagr.fridamusic.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlassEffect(cornerRadius: Dp = 16.dp): Modifier = this.composed {
    val isDark = isSystemInDarkTheme()

    val baseColor = MaterialTheme.colorScheme.onSurface

    val bgColor = if (isDark) baseColor.copy(alpha = 0.05f) else baseColor.copy(alpha = 0.1f)
    val borderColorStart = if (isDark) baseColor.copy(alpha = 0.15f) else baseColor.copy(alpha = 0.3f)
    val borderColorEnd = if (isDark) baseColor.copy(alpha = 0.02f) else baseColor.copy(alpha = 0.05f)

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