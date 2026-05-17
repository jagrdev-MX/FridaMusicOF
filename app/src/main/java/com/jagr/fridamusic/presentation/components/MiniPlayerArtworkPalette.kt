package com.jagr.fridamusic.presentation.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal data class MiniPlayerArtworkPalette(
    val container: Color,
    val glowStart: Color,
    val glowEnd: Color,
    val onContainer: Color,
    val onContainerMuted: Color,
    val border: Color,
    val accent: Color,
    val fromArtwork: Boolean
)

@Composable
internal fun rememberMiniPlayerArtworkPalette(albumArtUrl: String?): State<MiniPlayerArtworkPalette> {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val fallback = defaultMiniPlayerArtworkPalette(darkTheme)

    return produceState(
        initialValue = fallback,
        key1 = albumArtUrl,
        key2 = darkTheme
    ) {
        value = fallback
        val normalizedUrl = albumArtUrl?.takeIf { it.isNotBlank() } ?: return@produceState

        value = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(normalizedUrl)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val result = context.imageLoader.execute(request)
                val drawable = (result as? SuccessResult)?.drawable ?: return@runCatching fallback
                val bitmap = drawable.toBitmap(
                    width = drawable.intrinsicWidth.coerceAtLeast(1),
                    height = drawable.intrinsicHeight.coerceAtLeast(1)
                )
                extractMiniPlayerArtworkPalette(
                    bitmap = bitmap,
                    darkTheme = darkTheme,
                    fallback = fallback
                )
            }
                .getOrElse { fallback }
        }
    }
}

@Composable
private fun defaultMiniPlayerArtworkPalette(darkTheme: Boolean): MiniPlayerArtworkPalette {
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    return MiniPlayerArtworkPalette(
        container = surface,
        glowStart = primary,
        glowEnd = secondary,
        onContainer = onSurface,
        onContainerMuted = muted,
        border = onSurface.copy(alpha = if (darkTheme) 0.10f else 0.08f),
        accent = primary,
        fromArtwork = false
    )
}

internal fun extractMiniPlayerArtworkPalette(
    bitmap: Bitmap,
    darkTheme: Boolean,
    fallback: MiniPlayerArtworkPalette
): MiniPlayerArtworkPalette {
    val scaled = Bitmap.createScaledBitmap(bitmap, 40, 40, true)
    val buckets = linkedMapOf<Int, ColorBucket>()
    val samples = mutableListOf<ColorSample>()

    for (y in 0 until scaled.height) {
        for (x in 0 until scaled.width) {
            val pixel = scaled.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 96) continue

            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(pixel, hsl)
            val saturation = hsl[1]
            val lightness = hsl[2]

            val sample = ColorSample(pixel, hsl[0], saturation, lightness)
            samples += sample

            val key = quantize(pixel)
            val bucket = buckets.getOrPut(key) { ColorBucket() }
            bucket.add(pixel)
        }
    }

    if (samples.isEmpty()) return fallback

    val dominant = buckets.values.maxByOrNull { it.count }?.averageColor() ?: fallback.container.toArgb()
    val vibrant = samples
        .filter { it.saturation >= 0.24f && it.lightness in 0.12f..0.88f }
        .maxByOrNull { it.saturation * (1f - abs(it.lightness - 0.55f)) }
        ?.argb
        ?: dominant
    val secondary = samples
        .filter { it.saturation >= 0.18f && it.lightness in 0.10f..0.90f }
        .maxByOrNull { sample ->
            hueDistance(sample.hue, colorHue(vibrant)) * 0.65f + sample.saturation * 0.35f
        }
        ?.argb
        ?: dominant

    val mixedBase = ColorUtils.blendARGB(dominant, vibrant, 0.34f)
    val containerHsl = FloatArray(3)
    ColorUtils.colorToHSL(mixedBase, containerHsl)
    containerHsl[1] = containerHsl[1].coerceIn(0.18f, 0.72f)
    containerHsl[2] = containerHsl[2].coerceIn(
        minimumValue = if (darkTheme) 0.18f else 0.20f,
        maximumValue = if (darkTheme) 0.30f else 0.34f
    )
    val container = Color(ColorUtils.HSLToColor(containerHsl))

    return MiniPlayerArtworkPalette(
        container = container,
        glowStart = Color(vibrant),
        glowEnd = Color(secondary),
        onContainer = Color.White,
        onContainerMuted = Color.White.copy(alpha = 0.78f),
        border = Color.White.copy(alpha = 0.12f),
        accent = Color.White,
        fromArtwork = true
    )
}

private data class ColorSample(
    val argb: Int,
    val hue: Float,
    val saturation: Float,
    val lightness: Float
)

private class ColorBucket {
    var count: Int = 0
        private set
    private var redTotal: Int = 0
    private var greenTotal: Int = 0
    private var blueTotal: Int = 0

    fun add(color: Int) {
        count += 1
        redTotal += AndroidColor.red(color)
        greenTotal += AndroidColor.green(color)
        blueTotal += AndroidColor.blue(color)
    }

    fun averageColor(): Int {
        if (count == 0) return AndroidColor.BLACK
        return AndroidColor.rgb(
            redTotal / count,
            greenTotal / count,
            blueTotal / count
        )
    }
}

private fun quantize(color: Int): Int {
    val red = AndroidColor.red(color) / 32
    val green = AndroidColor.green(color) / 32
    val blue = AndroidColor.blue(color) / 32
    return (red shl 16) or (green shl 8) or blue
}

private fun hueDistance(first: Float, second: Float): Float {
    val direct = abs(first - second)
    return minOf(direct, 360f - direct) / 180f
}

private fun colorHue(color: Int): Float {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    return hsl[0]
}
