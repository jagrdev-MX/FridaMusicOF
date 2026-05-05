package com.jagr.fridamusic.data.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
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
    private val appContext = context.applicationContext
    private val lastAdDisplayAtMs = AtomicLong(0)

    private val cooldownMs = 45_000L

    companion object {
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
        val lastTime = lastAdDisplayAtMs.get()
        return SystemClock.elapsedRealtime() - lastTime >= cooldownMs
    }

    fun markAdShown() {
        lastAdDisplayAtMs.set(SystemClock.elapsedRealtime())
    }

    fun loadLargeAnchoredAdaptiveBanner(activity: Activity, container: ViewGroup): AdView {
        val adView = AdView(activity)
        adView.adUnitId = AdConfig.BANNER_AD_UNIT_ID
        val adWidth = container.width.coerceAtLeast(320)
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth))
        container.removeAllViews()
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }

    fun loadNativeAd(
        onLoaded: (NativeAd) -> Unit,
        onFailed: () -> Unit
    ) {
        val loader = AdLoader.Builder(appContext, AdConfig.NATIVE_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                nativeAd.mediaContent.hasVideoContent()
                onLoaded(nativeAd)
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                    .build()
            )
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    onFailed()
                }
            })
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }

    fun destroyBanner(adView: AdView?) {
        adView?.destroy()
    }

    fun destroyNativeAd(nativeAd: NativeAd?) {
        nativeAd?.destroy()
    }
}
