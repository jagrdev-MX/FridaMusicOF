package com.jagr.fridamusic.data.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import java.util.concurrent.atomic.AtomicLong

class AdManager private constructor(
    context: Context
) {
    private data class NativeAdCallbacks(
        val onLoaded: (NativeAd) -> Unit,
        val onFailed: () -> Unit
    )

    private data class NativeAdLoadDecision(
        val cachedAd: NativeAd?,
        val shouldLoad: Boolean,
        val failImmediately: Boolean
    )

    private data class NativeAdDelivery(
        val callback: NativeAdCallbacks?,
        val remainingCallbacks: List<NativeAdCallbacks>
    )

    private val appContext = context.applicationContext
    private val lastAdDisplayAtMs = AtomicLong(0)
    private val nativeAdLock = Any()
    private val pendingNativeAdCallbacks = mutableListOf<NativeAdCallbacks>()
    private var cachedNativeAd: NativeAd? = null
    private var cachedNativeAdLoadedAtMs: Long = 0L
    private var nativeAdLoading = false
    private var lastNativeAdFailedAtMs: Long = 0L
    private var isFirstAdAttempt = true

    private val cooldownMs = 300_000L
    private val nativeAdTtlMs = 55 * 60 * 1000L
    private val nativeAdRetryDelayMs = 30_000L

    companion object {
        private const val TAG = "AdManager"
        @Volatile
        private var instance: AdManager? = null

        fun getInstance(context: Context): AdManager {
            return instance ?: synchronized(this) {
                instance ?: AdManager(context).also { instance = it }
            }
        }
    }

    fun initialize() {
        MobileAds.initialize(appContext)
    }

    fun canShowAdNow(): Boolean {
        if (isFirstAdAttempt) return true
        val lastTime = lastAdDisplayAtMs.get()
        return SystemClock.elapsedRealtime() - lastTime >= cooldownMs
    }

    fun markAdShown() {
        isFirstAdAttempt = false
        lastAdDisplayAtMs.set(SystemClock.elapsedRealtime())
    }

    fun preloadNativeAd() {
        val shouldLoad = synchronized(nativeAdLock) {
            clearExpiredNativeAdLocked(SystemClock.elapsedRealtime())
            if (cachedNativeAd != null || nativeAdLoading || recentlyFailedNativeAdLocked()) {
                false
            } else {
                nativeAdLoading = true
                true
            }
        }
        if (shouldLoad) {
            requestNativeAd()
        }
    }

    fun loadOrConsumeNativeAd(
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit
    ) {
        val decision = synchronized(nativeAdLock) {
            val now = SystemClock.elapsedRealtime()
            clearExpiredNativeAdLocked(now)
            val cached = cachedNativeAd
            if (cached != null) {
                cachedNativeAd = null
                cachedNativeAdLoadedAtMs = 0L
                NativeAdLoadDecision(cachedAd = cached, shouldLoad = false, failImmediately = false)
            } else if (recentlyFailedNativeAdLocked(now)) {
                NativeAdLoadDecision(cachedAd = null, shouldLoad = false, failImmediately = true)
            } else {
                pendingNativeAdCallbacks += NativeAdCallbacks(onLoaded, onFailed)
                if (!nativeAdLoading) {
                    nativeAdLoading = true
                    NativeAdLoadDecision(cachedAd = null, shouldLoad = true, failImmediately = false)
                } else {
                    NativeAdLoadDecision(cachedAd = null, shouldLoad = false, failImmediately = false)
                }
            }
        }

        decision.cachedAd?.let(onLoaded)
        if (decision.failImmediately) onFailed()
        if (decision.shouldLoad) requestNativeAd()
    }

    fun destroyPreloadedNativeAd() {
        synchronized(nativeAdLock) {
            cachedNativeAd?.destroy()
            cachedNativeAd = null
            cachedNativeAdLoadedAtMs = 0L
        }
    }

    fun loadLargeAnchoredAdaptiveBanner(activity: Activity, container: ViewGroup): AdView {
        val adView = AdView(activity)
        adView.adUnitId = AdConfig.BANNER_AD_UNIT_ID
        
        val displayMetrics = activity.resources.displayMetrics
        val adWidthPixels = if (container.width > 0) container.width else displayMetrics.widthPixels
        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth))

        container.removeAllViews()
        container.addView(adView)
        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "Banner ad loaded")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Banner ad failed: ${adError.message}")
            }
        }
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }

    fun loadNativeAd(
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit
    ) {
        loadOrConsumeNativeAd(onLoaded, onFailed)
    }

    private fun requestNativeAd() {
        val loader = AdLoader.Builder(appContext, AdConfig.NATIVE_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                nativeAd.mediaContent?.hasVideoContent()
                Log.d(TAG, "Native ad loaded. hasVideo=${nativeAd.mediaContent?.hasVideoContent()}")
                val delivery = synchronized(nativeAdLock) {
                    nativeAdLoading = false
                    lastNativeAdFailedAtMs = 0L
                    val callback = pendingNativeAdCallbacks.firstOrNull()
                    val remainingCallbacks = pendingNativeAdCallbacks.drop(1)
                    pendingNativeAdCallbacks.clear()
                    if (callback == null) {
                        cachedNativeAd?.destroy()
                        cachedNativeAd = nativeAd
                        cachedNativeAdLoadedAtMs = SystemClock.elapsedRealtime()
                    }
                    NativeAdDelivery(callback, remainingCallbacks)
                }

                if (delivery.callback != null) {
                    delivery.callback.onLoaded(nativeAd)
                    delivery.remainingCallbacks.forEach { it.onFailed() }
                } else {
                    Log.d(TAG, "Native ad preloaded for Now Playing")
                }
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                    .build()
            )
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Native ad failed: ${adError.message}")
                    val callbacks = synchronized(nativeAdLock) {
                        nativeAdLoading = false
                        lastNativeAdFailedAtMs = SystemClock.elapsedRealtime()
                        pendingNativeAdCallbacks.toList().also {
                            pendingNativeAdCallbacks.clear()
                        }
                    }
                    callbacks.forEach { it.onFailed() }
                }
            })
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }

    private fun clearExpiredNativeAdLocked(now: Long) {
        if (cachedNativeAd != null && now - cachedNativeAdLoadedAtMs > nativeAdTtlMs) {
            cachedNativeAd?.destroy()
            cachedNativeAd = null
            cachedNativeAdLoadedAtMs = 0L
        }
    }

    private fun recentlyFailedNativeAdLocked(now: Long = SystemClock.elapsedRealtime()): Boolean =
        lastNativeAdFailedAtMs > 0L && now - lastNativeAdFailedAtMs < nativeAdRetryDelayMs

    fun destroyBanner(adView: AdView?) {
        adView?.destroy()
    }

    fun destroyNativeAd(nativeAd: NativeAd?) {
        nativeAd?.destroy()
    }
}
