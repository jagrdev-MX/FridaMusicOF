package com.jagr.fridamusic.data.remote.innertube

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

import io.ktor.client.engine.cio.*

object YouTube {
    private const val SEARCH_CACHE_TTL_MS = 5 * 60 * 1000L
    private const val SEARCH_CACHE_LIMIT = 25

    private data class CachedSearch(
        val results: List<YouTubeResult>,
        val cachedAtMs: Long
    )

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 3_000
            connectTimeoutMillis = 3_000
            socketTimeoutMillis = 3_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private val PIPED_INSTANCES = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.syncapp.store",
        "https://api.piped.yt",
        "https://piped-api.lunar.icu"
    )

    private val searchCache = ConcurrentHashMap<String, CachedSearch>()
    private val searchesInFlight = ConcurrentHashMap<String, CompletableDeferred<List<YouTubeResult>>>()

    suspend fun search(query: String): List<YouTubeResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()
        val cacheKey = normalizeSearchKey(trimmedQuery)
        val now = System.currentTimeMillis()
        searchCache[cacheKey]
            ?.takeIf { now - it.cachedAtMs <= SEARCH_CACHE_TTL_MS }
            ?.let { return it.results }

        val pending = CompletableDeferred<List<YouTubeResult>>()
        val existing = searchesInFlight.putIfAbsent(cacheKey, pending)
        if (existing != null) return existing.await()

        return try {
            val results = searchUncached(trimmedQuery)
            if (results.isNotEmpty()) {
                if (searchCache.size >= SEARCH_CACHE_LIMIT) searchCache.clear()
                searchCache[cacheKey] = CachedSearch(results, System.currentTimeMillis())
            }
            pending.complete(results)
            results
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            searchesInFlight.remove(cacheKey, pending)
        }
    }

    fun clearCache() {
        searchCache.clear()
        searchesInFlight.clear()
    }

    private suspend fun searchUncached(query: String): List<YouTubeResult> {
        for (baseUrl in PIPED_INSTANCES) {
            try {
                val url = "$baseUrl/search"
                val response: JsonObject = client.get(url) {
                    parameter("q", query)
                    parameter("filter", "all")
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                }.body()

                val results = parsePipedResults(response)
                if (results.isNotEmpty()) {
                    return results // Success!
                }
            } catch (e: Exception) {
                continue
            }
        }
        return try {
            val htmlUrl = "https://www.youtube.com/results"
            val html = client.get(htmlUrl) {
                parameter("search_query", query)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "en-US,en;q=0.9")
            }.body<String>()

            val jsonString = html.substringAfter("var ytInitialData = ").substringBefore(";</script>")
            val response = Json.parseToJsonElement(jsonString).jsonObject

            parseHTMLResults(response)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun normalizeSearchKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun parseHTMLResults(response: JsonObject): List<YouTubeResult> {
        val results = mutableListOf<YouTubeResult>()
        try {
            val contents = response["contents"]?.jsonObject
                ?.get("twoColumnSearchResultsRenderer")?.jsonObject
                ?.get("primaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return emptyList()

            for (section in contents) {
                val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
                if (itemSection != null) {
                    val itemContents = itemSection["contents"]?.jsonArray ?: continue

                    for (item in itemContents) {
                        val videoRenderer = item.jsonObject["videoRenderer"]?.jsonObject
                        val channelRenderer = item.jsonObject["channelRenderer"]?.jsonObject
                        val playlistRenderer = item.jsonObject["playlistRenderer"]?.jsonObject

                        if (videoRenderer != null) {
                            val videoId = videoRenderer["videoId"]?.jsonPrimitive?.content ?: continue
                            val title = videoRenderer["title"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                            val artist = videoRenderer["ownerText"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                            val thumbnail = videoRenderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
                            val durationMs = videoRenderer["lengthText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content
                                ?.let(::durationTextToMillis)
                                ?: 0L

                            results.add(YouTubeResult(videoId, title, artist, thumbnail, ResultType.SONG, durationMs))

                        } else if (channelRenderer != null) {
                            val channelId = channelRenderer["channelId"]?.jsonPrimitive?.content ?: continue
                            val title = channelRenderer["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content ?: "Unknown"
                            val thumbnail = channelRenderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""

                            results.add(YouTubeResult(channelId, title, "Artista", thumbnail, ResultType.ARTIST))

                        } else if (playlistRenderer != null) {
                            val playlistId = playlistRenderer["playlistId"]?.jsonPrimitive?.content ?: continue
                            val title = playlistRenderer["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content ?: "Unknown"
                            val uploader = playlistRenderer["longBylineText"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "YouTube"

                            val thumbnailsArray = playlistRenderer["thumbnails"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("thumbnails")?.jsonArray
                                ?: playlistRenderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray

                            val thumbnail = thumbnailsArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""

                            results.add(YouTubeResult(playlistId, title, uploader, thumbnail, ResultType.PLAYLIST))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun parsePipedResults(response: JsonObject): List<YouTubeResult> {
        val results = mutableListOf<YouTubeResult>()
        try {
            val items = response["items"]?.jsonArray ?: return emptyList()

            for (item in items) {
                val obj = item.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content ?: ""
                val type = obj["type"]?.jsonPrimitive?.content ?: ""

                when (type) {
                    "stream" -> {
                        if (url.contains("/watch?v=")) {
                            val videoId = url.replace("/watch?v=", "").substringBefore("&")
                            val title = obj["title"]?.jsonPrimitive?.content ?: "Unknown Title"
                            val artist = obj["uploaderName"]?.jsonPrimitive?.content ?: "Unknown Artist"
                            val thumbnail = obj["thumbnail"]?.jsonPrimitive?.content ?: ""
                            val durationMs = obj["duration"]?.jsonPrimitive?.longOrNull?.times(1000L) ?: 0L

                            results.add(YouTubeResult(videoId, title, artist, thumbnail, ResultType.SONG, durationMs))
                        }
                    }
                    "channel" -> {
                        val channelId = url.replace("/channel/", "").substringBefore("?")
                        val title = obj["name"]?.jsonPrimitive?.content ?: "Unknown Artist"
                        val thumbnail = obj["thumbnail"]?.jsonPrimitive?.content ?: ""

                        results.add(YouTubeResult(channelId, title, "Artista", thumbnail, ResultType.ARTIST))
                    }
                    "playlist" -> {
                        val playlistId = url.replace("/playlist?list=", "").substringBefore("&")
                        val title = obj["title"]?.jsonPrimitive?.content ?: "Unknown Playlist"
                        val uploader = obj["uploaderName"]?.jsonPrimitive?.content ?: "YouTube"
                        val thumbnail = obj["thumbnail"]?.jsonPrimitive?.content ?: ""

                        results.add(YouTubeResult(playlistId, title, uploader, thumbnail, ResultType.PLAYLIST))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun emptyJsonArray() = JsonArray(emptyList())

    private fun durationTextToMillis(duration: String): Long {
        val parts = duration.split(":").mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0L
        return parts.fold(0L) { total, part -> total * 60L + part } * 1000L
    }

    suspend fun getTranscript(videoId: String): String? {
        return null
    }
}

enum class ResultType { SONG, ARTIST, PLAYLIST, ALBUM }

data class YouTubeResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String = "",
    val type: ResultType = ResultType.SONG,
    val durationMs: Long = 0L
)
