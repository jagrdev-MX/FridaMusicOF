package com.jagr.fridamusic.data.remote.innertube

import android.util.Log
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
    private const val SEARCH_CACHE_TTL_MS = 30 * 60 * 1000L
    private const val SEARCH_CACHE_LIMIT = 100

    private const val SUGGESTION_CACHE_LIMIT = 100
    private val suggestionCache = ConcurrentHashMap<String, List<String>>()

    private data class CachedSearch(
        val results: List<YouTubeResult>,
        val cachedAtMs: Long
    )

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

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
        suggestionCache.clear()
    }

    suspend fun getSuggestions(query: String): List<String> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return emptyList()
        
        suggestionCache[trimmed]?.let { return it }

        return try {
            val url = "https://suggestqueries.google.com/complete/search"
            val response = client.get(url) {
                parameter("q", query)
                parameter("client", "youtube")
                parameter("ds", "yt")
                parameter("hl", "es")
            }.body<String>()
            
            // Basic extraction from JSONP-like format: ["query", ["s1", "s2", ...]]
            val jsonArray = Json.parseToJsonElement(response.substringAfter("(").substringBeforeLast(")")).jsonArray
            val suggestionsArray = jsonArray.getOrNull(1)?.jsonArray ?: return emptyList()
            val results = suggestionsArray.map { it.jsonArray[0].jsonPrimitive.content }
            
            if (results.isNotEmpty()) {
                if (suggestionCache.size >= SUGGESTION_CACHE_LIMIT) suggestionCache.clear()
                suggestionCache[trimmed] = results
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun searchUncached(query: String): List<YouTubeResult> {
        val totalStart = System.currentTimeMillis()
        // High-Speed Direct API attempt (InnerTube Music)
        try {
            Log.d("YouTubeSearch", "Fast API search attempt: $query")
            val networkStart = System.currentTimeMillis()
            val response = client.post("https://music.youtube.com/youtubei/v1/search") {
                setBody(buildSearchPayload(query))
                header("Content-Type", "application/json")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                header("X-YouTube-Client-Name", "67")
                header("X-YouTube-Client-Version", "1.20240617.01.00")
            }.body<JsonObject>()
            
            val networkTime = System.currentTimeMillis() - networkStart
            Log.d("SearchPerf", "[2] HTTP Call (InnerTube): ${networkTime}ms")

            val parseStart = System.currentTimeMillis()
            val results = parseInnerTubeMusicResults(response)
            val parseTime = System.currentTimeMillis() - parseStart
            Log.d("SearchPerf", "[3] JSON Deserialization + Mapping: ${parseTime}ms")

            if (results.isNotEmpty()) {
                Log.d("YouTubeSearch", "Fast API success")
                return results
            }
        } catch (e: Exception) {
            Log.w("YouTubeSearch", "Fast API failed, falling back to scraping: ${e.message}")
        }

        // Reliable Fallback (Scraping)
        return try {
            Log.d("YouTubeSearch", "Scraping search attempt: $query")
            val scrapingNetworkStart = System.currentTimeMillis()
            val html = client.get("https://www.youtube.com/results") {
                parameter("search_query", query)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }.body<String>()
            
            val scrapingNetworkTime = System.currentTimeMillis() - scrapingNetworkStart
            Log.d("SearchPerf", "[2-FB] HTTP Call (Scraping): ${scrapingNetworkTime}ms")

            val scrapingParseStart = System.currentTimeMillis()
            val jsonString = html.substringAfter("var ytInitialData = ").substringBefore(";</script>")
            val results = parseHTMLResults(Json.parseToJsonElement(jsonString).jsonObject)
            val scrapingParseTime = System.currentTimeMillis() - scrapingParseStart
            Log.d("SearchPerf", "[3-FB] Scraping Parsing: ${scrapingParseTime}ms")

            Log.d("YouTubeSearch", "Scraping found ${results.size} results")
            results
        } catch (e: Exception) {
            Log.e("YouTubeSearch", "All search methods failed", e)
            emptyList()
        }
    }

    private fun buildSearchPayload(query: String) = buildJsonObject {
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20240617.01.00")
                put("hl", "es")
            }
        }
        put("query", query)
    }

    private fun parseInnerTubeMusicResults(response: JsonObject): List<YouTubeResult> {
        val results = mutableListOf<YouTubeResult>()
        try {
            val sections = response["contents"]?.jsonObject
                ?.get("tabbedSearchResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray?.get(0)?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: return emptyList()

            for (section in sections) {
                val musicShelf = section.jsonObject["musicShelfRenderer"]?.jsonObject ?: continue
                val items = musicShelf["contents"]?.jsonArray ?: continue
                for (item in items) {
                    val renderer = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                    val videoId = renderer["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content ?: continue
                    val flex = renderer["flexColumns"]?.jsonArray
                    val title = flex?.getOrNull(0)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                    val artist = flex?.getOrNull(1)?.jsonObject?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                    val thumb = renderer["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
                    results.add(YouTubeResult(videoId, title, artist, thumb))
                }
            }
        } catch (e: Exception) { }
        return results
    }

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
                        if (videoRenderer != null) {
                            val videoId = videoRenderer["videoId"]?.jsonPrimitive?.content ?: continue
                            val title = videoRenderer["title"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                            val artist = videoRenderer["ownerText"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                            val thumbnail = videoRenderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
                            val durationMs = videoRenderer["lengthText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.content?.let(::durationTextToMillis) ?: 0L
                            
                            results.add(YouTubeResult(videoId, title, artist, thumbnail, ResultType.SONG, durationMs))
                        }
                    }
                }
            }
        } catch (e: Exception) { 
            Log.e("YouTubeSearch", "Error parsing HTML results: ${e.message}")
        }
        return results
    }

    private fun normalizeSearchKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase().replace(Regex("\\s+"), " ").trim()

    private fun durationTextToMillis(duration: String): Long {
        val parts = duration.split(":").mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0L
        return parts.fold(0L) { total, part -> total * 60L + part } * 1000L
    }

    suspend fun getTranscript(videoId: String): String? = null
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
