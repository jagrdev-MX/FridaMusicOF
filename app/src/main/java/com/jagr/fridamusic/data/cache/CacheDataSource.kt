
@file:OptIn(UnstableApi::class)

package com.jagr.fridamusic.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource

fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
    val cache = MediaCacheManager.getCache(context)

    return CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(httpDataSourceFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}