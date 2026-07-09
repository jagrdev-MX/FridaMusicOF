package com.jagr.fridamusic.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

fun Modifier.graphicsLayerCompat(
    scale: Float = 1f,
    translationY: Dp = Dp.Unspecified,
    rotationZ: Float = 0f
): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    if (translationY != Dp.Unspecified) {
        this.translationY = translationY.toPx()
    }
    this.rotationZ = rotationZ
}
