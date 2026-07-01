@file:OptIn(UnstableApi::class)
package com.jagr.fridamusic.data.repository

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BITRATE_TIE_MARGIN_BPS = 16_000

data class RemotePlaybackStream(
    val url: String,
    val source: String,
    val formatName: String,
    val bitrate: Int
)

class YouTubeRepository(private val context: Context) {
    private val audioStreamCache = ConcurrentHashMap<String, String>()
    private val audioStreamCacheExpiresAtMs = ConcurrentHashMap<String, Long>()
    private val audioDurationCache = ConcurrentHashMap<String, Long>()

    suspend fun extractAudioStream(result: YouTubeResult): RemotePlaybackStream? = withContext(Dispatchers.IO) {
        val totalStart = System.currentTimeMillis()
        try {
            val videoId = result.videoId

            getCachedStream(videoId)?.let { url ->
                Log.d("SearchPerf", "[CACHE] extractAudioStream hit for ${result.title}: ${System.currentTimeMillis() - totalStart}ms")
                return@withContext RemotePlaybackStream(url, "cache", "", 0)
            }

            val extractionStart = System.currentTimeMillis()
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val extractionTime = System.currentTimeMillis() - extractionStart
            Log.d("SearchPerf", "[1] NewPipe StreamInfo.getInfo (page+player fetch): ${extractionTime}ms")

            val selectionStart = System.currentTimeMillis()
            val playbackStream = selectPlayableRemoteStream(streamInfo)
            val selectionTime = System.currentTimeMillis() - selectionStart
            Log.d("SearchPerf", "[2] Stream selection (CPU, no network): ${selectionTime}ms")

            if (playbackStream != null) {
                cacheStream(videoId, playbackStream.url, streamInfo.duration * 1000L)
            }

            val totalTime = System.currentTimeMillis() - totalStart
            Log.d("SearchPerf", "[TOTAL] extractAudioStream for ${result.title}: ${totalTime}ms (network=${extractionTime}ms, cpu=${selectionTime}ms)")

            playbackStream
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - totalStart
            Log.e("YouTubeRepo", "Extraction failed for ${result.title} after ${totalTime}ms", e)
            null
        }
    }

    fun getCachedStream(videoId: String): String? {
        val url = audioStreamCache[videoId] ?: return null
        val expiry = audioStreamCacheExpiresAtMs[videoId] ?: 0L
        if (System.currentTimeMillis() >= expiry) {
            audioStreamCache.remove(videoId)
            audioStreamCacheExpiresAtMs.remove(videoId)
            return null
        }
        return url
    }

    fun getCachedDuration(videoId: String): Long? = audioDurationCache[videoId]

    fun cacheStream(videoId: String, url: String, durationMs: Long) {
        audioStreamCache[videoId] = url
        audioDurationCache[videoId] = durationMs
        audioStreamCacheExpiresAtMs[videoId] = System.currentTimeMillis() + (1000 * 60 * 60 * 4) // 4 hours
    }

    private fun selectPlayableRemoteStream(streamInfo: StreamInfo): RemotePlaybackStream? {
        val audioStream = pickBestAudioStream(streamInfo.audioStreams)

        audioStream?.let {
            val url = it.url ?: return@let null
            if (url.isNotBlank()) {
                return RemotePlaybackStream(
                    url = url,
                    source = "audio",
                    formatName = it.format?.name.orEmpty(),
                    bitrate = it.bitrate
                )
            }
        }

        val muxedStream = streamInfo.videoStreams
            .asSequence()
            .filterNot { it.isVideoOnly }
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.url?.isNotBlank() == true }
            .minWithOrNull(
                compareBy(
                    { if (it.format == MediaFormat.MPEG_4) 0 else 1 },
                    { it.bitrate.coerceAtLeast(0) }
                )
            )

        return muxedStream?.let {
            val url = it.url ?: return@let null
            RemotePlaybackStream(
                url = url,
                source = "muxed",
                formatName = it.format?.name.orEmpty(),
                bitrate = it.bitrate
            )
        }
    }

    private fun pickBestAudioStream(streams: List<AudioStream>) =
        streams.filter { it.url?.isNotBlank() == true }.let { playable ->
            if (playable.isEmpty()) return@let null
            val maxBitrate = playable.maxOf { it.bitrate }
            val nearBest = playable.filter { maxBitrate - it.bitrate <= BITRATE_TIE_MARGIN_BPS }
            nearBest.firstOrNull { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
                ?: nearBest.maxByOrNull { it.bitrate }
        }

    suspend fun prefetchStream(result: YouTubeResult) = withContext(Dispatchers.IO) {
        if (getCachedStream(result.videoId) != null) return@withContext
        Log.d("YouTubeRepo", "Prefetching stream for ${result.title}")
        extractAudioStream(result)
    }

    fun clearCache() {
        audioStreamCache.clear()
        audioStreamCacheExpiresAtMs.clear()
        audioDurationCache.clear()
    }
}