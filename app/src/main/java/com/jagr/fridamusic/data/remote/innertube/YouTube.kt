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

                            results.add(YouTubeResult(videoId, title, artist, thumbnail, ResultType.SONG))

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

                            results.add(YouTubeResult(videoId, title, artist, thumbnail, ResultType.SONG))
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
    val type: ResultType = ResultType.SONG
)