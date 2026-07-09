package com.jagr.fridamusic.data.repository

import android.content.Context
import android.net.Uri
import com.jagr.fridamusic.domain.model.Song
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class ArtworkRepository(private val context: Context) {
    private val imageUrlCache = ConcurrentHashMap<String, String>()
    private val imageUrlMisses = ConcurrentHashMap<String, Long>()
    private val imageUrlLookups = ConcurrentHashMap<String, Deferred<String?>>()
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val METADATA_NETWORK_TIMEOUT_MS = 4_000

    fun getCachedUrl(key: String): String? = imageUrlCache[key]
    
    suspend fun getSongImageUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val preloadedArtwork = song.artworkUri.toString()
            .takeIf { it.isNotBlank() && it != "content://media/external/audio/albumart/-1" }
        if (preloadedArtwork != null && !preloadedArtwork.endsWith("-1")) return@withContext preloadedArtwork

        val cacheKey = "song_${songArtworkMetadataKey(song)}"
        resolveArtworkUrl(cacheKey) {
            fetchAlbumArt(song.title, song.artist, requireTrackMatch = true)
        }
    }

    suspend fun getArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        val cacheKey = "artist_${normalizeArtworkText(artistName)}"
        resolveArtworkUrl(cacheKey) {
            fetchAlbumArt(artistName, "", requireTrackMatch = false)
        }
    }

    private fun songArtworkMetadataKey(song: Song): String {
        return listOf(
            normalizeArtworkText(song.title),
            normalizeArtworkText(song.artist),
            normalizeArtworkText(song.album)
        ).joinToString("|")
    }

    private suspend fun resolveArtworkUrl(cacheKey: String, lookup: () -> String?): String? {
        imageUrlCache[cacheKey]?.let { return it }
        imageUrlMisses[cacheKey]?.let { failedAt ->
            if (System.currentTimeMillis() - failedAt < 1000 * 60 * 60 * 24) return null
        }

        val request = imageUrlLookups.computeIfAbsent(cacheKey) {
            repositoryScope.async { lookup() }
        }
        val url = try {
            request.await()
        } finally {
            imageUrlLookups.remove(cacheKey, request)
        }

        if (url.isNullOrBlank()) {
            imageUrlMisses[cacheKey] = System.currentTimeMillis()
        } else {
            imageUrlMisses.remove(cacheKey)
            imageUrlCache[cacheKey] = url
        }
        return url
    }

    private fun fetchAlbumArt(
        title: String,
        artist: String?,
        requireTrackMatch: Boolean
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val cleanQuery = normalizeArtworkText(title)
            val cleanArtist = if (artist.isNullOrBlank() || artist.contains("unknown", ignoreCase = true)) "" else artist.trim()
            val finalQueryText = "$cleanQuery $cleanArtist".trim().replace(Regex("\\s+"), " ")
            if (finalQueryText.isBlank()) return null

            val encodedQuery = URLEncoder.encode(finalQueryText, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=10"
            val activeConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = METADATA_NETWORK_TIMEOUT_MS
                readTimeout = METADATA_NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", "FridaMusic/1.0")
            }
            connection = activeConnection
            if (activeConnection.responseCode !in 200..299) return null
            val response = activeConnection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val results = json.getJSONArray("results")

            var bestUrl: String? = null
            var bestScore = 0

            for (index in 0 until results.length()) {
                val result = results.optJSONObject(index) ?: continue
                val artworkUrl = result.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
                val normalizedArtworkUrl = normalizeAppleArtworkUrl(artworkUrl)

                if (!requireTrackMatch) return normalizedArtworkUrl

                val score = artworkMetadataScore(
                    expectedTitle = title,
                    expectedArtist = cleanArtist,
                    resultTitle = result.optString("trackName"),
                    resultArtist = result.optString("artistName")
                )

                if (score > bestScore) {
                    bestScore = score
                    bestUrl = normalizedArtworkUrl
                }
            }

            bestUrl
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun artworkMetadataScore(
        expectedTitle: String,
        expectedArtist: String,
        resultTitle: String,
        resultArtist: String
    ): Int {
        val titleScore = textMatchScore(
            expected = normalizeArtworkText(expectedTitle),
            actual = normalizeArtworkText(resultTitle)
        )
        if (titleScore < 2) return 0

        val artistScore = if (expectedArtist.isBlank()) {
            1
        } else {
            textMatchScore(
                expected = normalizeArtworkText(expectedArtist),
                actual = normalizeArtworkText(resultArtist)
            )
        }
        if (expectedArtist.isNotBlank() && artistScore == 0) return 0

        return titleScore * 10 + artistScore
    }

    private fun textMatchScore(expected: String, actual: String): Int {
        if (expected.isBlank() || actual.isBlank()) return 0
        if (expected == actual) return 4
        if ((expected.length >= 4 && actual.contains(expected)) ||
            (actual.length >= 4 && expected.contains(actual))
        ) {
            return 3
        }

        val expectedTokens = expected.split(" ").filter { it.length > 1 }.toSet()
        val actualTokens = actual.split(" ").filter { it.length > 1 }.toSet()
        if (expectedTokens.isEmpty() || actualTokens.isEmpty()) return 0

        val overlap = expectedTokens.count { it in actualTokens }
        val expectedCoverage = overlap.toDouble() / expectedTokens.size.toDouble()
        val actualCoverage = overlap.toDouble() / actualTokens.size.toDouble()

        return when {
            expectedCoverage >= 0.80 && actualCoverage >= 0.50 -> 2
            expectedCoverage >= 0.60 && actualCoverage >= 0.60 -> 1
            else -> 0
        }
    }

    fun normalizeAppleArtworkUrl(url: String): String {
        return url
            .replace("100x100bb.jpg", "600x600bb.jpg")
            .replace("100x100bb.png", "600x600bb.png")
    }

    fun normalizeArtworkText(value: String?): String {
        return value.orEmpty()
            .lowercase()
            .replace(Regex("(?i)\\.(mp3|m4a|wav|flac|ogg)$"), " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)\\b(feat|ft|featuring)\\b\\.?"), " ")
            .replace(Regex("(?i)\\b(official|video|audio|lyrics|lyric|visualizer|remaster|remastered|explicit|clean|edit)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    fun clearCache() {
        imageUrlCache.clear()
        imageUrlMisses.clear()
        imageUrlLookups.clear()
    }
}
