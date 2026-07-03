package com.jagr.fridamusic.data.repository

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.jagr.fridamusic.data.remote.innertube.YouTube
import com.jagr.fridamusic.domain.lyrics.LyricsParser
import com.jagr.fridamusic.domain.lyrics.LyricsResult
import com.jagr.fridamusic.domain.lyrics.LyricsSyncState
import com.jagr.fridamusic.domain.model.Song
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

private const val LOCAL_PROVIDER_TIMEOUT_MS = 300L
private const val LRCLIB_PROVIDER_TIMEOUT_MS = 8_000L
private const val REMOTE_PROVIDER_TIMEOUT_MS = 700L

interface LyricsProvider {
    val name: String
    val timeoutMs: Long
        get() = REMOTE_PROVIDER_TIMEOUT_MS
    suspend fun getLyrics(song: Song): LyricsResult?
}

class LyricsRepository(context: Context) {
    private val settingsManager = SettingsManager(context)
    private val memoryCache = ConcurrentHashMap<String, LyricsResult>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<LyricsResult>>()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val providers: List<LyricsProvider> = listOf(
        LrclibExactLyricsProvider(),
        LrclibSearchLyricsProvider(),
        YouTubeMusicLyricsProvider(),
        LocalLyricsProvider(),
        LyricsOvhProvider(),
        YouTubeCaptionLyricsProvider()
    )

    suspend fun getLyricsResult(song: Song): LyricsResult {
        val key = song.lyricsCacheKey()
        memoryCache[key]?.let { return it }
        settingsManager.cachedLyrics(key)?.let { cached ->
            cached.toUsableLyricsResult("Cache")?.let { result ->
                memoryCache[key] = result
                return result
            }
        }
        negativeCache[key]?.takeIf { System.currentTimeMillis() - it < NEGATIVE_CACHE_TTL_MS }?.let {
            return LyricsResult.NotAvailable
        }

        val request = repositoryScope.async {
            queryProviders(song, key)
        }
        val activeRequest = inFlightRequests.putIfAbsent(key, request)
        if (activeRequest != null) {
            request.cancel()
            return activeRequest.await()
        }

        return try {
            request.await()
        } finally {
            inFlightRequests.remove(key, request)
        }
    }

    private suspend fun queryProviders(song: Song, key: String): LyricsResult {
        for (provider in providers) {
            Log.d(LYRICS_LOG_TAG, "provider=${provider.name} start title=${song.title} artist=${song.artist}")
            val providerRequest = repositoryScope.async {
                runCatching { provider.getLyrics(song) }.getOrNull()
            }
            val result = withTimeoutOrNull(provider.timeoutMs) {
                providerRequest.await()
            }
            if (result == null) {
                providerRequest.cancel()
                Log.d(LYRICS_LOG_TAG, "provider=${provider.name} timeout_or_empty")
                continue
            }
            when (result) {
                is LyricsResult.Available -> {
                    val usable = result.takeIfUsable() ?: continue
                    memoryCache[key] = usable
                    if (usable.source.isLrclibSource()) {
                        usable.plainText?.takeIf { it.isNotBlank() }?.let { settingsManager.setCachedLyrics(key, it) }
                    }
                    Log.d(
                        LYRICS_LOG_TAG,
                        "provider=${provider.name} success state=${usable.syncState} lines=${usable.lines.size} text=${usable.plainText?.length ?: 0}"
                    )
                    return usable
                }
                LyricsResult.NotAvailable -> Log.d(LYRICS_LOG_TAG, "provider=${provider.name} not_available")
                is LyricsResult.Error -> Log.d(LYRICS_LOG_TAG, "provider=${provider.name} error=${result.message}")
                LyricsResult.Loading -> Unit
            }
        }

        negativeCache[key] = System.currentTimeMillis()
        return LyricsResult.NotAvailable
    }

    private inner class LocalLyricsProvider : LyricsProvider {
        override val name = "Local lyrics"
        override val timeoutMs = LOCAL_PROVIDER_TIMEOUT_MS

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            settingsManager.localLyrics(song.id)?.takeIf { it.isNotBlank() }?.toUsableLyricsResult("Manual")?.let {
                return@withContext it
            }

            song.lyrics?.takeIf { it.isNotBlank() }?.toUsableLyricsResult("Embedded")?.let {
                return@withContext it
            }

            val localFile = song.localFileOrNull()
            val sidecar = localFile?.sidecarLyrics()
            sidecar?.toUsableLyricsResult(name)?.let {
                return@withContext it
            }

            val embedded = localFile?.embeddedLyrics()
            embedded?.toUsableLyricsResult(name)?.let {
                return@withContext it
            }

            null
        }
    }

    private inner class LrclibExactLyricsProvider : LyricsProvider {
        override val name = "LRCLIB exact"
        override val timeoutMs = LRCLIB_PROVIDER_TIMEOUT_MS

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            val durationSeconds = song.duration.takeIf { it > 0L }?.let { (it / 1000L).toInt() } ?: 0
            val params = buildMap {
                put("track_name", song.title.apiSearchQuery())
                put("artist_name", song.artist.apiSearchQuery())
                song.album.apiSearchQuery().takeIf { it.isNotBlank() }?.let { put("album_name", it) }
                if (durationSeconds > 0) put("duration", durationSeconds.toString())
            }
            Log.d(LYRICS_LOG_TAG, "LRCLIB exact query=$params")
            fetchObject("/api/get", params)?.toLyricsResultIfUsable(song, name)
        }
    }

    private inner class LrclibSearchLyricsProvider : LyricsProvider {
        override val name = "LRCLIB search"
        override val timeoutMs = LRCLIB_PROVIDER_TIMEOUT_MS

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            val metadataParams = buildMap {
                put("track_name", song.title.apiSearchQuery())
                put("artist_name", song.artist.apiSearchQuery())
                song.album.apiSearchQuery().takeIf { it.isNotBlank() }?.let { put("album_name", it) }
            }
            Log.d(LYRICS_LOG_TAG, "LRCLIB search metadata query=$metadataParams")
            val metadataSearch = fetchArray("/api/search", metadataParams)?.jsonObjects().orEmpty()
            val querySearch = if (metadataSearch.isEmpty()) {
                val q = "${song.title.apiSearchQuery()} ${song.artist.apiSearchQuery()}".trim()
                Log.d(LYRICS_LOG_TAG, "LRCLIB search free-text query=\"$q\"")
                fetchArray("/api/search", mapOf("q" to q))?.jsonObjects().orEmpty()
            } else {
                emptyList()
            }
            val candidates = metadataSearch + querySearch
            Log.d(LYRICS_LOG_TAG, "LRCLIB search returned ${candidates.size} raw candidate(s)")

            val best = candidates
                .filter { candidate ->
                    val matches = candidate.matchesSong(song)
                    if (!matches) {
                        Log.d(
                            LYRICS_LOG_TAG,
                            "LRCLIB candidate rejected: title=\"${candidate.optString("trackName")}\" " +
                                    "artist=\"${candidate.optString("artistName")}\" " +
                                    "titleSim=${"%.2f".format(titleSimilarity(candidate.optString("trackName"), song.title))} " +
                                    "durationDelta=${candidate.durationDeltaSeconds(song)}"
                        )
                    }
                    matches
                }
                .maxByOrNull { it.matchScore(song) }

            if (best != null) {
                Log.d(LYRICS_LOG_TAG, "LRCLIB best candidate: title=\"${best.optString("trackName")}\" artist=\"${best.optString("artistName")}\" score=${best.matchScore(song)}")
            }
            best?.toLyricsResultIfUsable(song, name)
        }
    }

    private inner class YouTubeMusicLyricsProvider : LyricsProvider {
        override val name = "YouTube Music"
        override val timeoutMs = LRCLIB_PROVIDER_TIMEOUT_MS

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            val videoId = song.youtubeVideoIdOrNull() ?: return@withContext null
            val result = YouTube.fetchLyrics(videoId) ?: return@withContext null
            val sourceLabel = result.sourceAttribution?.let { "$name ($it)" } ?: name
            result.text.toUsableLyricsResult(sourceLabel)
        }
    }


    private inner class LyricsOvhProvider : LyricsProvider {
        override val name = "lyrics.ovh"

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            val artist = song.artist.cleanedLyricsQuery()
            val title = song.title.cleanedLyricsQuery()
            if (artist.isBlank() || title.isBlank()) return@withContext null

            val url = "https://api.lyrics.ovh/v1/${artist.encodePathSegment()}/${title.encodePathSegment()}"
            val connection = openRawConnection(url) ?: return@withContext null
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val lyrics = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .optString("lyrics")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: return@withContext null
                lyrics.toUsableLyricsResult(name)
            } catch (_: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }
    }

    private inner class YouTubeCaptionLyricsProvider : LyricsProvider {
        override val name = "YouTube captions"
        override val timeoutMs = REMOTE_PROVIDER_TIMEOUT_MS

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            val videoId = song.youtubeVideoIdOrNull() ?: return@withContext null
            val transcript = YouTube.getTranscript(videoId)?.takeIf { it.isNotBlank() } ?: return@withContext null
            val webVttLines = LyricsParser.parseWebVtt(transcript)
            val timedTextLines = if (webVttLines.isEmpty()) LyricsParser.parseYouTubeTimedText(transcript) else emptyList()
            val syncedLines = webVttLines.ifEmpty { timedTextLines }
            if (syncedLines.isNotEmpty()) {
                LyricsResult.Available(
                    lines = syncedLines,
                    plainText = transcript,
                    source = name,
                    syncState = LyricsSyncState.SYNCED
                )
            } else {
                transcript.toUsableLyricsResult(name)
            }
        }
    }

    private fun fetchObject(endpoint: String, params: Map<String, String>): JSONObject? {
        val connection = openConnection(endpoint, params) ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(LYRICS_LOG_TAG, "lrclib object http=${connection.responseCode} endpoint=$endpoint")
                return null
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (error: Exception) {
            Log.d(LYRICS_LOG_TAG, "lrclib object failed endpoint=$endpoint error=${error.javaClass.simpleName}:${error.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchArray(endpoint: String, params: Map<String, String>): JSONArray? {
        val connection = openConnection(endpoint, params) ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(LYRICS_LOG_TAG, "lrclib array http=${connection.responseCode} endpoint=$endpoint")
                return null
            }
            JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (error: Exception) {
            Log.d(LYRICS_LOG_TAG, "lrclib array failed endpoint=$endpoint error=${error.javaClass.simpleName}:${error.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.jsonObjects(): List<JSONObject> =
        (0 until length()).mapNotNull { index -> optJSONObject(index) }

    private fun openConnection(endpoint: String, params: Map<String, String>): HttpURLConnection? {
        return try {
            val query = params.entries
                .filter { it.value.isNotBlank() }
                .joinToString("&") { (key, value) ->
                    "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                }
            (URL("https://lrclib.net$endpoint?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = LRCLIB_PROVIDER_TIMEOUT_MS.toInt()
                readTimeout = LRCLIB_PROVIDER_TIMEOUT_MS.toInt()
                setRequestProperty("User-Agent", "FridaMusic/1.0 (https://github.com/jagrdev-MX)")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openRawConnection(url: String): HttpURLConnection? =
        try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = REMOTE_PROVIDER_TIMEOUT_MS.toInt()
                readTimeout = REMOTE_PROVIDER_TIMEOUT_MS.toInt()
                setRequestProperty("User-Agent", "FridaMusic/1.0 (https://github.com/jagrdev-MX)")
            }
        } catch (_: Exception) {
            null
        }

    private fun JSONObject.toLyricsResultIfUsable(song: Song, source: String): LyricsResult? {
        if (!matchesSong(song)) return null
        val synced = optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val syncedResult = synced?.toUsableLyricsResult(source)
        return syncedResult?.takeIf { it.lines.isNotEmpty() }
            ?: plain?.toUsableLyricsResult(source)
            ?: syncedResult
    }

    private fun String.toUsableLyricsResult(source: String): LyricsResult.Available? =
        LyricsParser.toResult(this, source = source).let { result ->
            (result as? LyricsResult.Available)?.takeIfUsable()
        }

    private fun LyricsResult.Available.takeIfUsable(): LyricsResult.Available? {
        val rawPlainText = plainText.orEmpty()
        val normalizedText = rawPlainText
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\[[^\\]]+\\]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val lineTexts = lines.map { it.content.trim() }.filter { it.isNotBlank() }
        val combined = if (normalizedText.isNotBlank()) normalizedText else lineTexts.joinToString(" ")
        val letters = combined.count { it.isLetter() }
        val words = combined.split(Regex("\\s+")).count { word ->
            word.count { it.isLetterOrDigit() } >= 2
        }
        val plainLines = rawPlainText.lines().filter { it.count(Char::isLetter) >= 3 }
        val syncedLines = lineTexts.count { it.count(Char::isLetter) >= 3 }

        return takeIf {
            syncedLines >= MIN_SYNCED_LYRICS_LINES ||
                    plainLines.size >= MIN_PLAIN_LYRICS_LINES ||
                    letters >= MIN_LYRICS_LETTERS ||
                    words >= MIN_LYRICS_WORDS
        }
    }

    private fun JSONObject.matchesSong(song: Song): Boolean {
        val candidateTitle = optString("trackName").ifBlank { optString("name") }
        val titleSimilarity = titleSimilarity(candidateTitle, song.title)
        val candidateArtist = optString("artistName")
        val titleOk = titleSimilarity(
            candidateTitle,
            song.title
        ) >= TITLE_MATCH_THRESHOLD
        val artistOk = artistMatches(candidateArtist, song.artist)
        val durationDelta = durationDeltaSeconds(song)
        val durationOk = durationDelta == null || durationDelta <= DURATION_TOLERANCE_SECONDS
        val strongTitleArtist = titleSimilarity >= STRONG_TITLE_MATCH_THRESHOLD &&
                candidateArtist.normalizedArtistKey() == song.artist.normalizedArtistKey()
        return titleOk && artistOk && (durationOk || strongTitleArtist)
    }

    private fun JSONObject.matchScore(song: Song): Int {
        var score = 0
        val titleSim = titleSimilarity(optString("trackName").ifBlank { optString("name") }, song.title)
        score += when {
            titleSim >= 0.99 -> 60
            titleSim >= 0.85 -> 50
            titleSim >= TITLE_MATCH_THRESHOLD -> 35
            else -> 0
        }
        if (optString("artistName").normalizedArtistKey() == song.artist.normalizedArtistKey()) score += 35
        else if (artistMatches(optString("artistName"), song.artist)) score += 20
        if (optString("albumName").normalizedLyricsKey() == song.album.normalizedLyricsKey()) score += 15
        durationDeltaSeconds(song)?.let { score += (20 - it.toInt()).coerceAtLeast(0) }
        if (optString("syncedLyrics").isNotBlank()) score += 8
        return score
    }

    private fun Song.lyricsCacheKey(): String =
        "lrclib-v3|${extractCoreTitle(title).normalizedLyricsKey()}|${artist.normalizedArtistKey()}"

    private fun JSONObject.durationDeltaSeconds(song: Song): Double? {
        val candidateDuration = optDouble("duration", 0.0).takeIf { it > 0.0 } ?: return null
        val songDuration = song.duration.takeIf { it > 0L }?.div(1000.0) ?: return null
        return abs(candidateDuration - songDuration)
    }

    private fun String.isLrclibSource(): Boolean =
        startsWith("LRCLIB", ignoreCase = true)

    private fun artistMatches(candidateRaw: String, songRaw: String): Boolean {
        val candidate = candidateRaw.normalizedArtistKey()
        val song = songRaw.normalizedArtistKey()
        if (candidate.isBlank() || song.isBlank()) return true
        if (candidate == song || candidate.contains(song) || song.contains(candidate)) return true

        val candidateTokens = candidate.split(" ").filter { it.length > 1 }.toSet()
        val songTokens = song.split(" ").filter { it.length > 1 }.toSet()
        if (candidateTokens.isEmpty() || songTokens.isEmpty()) return false
        val overlap = candidateTokens.intersect(songTokens).size
        val minTokenCount = minOf(candidateTokens.size, songTokens.size)
        return overlap >= 2 || overlap == minTokenCount
    }

    private fun Song.localFileOrNull(): File? {
        val path = data.takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) } ?: return null
        return File(path).takeIf { it.exists() && it.isFile }
    }

    private fun Song.youtubeVideoIdOrNull(): String? {
        val uriText = uri.toString()
        val fromUri = runCatching {
            val parsed = uriText.toUri()
            parsed.getQueryParameter("v")
        }.getOrNull()
        return fromUri
            ?: data.takeIf { YOUTUBE_VIDEO_ID.matches(it) }
            ?: YOUTUBE_VIDEO_ID.find(uriText)?.value
    }

    private fun File.sidecarLyrics(): String? {
        val parent = parentFile ?: return null
        val name = name.substringBeforeLast('.', name)
        return listOf(
            File(parent, "$name.lrc"),
            File(parent, "$name.LRC"),
            File(parent, "$name.txt")
        ).firstOrNull { it.exists() && it.isFile && it.length() in 1..256_000L }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
    }

    private fun File.embeddedLyrics(): String? {
        val id3 = extractId3Lyrics()
        if (!id3.isNullOrBlank()) return id3
        return extractTextCommentLyrics()
    }

    private fun File.extractId3Lyrics(): String? {
        if (extension.lowercase() !in setOf("mp3", "aac", "m4a")) return null
        return runCatching {
            inputStream().use { input ->
                val header = ByteArray(10)
                if (input.read(header) != 10 || String(header, 0, 3) != "ID3") return@runCatching null
                val tagSize = syncSafeInt(header.copyOfRange(6, 10)).coerceAtMost(1_048_576)
                val tag = ByteArray(tagSize)
                if (input.read(tag) <= 0) return@runCatching null
                extractId3FrameText(tag, "SYLT") ?: extractId3FrameText(tag, "USLT")
            }
        }.getOrNull()
    }

    private fun extractId3FrameText(tag: ByteArray, frameId: String): String? {
        var offset = 0
        while (offset + 10 < tag.size) {
            val id = tag.decodeAscii(offset, 4)
            if (!id.all { it.isLetterOrDigit() }) break
            val size = int32(tag, offset + 4)
            if (size <= 0 || offset + 10 + size > tag.size) break
            if (id == frameId) {
                val frame = tag.copyOfRange(offset + 10, offset + 10 + size)
                return if (frameId == "SYLT") {
                    decodeSyltFrame(frame)
                } else {
                    decodeUsltFrame(frame)
                }.takeIf { it.isNotBlank() }
            }
            offset += 10 + size
        }
        return null
    }

    private fun decodeUsltFrame(frame: ByteArray): String {
        if (frame.size < 5) return ""
        val encoding = frame[0].toInt()
        var textStart = 4
        while (textStart < frame.lastIndex && frame[textStart].toInt() != 0) textStart++
        textStart = (textStart + 1).coerceAtMost(frame.size)
        return decodeId3Text(frame.copyOfRange(textStart, frame.size), encoding)
            .replace(Regex("\\u0000+"), "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun decodeSyltFrame(frame: ByteArray): String {
        if (frame.size < 10) return ""
        val encoding = frame[0].toInt()
        val delimiterSize = if (encoding == 1 || encoding == 2) 2 else 1
        var offset = 6
        offset = frame.indexAfterTextDelimiter(offset, delimiterSize)

        val lines = mutableListOf<String>()
        while (offset in frame.indices) {
            val textEnd = frame.indexOfTextDelimiter(offset, delimiterSize).takeIf { it >= 0 } ?: break
            val text = decodeId3Text(frame.copyOfRange(offset, textEnd), encoding)
            offset = textEnd + delimiterSize
            if (offset + 4 > frame.size) break
            val timestampMs = int32(frame, offset).toLong()
            offset += 4
            if (text.isNotBlank()) {
                lines.add("[${formatLrcTimestamp(timestampMs)}] $text")
            }
        }
        return lines.joinToString("\n")
    }

    private fun File.extractTextCommentLyrics(): String? {
        return runCatching {
            val head = inputStream().use { it.readNBytesCompat(512_000) }
            val text = head.toString(Charsets.ISO_8859_1)
            COMMENT_KEYS.firstNotNullOfOrNull { key ->
                Regex("(?is)$key=([^\\u0000]+)").find(text)?.groupValues?.getOrNull(1)
            }?.trim()
        }.getOrNull()
    }

    private fun java.io.InputStream.readNBytesCompat(size: Int): ByteArray {
        val buffer = ByteArray(size)
        val read = read(buffer)
        return if (read <= 0) ByteArray(0) else buffer.copyOf(read)
    }

    private fun ByteArray.decodeAscii(offset: Int, count: Int): String =
        copyOfRange(offset, offset + count).toString(Charsets.ISO_8859_1)

    private fun ByteArray.indexAfterTextDelimiter(start: Int, delimiterSize: Int): Int {
        val index = indexOfTextDelimiter(start, delimiterSize)
        return if (index >= 0) (index + delimiterSize).coerceAtMost(size) else start
    }

    private fun ByteArray.indexOfTextDelimiter(start: Int, delimiterSize: Int): Int {
        var index = start
        while (index <= size - delimiterSize) {
            val isDelimiter = (0 until delimiterSize).all { this[index + it].toInt() == 0 }
            if (isDelimiter) return index
            index++
        }
        return -1
    }

    private fun formatLrcTimestamp(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val hundredths = (ms % 1000L) / 10L
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }

    private fun decodeId3Text(bytes: ByteArray, encoding: Int): String {
        val charset = when (encoding) {
            1, 2 -> Charsets.UTF_16
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return bytes.toString(charset).trim('\u0000', '\uFEFF', ' ', '\n', '\r')
    }

    private fun syncSafeInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0x7F) shl 21) or
                ((bytes[1].toInt() and 0x7F) shl 14) or
                ((bytes[2].toInt() and 0x7F) shl 7) or
                (bytes[3].toInt() and 0x7F)

    private fun int32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

    private fun String.cleanedLyricsQuery(): String =
        normalizedLyricsKey().replace(Regex("\\s+"), " ").trim()

    private fun String.apiSearchQuery(): String =
        replace(YOUTUBE_METADATA_NOISE, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.encodePathSegment(): String =
        URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun String.normalizedLyricsKey(): String {
        val noMarks = Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noMarks
            .lowercase()
            .replace(
                Regex(
                    "\\b(remix|live|official video|official audio|official|audio|" +
                            "lyrics|lyric video|slowed|reverb|sped up|speed up|nightcore|" +
                            "bass boosted|ft|feat|featuring|con|with|vol|volume|version|" +
                            "remaster|remastered|extended|radio edit|acoustic|instrumental|" +
                            "topic|vevo|records|recordings|official channel|" +
                            "4k|hd|hq|visualizer|video oficial|traducida|subtitulada)\\b"
                ),
                " "
            )
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.normalizedArtistKey(): String =
        normalizedLyricsKey()
            .replace(Regex("\\b(and|y|&|x)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractCoreTitle(raw: String): String {
        val stripped = FEAT_SUFFIX.replace(raw, "")
            .replace(Regex("\\s*[\\(\\[]\\s*[\\)\\]]"), "") // remove empty parens
            .trim()
        return stripped.ifBlank { raw }
    }

    private fun titleSimilarity(rawA: String, rawB: String): Double {
        val a = extractCoreTitle(rawA).normalizedLyricsKey()
        val b = extractCoreTitle(rawB).normalizedLyricsKey()
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0

        val aTok = a.split(" ").filter { it.length > 1 }
        val bTok = b.split(" ").filter { it.length > 1 }

        val bSet = bTok.toHashSet()
        val aSet = aTok.toHashSet()
        val covAinB = if (aTok.isEmpty()) 0.0 else aTok.count { it in bSet } / aTok.size.toDouble()
        val covBinA = if (bTok.isEmpty()) 0.0 else bTok.count { it in aSet } / bTok.size.toDouble()

        val lenRatio = minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)

        val tokenSim = covAinB * 0.65 + covBinA * 0.35

        val lev = levenshteinRatio(a, b)

        val nTokens = (aSet + bSet).size
        return if (nTokens <= 2) {
            lev * 0.6 + tokenSim * lenRatio * 0.4
        } else {
            tokenSim * lenRatio * 0.7 + lev * 0.3
        }
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        val la = a.length; val lb = b.length
        if (la == 0 || lb == 0) return 0.0
        var dp = IntArray(lb + 1) { it }
        for (i in 1..la) {
            val newDp = IntArray(lb + 1) { if (it == 0) i else 0 }
            for (j in 1..lb) {
                newDp[j] = minOf(
                    newDp[j - 1] + 1,
                    dp[j] + 1,
                    dp[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            dp = newDp
        }
        return 1.0 - dp[lb].toDouble() / maxOf(la, lb)
    }

    companion object {
        private const val MIN_SYNCED_LYRICS_LINES = 2
        private const val MIN_PLAIN_LYRICS_LINES = 3
        private const val MIN_LYRICS_LETTERS = 40
        private const val MIN_LYRICS_WORDS = 8
        private const val NEGATIVE_CACHE_TTL_MS = 2 * 60 * 1000L
        private const val DURATION_TOLERANCE_SECONDS = 12.0
        private const val TITLE_MATCH_THRESHOLD = 0.60
        private const val STRONG_TITLE_MATCH_THRESHOLD = 0.95
        private const val LYRICS_LOG_TAG = "FridaLyrics"
        private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val COMMENT_KEYS = listOf("SYNCEDLYRICS", "UNSYNCEDLYRICS", "LYRICS")
        private val FEAT_SUFFIX = Regex(
            """\s*[\(\[（]?\s*(?:ft\.?|feat\.?|featuring|con|with|x)\s+.+?[\)\]）]?\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val YOUTUBE_METADATA_NOISE = Regex(
            "(?i)\\b(official video|official audio|official music video|lyric video|" +
                    "video oficial|video lyrics|visualizer|topic|vevo|official channel|" +
                    "hd|hq|4k|traducida|subtitulada)\\b"
        )
    }
}