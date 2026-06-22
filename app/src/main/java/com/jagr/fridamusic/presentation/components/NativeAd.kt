package com.jagr.fridamusic.presentation.components

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.jagr.fridamusic.data.ads.AdManager

@Composable
fun SpotifyNativeAd(
    modifier: Modifier = Modifier,
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val adManager = remember { AdManager.getInstance(context) }
    var nativeAdState by remember { mutableStateOf<NativeAd?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // Use a key that changes only when a new ad is actually received
    LaunchedEffect(nativeAdState) {
        val ad = nativeAdState
        if (ad != null) {
            android.util.Log.d("SpotifyNativeAd", "Ad visible: ${ad.headline}. Starting 15s timer.")
            delay(15000)
            android.util.Log.d("SpotifyNativeAd", "Timer finished, closing ad")
            onClose()
        }
    }

    DisposableEffect(Unit) {
        var disposed = false
        android.util.Log.d("SpotifyNativeAd", "Requesting native ad...")
        adManager.loadNativeAd(
            onLoaded = { loadedAd ->
                if (disposed) {
                    loadedAd.destroy()
                    return@loadNativeAd
                }
                android.util.Log.d("SpotifyNativeAd", "Ad loaded successfully: ${loadedAd.headline}")
                nativeAdState = loadedAd
                isLoaded = true
                onAdLoaded()
                adManager.markAdShown()
            },
            onFailed = {
                android.util.Log.e("SpotifyNativeAd", "Ad failed to load (check internet or AdUnit ID)")
                onAdFailed()
            }
        )
        onDispose {
            disposed = true
            nativeAdState?.destroy()
            nativeAdState = null
        }
    }

    if (isLoaded && nativeAdState != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF1A1A1A)) // Slightly lighter than pure black to debug visibility
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    val nativeAdView = NativeAdView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    
                    val root = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val mediaView = MediaView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            0,
                            1f
                        )
                    }
                    root.addView(mediaView)

                    val bottomSection = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(40, 24, 40, 24)
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#CC000000"))
                        }
                    }

                    val headline = TextView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        maxLines = 1
                    }
                    bottomSection.addView(headline)

                    val cta = TextView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(16, 0, 0, 0) }
                        
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 50f 
                            setColor(android.graphics.Color.WHITE)
                        }
                        
                        setTextColor(android.graphics.Color.BLACK)
                        setPadding(32, 12, 32, 12)
                        gravity = Gravity.CENTER
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    bottomSection.addView(cta)
                    
                    root.addView(bottomSection)

                    nativeAdView.headlineView = headline
                    nativeAdView.mediaView = mediaView
                    nativeAdView.callToActionView = cta
                    
                    nativeAdView.addView(root)
                    nativeAdView
                },
                update = { adView ->
                    nativeAdState?.let { ad ->
                        android.util.Log.d("SpotifyNativeAd", "Updating NativeAdView with assets")
                        (adView.headlineView as? TextView)?.text = ad.headline
                        (adView.callToActionView as? TextView)?.text = ad.callToAction
                        adView.mediaView?.setMediaContent(ad.mediaContent)
                        adView.setNativeAd(ad)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = "Advertisement",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Ad",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
