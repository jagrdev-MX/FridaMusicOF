package com.jagr.fridamusic.data.repository

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface LyricsProvider {
    val name: String
    suspend fun getLyrics(song: Song): LyricsResult?
}

class LyricsRepository(private val context: Context) {
    private val settingsManager = SettingsManager(context)
    private val memoryCache = ConcurrentHashMap<String, LyricsResult>()
    private val negativeCache = ConcurrentHashMap<String, Long>()

    private val providers: List<LyricsProvider> = listOf(
        LocalLyricsProvider(),
        LrclibLyricsProvider(),
        YouTubeCaptionLyricsProvider()
    )

    suspend fun getLyricsResult(song: Song): LyricsResult {
        settingsManager.localLyrics(song.id)?.takeIf { it.isNotBlank() }?.let {
            return LyricsParser.toResult(it, source = "Manual")
        }
        song.lyrics?.takeIf { it.isNotBlank() }?.let {
            return LyricsParser.toResult(it, source = "Embedded")
        }

        val key = song.lyricsCacheKey()
        memoryCache[key]?.let { return it }
        settingsManager.cachedLyrics(key)?.let { cached ->
            val result = LyricsParser.toResult(cached, source = "Cache")
            memoryCache[key] = result
            return result
        }
        negativeCache[key]?.takeIf { System.currentTimeMillis() - it < NEGATIVE_CACHE_TTL_MS }?.let {
            return LyricsResult.NotAvailable
        }

        for (provider in providers) {
            val result = runCatching { provider.getLyrics(song) }.getOrNull() ?: continue
            when (result) {
                is LyricsResult.Available -> {
                    memoryCache[key] = result
                    result.plainText?.takeIf { it.isNotBlank() }?.let { settingsManager.setCachedLyrics(key, it) }
                    return result
                }
                LyricsResult.NotAvailable -> Unit
                is LyricsResult.Error -> Unit
                LyricsResult.Loading -> Unit
            }
        }

        negativeCache[key] = System.currentTimeMillis()
        return LyricsResult.NotAvailable
    }

    suspend fun getLyrics(song: Song): String? {
        return when (val result = getLyricsResult(song)) {
            is LyricsResult.Available -> result.plainText ?: result.lines.joinToString("\n") { it.content }
            else -> null
        }
    }

    private inner class LocalLyricsProvider : LyricsProvider {
        override val name = "Local lyrics"

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            val localFile = song.localFileOrNull()
            val sidecar = localFile?.sidecarLyrics()
            if (!sidecar.isNullOrBlank()) {
                return@withContext LyricsParser.toResult(sidecar, source = name)
            }

            val embedded = localFile?.embeddedLyrics()
            if (!embedded.isNullOrBlank()) {
                return@withContext LyricsParser.toResult(embedded, source = name)
            }

            null
        }
    }

    private inner class LrclibLyricsProvider : LyricsProvider {
        override val name = "LRCLIB"

        override suspend fun getLyrics(song: Song): LyricsResult? = withContext(Dispatchers.IO) {
            val durationSeconds = song.duration.takeIf { it > 0L }?.let { (it / 1000L).toInt() } ?: 0
            val exact = if (durationSeconds > 0) {
                fetchObject(
                    endpoint = "/api/get-cached",
                    params = mapOf(
                        "track_name" to song.title.cleanedLyricsQuery(),
                        "artist_name" to song.artist.cleanedLyricsQuery(),
                        "album_name" to song.album.cleanedLyricsQuery().ifBlank { "Unknown Album" },
                        "duration" to durationSeconds.toString()
                    )
                )
            } else {
                null
            }

            exact?.toLyricsResultIfUsable(song, name)?.let { return@withContext it }

            val search = fetchArray(
                endpoint = "/api/search",
                params = buildMap {
                    put("track_name", song.title.cleanedLyricsQuery())
                    put("artist_name", song.artist.cleanedLyricsQuery())
                    song.album.cleanedLyricsQuery().takeIf { it.isNotBlank() }?.let { put("album_name", it) }
                }
            )
            val best = search
                ?.jsonObjects()
                ?.filter { it.matchesSong(song) }
                ?.maxByOrNull { it.matchScore(song) }

            best?.toLyricsResultIfUsable(song, name)
        }
    }

    private inner class YouTubeCaptionLyricsProvider : LyricsProvider {
        override val name = "YouTube captions"

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
                LyricsParser.toResult(transcript, source = name)
            }
        }
    }

    private fun fetchObject(endpoint: String, params: Map<String, String>): JSONObject? {
        val connection = openConnection(endpoint, params) ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchArray(endpoint: String, params: Map<String, String>): JSONArray? {
        val connection = openConnection(endpoint, params) ?: return null
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
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
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "FridaMusic/1.0 (https://github.com/jagrdev-MX)")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.toLyricsResultIfUsable(song: Song, source: String): LyricsResult? {
        if (!matchesSong(song)) return null
        val synced = optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val raw = synced ?: plain ?: return null
        return LyricsParser.toResult(raw, source = source)
    }

    private fun JSONObject.matchesSong(song: Song): Boolean {
        val titleOk = optString("trackName").normalizedLyricsKey()
            .let { candidate -> candidate == song.title.normalizedLyricsKey() || candidate.contains(song.title.normalizedLyricsKey()) || song.title.normalizedLyricsKey().contains(candidate) }
        val artistOk = optString("artistName").normalizedLyricsKey()
            .let { candidate -> candidate.isBlank() || candidate == song.artist.normalizedLyricsKey() || candidate.contains(song.artist.normalizedLyricsKey()) || song.artist.normalizedLyricsKey().contains(candidate) }
        val duration = optLong("duration", 0L)
        val durationOk = song.duration <= 0L || duration <= 0L || abs(duration - (song.duration / 1000L)) <= DURATION_TOLERANCE_SECONDS
        return titleOk && artistOk && durationOk
    }

    private fun JSONObject.matchScore(song: Song): Int {
        var score = 0
        if (optString("trackName").normalizedLyricsKey() == song.title.normalizedLyricsKey()) score += 60
        if (optString("artistName").normalizedLyricsKey() == song.artist.normalizedLyricsKey()) score += 35
        if (optString("albumName").normalizedLyricsKey() == song.album.normalizedLyricsKey()) score += 15
        val duration = optLong("duration", 0L)
        if (song.duration > 0 && duration > 0) score += (20 - abs(duration - song.duration / 1000L).toInt()).coerceAtLeast(0)
        if (optString("syncedLyrics").isNotBlank()) score += 8
        return score
    }

    private fun Song.lyricsCacheKey(): String =
        "${title.normalizedLyricsKey()}|${artist.normalizedLyricsKey()}|${album.normalizedLyricsKey()}|${duration / 1000L}"

    private fun Song.localFileOrNull(): File? {
        val path = data.takeIf { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) } ?: return null
        return File(path).takeIf { it.exists() && it.isFile }
    }

    private fun Song.youtubeVideoIdOrNull(): String? {
        val uriText = uri.toString()
        val fromUri = runCatching {
            val parsed = Uri.parse(uriText)
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

    private fun String.normalizedLyricsKey(): String {
        val noMarks = Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noMarks
            .lowercase()
            .replace(Regex("\\b(remix|live|official video|official audio|official|audio|lyrics|lyric video|slowed|reverb|sped up|speed up|nightcore|bass boosted|ft|feat)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val NEGATIVE_CACHE_TTL_MS = 15 * 60 * 1000L
        private const val DURATION_TOLERANCE_SECONDS = 8L
        private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        private val COMMENT_KEYS = listOf("SYNCEDLYRICS", "UNSYNCEDLYRICS", "LYRICS")
    }
}
