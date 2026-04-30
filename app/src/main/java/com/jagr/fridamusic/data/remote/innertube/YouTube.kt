package com.jagr.fridamusic.data.remote.innertube

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.util.*

import io.ktor.client.engine.cio.*

object YouTube {
    private val client = HttpClient(CIO) {
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

    suspend fun search(query: String): List<YouTubeResult> {
        for (baseUrl in PIPED_INSTANCES) {
            try {
                val url = "$baseUrl/search"
                val response: JsonObject = client.get(url) {
                    parameter("q", query)
                    parameter("filter", "music_songs") // Only YouTube Music tracks
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                }.body()
                
                val results = parsePipedResults(response)
                if (results.isNotEmpty()) {
                    return results // Success!
                }
            } catch (e: Exception) {
                // If this instance fails, silently try the next one
                continue
            }
        }
        // PLAN C: HTML Scraper Fallback
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
                        val renderer = item.jsonObject["videoRenderer"]?.jsonObject ?: continue
                        val videoId = renderer["videoId"]?.jsonPrimitive?.content ?: continue
                        
                        val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                            
                        val artist = renderer["ownerText"]?.jsonObject?.get("runs")?.jsonArray?.getOrNull(0)?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: "Unknown"

                        val thumbnail = renderer["thumbnail"]?.jsonObject
                            ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
                            ?.get("url")?.jsonPrimitive?.content ?: ""

                        results.add(YouTubeResult(videoId, title, artist, thumbnail))
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
                
                // If it has a watch URL, it's a playable audio/video track
                if (url.contains("/watch?v=")) { 
                    val videoId = url.replace("/watch?v=", "").substringBefore("&")
                    val title = obj["title"]?.jsonPrimitive?.content ?: "Unknown Title"
                    val artist = obj["uploaderName"]?.jsonPrimitive?.content ?: "Unknown Artist"
                    val thumbnail = obj["thumbnail"]?.jsonPrimitive?.content ?: ""

                    results.add(YouTubeResult(videoId, title, artist, thumbnail))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun emptyJsonArray() = JsonArray(emptyList())

    suspend fun getTranscript(videoId: String): String? {
        // Here we would call the InnerTube transcript endpoint
        // For now, returning null to avoid crashes until we have the full model
        return null
    }
}

data class YouTubeResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String = ""
)
