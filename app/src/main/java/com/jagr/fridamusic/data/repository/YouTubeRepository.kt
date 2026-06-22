@file:OptIn(UnstableApi::class)
package com.jagr.fridamusic.data.repository

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.jagr.fridamusic.data.remote.innertube.YouTube
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.data.remote.innertube.ResultType
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val deezerToYoutubeMap = ConcurrentHashMap<String, String>()
    
    private val YOUTUBE_VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")

    suspend fun extractAudioStream(result: YouTubeResult, trustVideoId: Boolean): RemotePlaybackStream? = withContext(Dispatchers.IO) {
        try {
            var realYtId = result.videoId.takeIf { trustVideoId && YOUTUBE_VIDEO_ID_PATTERN.matches(it) }
                ?: deezerToYoutubeMap[result.videoId]

            if (realYtId == null) {
                val ytMatch = YouTube.search("${result.title} ${result.artist} audio").firstOrNull { it.type == ResultType.SONG }
                realYtId = ytMatch?.videoId ?: return@withContext null
                deezerToYoutubeMap[result.videoId] = realYtId
            }

            getCachedStream(realYtId)?.let { url ->
                return@withContext RemotePlaybackStream(url, "cache", "", 0)
            }

            val videoUrl = "https://www.youtube.com/watch?v=$realYtId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val playbackStream = selectPlayableRemoteStream(streamInfo)

            if (playbackStream != null) {
                cacheStream(realYtId, playbackStream.url, streamInfo.duration * 1000L)
            }
            playbackStream
        } catch (e: Exception) {
            Log.e("YouTubeRepo", "Extraction failed for ${result.title}", e)
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
        val audioStream = streamInfo.audioStreams
            .filter { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
            .maxByOrNull { it.bitrate }
            ?: streamInfo.audioStreams.maxByOrNull { it.bitrate }

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
    
    suspend fun prefetchStream(result: YouTubeResult) = withContext(Dispatchers.IO) {
        if (getCachedStream(result.videoId) != null) return@withContext
        Log.d("YouTubeRepo", "Prefetching stream for ${result.title}")
        extractAudioStream(result, trustVideoId = true)
    }

    suspend fun getStreamOrExtract(result: YouTubeResult, trustVideoId: Boolean): RemotePlaybackStream? {
        getCachedStream(result.videoId)?.let { url ->
            return RemotePlaybackStream(url, "cache", "", 0)
        }
        return extractAudioStream(result, trustVideoId)
    }

    fun clearCache() {
        audioStreamCache.clear()
        audioStreamCacheExpiresAtMs.clear()
        audioDurationCache.clear()
    }
}
