package com.jagr.fridamusic.presentation.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.jagr.fridamusic.R

@Composable
fun rememberFridaArtworkRequest(data: Any?): ImageRequest {
    val context = LocalContext.current
    val resolvedData = remember(data) { data.normalizedArtworkData() }
    return remember(context, resolvedData) {
        ImageRequest.Builder(context)
            .data(resolvedData ?: R.drawable.frida_cover_fallback)
            .placeholder(R.drawable.frida_cover_fallback)
            .fallback(R.drawable.frida_cover_fallback)
            .error(R.drawable.frida_cover_fallback)
            .crossfade(true)
            .build()
    }
}

fun Any?.normalizedArtworkData(): Any? {
    return when (this) {
        null -> null
        is Uri -> takeIf { it != Uri.EMPTY && it.toString().isNotBlank() }
        is String -> trim()
            .takeIf { it.isNotBlank() && it != "null" && it != "content://media/external/audio/albumart/0" }
        else -> this
    }
}
