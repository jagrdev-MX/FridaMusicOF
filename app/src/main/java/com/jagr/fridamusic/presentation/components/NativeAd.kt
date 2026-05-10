package com.jagr.fridamusic.presentation.components

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
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
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // Usamos un identificador único para el ciclo de vida de este anuncio específico
    LaunchedEffect(nativeAd) {
        if (nativeAd != null) {
            android.util.Log.d("SpotifyNativeAd", "Ad loaded, starting 15s timer")
            delay(15000)
            android.util.Log.d("SpotifyNativeAd", "Timer finished, closing ad")
            onClose()
        }
    }

    DisposableEffect(Unit) {
        android.util.Log.d("SpotifyNativeAd", "Loading native ad...")
        adManager.loadNativeAd(
            onLoaded = {
                nativeAd = it
                isLoaded = true
                onAdLoaded()
                adManager.markAdShown()
            },
            onFailed = {
                android.util.Log.e("SpotifyNativeAd", "Ad failed to load")
                onAdFailed()
            }
        )
        onDispose {
            nativeAd?.destroy()
        }
    }

    if (isLoaded && nativeAd != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF121212))
        ) {
            AndroidView(
                factory = { ctx ->
                    val nativeAdView = NativeAdView(ctx)
                    
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
                        setPadding(40, 32, 40, 32)
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val headline = TextView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 20f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    bottomSection.addView(headline)

                    val cta = TextView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(20, 0, 0, 0)
                        }
                        
                        val shape = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 100f 
                            setColor(android.graphics.Color.WHITE)
                        }
                        background = shape
                        
                        setTextColor(android.graphics.Color.BLACK)
                        setPadding(48, 20, 48, 20)
                        gravity = Gravity.CENTER
                        textSize = 14f
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
                    nativeAd?.let { ad ->
                        (adView.headlineView as? TextView)?.text = ad.headline
                        (adView.callToActionView as? TextView)?.text = ad.callToAction
                        adView.mediaView?.setMediaContent(ad.mediaContent)
                        adView.setNativeAd(ad)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // "Advertisement" tag
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
