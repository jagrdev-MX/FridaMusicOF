package com.jagr.fridamusic.presentation.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.jagr.fridamusic.R

@Composable
fun rememberFridaArtworkRequest(
    model: Any?,
    requestSizePx: Int? = null,
    allowHardware: Boolean = true,
    crossfadeMillis: Int = 120
): ImageRequest {
    val context = LocalContext.current
    val normalizedModel = remember(model) { normalizedArtworkModel(model) }
    return remember(context, normalizedModel, requestSizePx, allowHardware, crossfadeMillis) {
        val builder = ImageRequest.Builder(context)
            .data(normalizedModel)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(allowHardware)

        if (crossfadeMillis > 0) {
            builder.crossfade(crossfadeMillis)
        } else {
            builder.crossfade(false)
        }

        if (requestSizePx != null) builder.size(requestSizePx)
        builder.build()
    }
}

private fun normalizedArtworkModel(model: Any?): Any {
    return when (model) {
        null -> R.drawable.frida_artwork_fallback
        is Uri -> model.takeUnless { it == Uri.EMPTY || it.isEmptyArtworkUri() }
            ?: R.drawable.frida_artwork_fallback
        is String -> model.trim()
            .takeUnless { it.isBlank() || it == "null" || it.isEmptyArtworkUriString() }
            ?: R.drawable.frida_artwork_fallback
        else -> model
    }
}

private fun Uri.isEmptyArtworkUri(): Boolean =
    toString().isEmptyArtworkUriString()

private fun String.isEmptyArtworkUriString(): Boolean =
    this == Uri.EMPTY.toString() || endsWith("/albumart/0", ignoreCase = true)

@Composable
fun FridaArtworkImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    contentScale: ContentScale = ContentScale.Fit,
    requestSizePx: Int? = null,
    crossfadeMillis: Int = 120
) {
    val fallback = painterResource(R.drawable.frida_artwork_fallback)
    val request = rememberFridaArtworkRequest(
        model = model,
        requestSizePx = requestSizePx,
        crossfadeMillis = crossfadeMillis
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        key(model, requestSizePx, crossfadeMillis) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                placeholder = fallback,
                error = fallback,
                fallback = fallback
            )
        }
    }
}

@Composable
fun FridaSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FridaEmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FridaArtworkImage(
            model = null,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(22.dp)
        )
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
