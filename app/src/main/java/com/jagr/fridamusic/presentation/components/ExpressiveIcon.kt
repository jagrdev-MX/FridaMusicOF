package com.jagr.fridamusic.presentation.components

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ExpressiveIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    active: Boolean,
    pressed: Boolean,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = when {
            reduceMotion -> 1f
            pressed -> 0.94f
            active -> 1.06f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expressive-icon-scale"
    )
    val translationY by animateDpAsState(
        targetValue = if (!reduceMotion && active) (-1).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expressive-icon-translation"
    )

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayerCompat(
            scale = scale,
            translationY = translationY
        )
    )
}

@Composable
private fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}
