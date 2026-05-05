package com.jagr.fridamusic.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.FrameLayout
import android.view.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdView
import com.jagr.fridamusic.data.ads.AdManager

@Composable
fun BannerAdView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val adManager = remember(context) { AdManager.getInstance(context) }
    val bannerContainer = remember { FrameLayout(activity) }
    val bannerAdView = remember { mutableListOf<AdView?>() }

    DisposableEffect(Unit) {
        onDispose {
            adManager.destroyBanner(bannerAdView.firstOrNull())
            bannerAdView.clear()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            bannerContainer.apply {
                if (bannerAdView.isEmpty()) {
                    post {
                        val adView = adManager.loadLargeAnchoredAdaptiveBanner(activity, this)
                        bannerAdView.add(adView)
                    }
                }
            }
        },
        update = { container ->
            if (bannerAdView.isEmpty() && container.width > 0) {
                container.post {
                    val adView = adManager.loadLargeAnchoredAdaptiveBanner(activity, container)
                    bannerAdView.add(adView)
                }
            }
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
