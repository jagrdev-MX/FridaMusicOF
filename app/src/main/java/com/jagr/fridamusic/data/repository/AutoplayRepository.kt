package com.jagr.fridamusic.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.data.remote.innertube.YouTube
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.data.remote.innertube.ResultType
import com.jagr.fridamusic.domain.model.PlaybackQueueState
import com.jagr.fridamusic.domain.model.QueueItem
import com.jagr.fridamusic.domain.model.QueueSource
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.domain.recommendation.AutoplayDiversity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlin.math.min

data class SongRecommendationProfile(
    val identityKey: String,
    val metadataKey: String,
    val titleBase: String,
    val variants: Set<String>,
    val styles: Set<String>,
    val tokens: Set<String>,
    val artist: String,
    val primaryArtist: String,
    val album: String
)

data class RecommendationContext(
    val tokens: Set<String>,
    val styles: Set<String>,
    val artists: Set<String>
)

data class RecommendationCandidate(
    val song: Song,
    val profile: SongRecommendationProfile,
    val score: Int,
    val reason: String
)

class AutoplayRepository(private val context: Context) {
    private val recommendationProfileCache = ConcurrentHashMap<String, SongRecommendationProfile>()
    private val autoplayRemoteSearchCache = ConcurrentHashMap<String, List<YouTubeResult>>()
    private val autoplayRecommendationCache = ConcurrentHashMap<String, List<QueueItem>>()

    private val AUTOPLAY_TARGET_SIZE = 12
    private val AUTOPLAY_FAST_READY_SIZE = 8
    private val AUTOPLAY_REMOTE_QUERY_LIMIT = 2
    private val AUTOPLAY_LOG_TAG = "FridaAutoplay"
    private val YOUTUBE_VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")

    fun getSongProfile(song: Song): SongRecommendationProfile {
        val key = songIdentityKey(song)
        return recommendationProfileCache.getOrPut(key) {
            val titleBase = AutoplayDiversity.baseTitleKey(song.title)
            val primaryArtist = primaryArtistName(song.artist) ?: song.artist
            SongRecommendationProfile(
                identityKey = key,
                metadataKey = metadataIdentity(song.title, song.artist),
                titleBase = titleBase,
                variants = AutoplayDiversity.variantTokens(song.title),
                styles = AutoplayDiversity.styleTokens("${song.title} ${song.artist} ${song.album}"),
                tokens = recommendationTokens(song),
                artist = song.artist,
                primaryArtist = primaryArtist,
                album = song.album
            )
        }
    }

    suspend fun generateRecommendations(
        seed: Song,
        anchorSeed: Song,
        state: PlaybackQueueState,
        allSongs: List<Song>,
        fullHistory: List<PlaybackHistoryEntity>,
        allowRemote: Boolean
    ): List<QueueItem> = withContext(Dispatchers.IO) {
        val seedProfile = getSongProfile(anchorSeed)
        val currentProfile = getSongProfile(seed)
        val immediateUpNext = state.upNext.take(AUTOPLAY_FAST_READY_SIZE)
        val excludedContext = state.previous.takeLast(20) + listOfNotNull(state.current) + immediateUpNext + state.autoplay
        val activeBaseContext = state.previous.takeLast(8) + immediateUpNext + state.autoplay
        
        val excluded = excludedContext
            .map { songIdentityKey(it.song) }
            .toMutableSet()
            
        val context = getRecommendationContext(state, anchorSeed)
        val reservedAutoplaySongs = emptyList<Song>() 
        val autoplayHistory = fullHistory.take(300)
        
        val historyCounts = autoplayHistory.groupingBy { metadataIdentity(it.title, it.artist) }.eachCount()
        val recentMetadataKeys = autoplayHistory.take(12).map { metadataIdentity(it.title, it.artist) }.toSet()
        val recentBaseCounts = autoplayHistory
            .take(24)
            .map { AutoplayDiversity.baseTitleKey(it.title) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            
        val activeBaseCounts = activeBaseContext
            .map { getSongProfile(it.song).titleBase }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            
        val candidates = LinkedHashMap<String, RecommendationCandidate>()

        fun isSeedProfile(profile: SongRecommendationProfile): Boolean =
            profile.identityKey == seedProfile.identityKey ||
                    profile.metadataKey == seedProfile.metadataKey ||
                    profile.identityKey == currentProfile.identityKey ||
                    profile.metadataKey == currentProfile.metadataKey

        fun isContextMatch(profile: SongRecommendationProfile): Boolean {
            if (profile.styles.intersect(seedProfile.styles).isNotEmpty()) return true
            if (profile.styles.intersect(context.styles).isNotEmpty()) return true
            if (profile.primaryArtist.isNotBlank() && profile.primaryArtist == seedProfile.primaryArtist) return true
            if (profile.primaryArtist.isNotBlank() && profile.primaryArtist in context.artists) return true
            if (profile.tokens.intersect(seedProfile.tokens).isNotEmpty()) return true
            if (profile.tokens.intersect(context.tokens).size >= 2) return true
            if (profile.metadataKey in historyCounts) return true
            return false
        }

        fun addCandidate(song: Song, baseScore: Int, reason: String? = null, requireContextMatch: Boolean = false) {
            val profile = getSongProfile(song)
            val key = profile.identityKey
            if (key in excluded || isSeedProfile(profile)) return
            if (!AutoplayDiversity.isAutoplayTrackCandidate(song)) return
            
            if (requireContextMatch && !isContextMatch(profile)) return
            
            val score = recommendationScore(
                seed = seedProfile,
                candidate = profile,
                historyCounts = historyCounts,
                recentMetadataKeys = recentMetadataKeys,
                recentBaseCounts = recentBaseCounts,
                activeBaseCounts = activeBaseCounts,
                context = context
            ) + baseScore
            
            Log.d(AUTOPLAY_LOG_TAG, "Candidate: ${song.title} - ${song.artist}, Score: $score")
            
            if (score < 15) return // Lowered from 22
            
            val existing = candidates[key]
            if (existing == null || score > existing.score) {
                candidates[key] = RecommendationCandidate(
                    song = song,
                    profile = profile,
                    score = score,
                    reason = reason ?: recommendationReason(anchorSeed, song)
                )
            }
        }

        allSongs.forEach { addCandidate(it, 10, reason = "From your library") }

        if (allowRemote) {
            val queries = getRecommendationQueries(anchorSeed, context)
            queries.take(AUTOPLAY_REMOTE_QUERY_LIMIT).forEach { query ->
                Log.d(AUTOPLAY_LOG_TAG, "Searching remote: $query")
                val results = getCachedRemoteSearch(query)
                Log.d(AUTOPLAY_LOG_TAG, "Remote results count: ${results.size}")
                results.filter { it.type == ResultType.SONG }.forEachIndexed { index, result ->
                    addCandidate(resultToSong(result), (40 - index).coerceAtLeast(15)) // Increased base score
                }
            }
        }

        val selected = selectDiverseRecommendations(candidates.values.toList(), anchorSeed, reservedAutoplaySongs)
        Log.d(AUTOPLAY_LOG_TAG, "Selected recommendations: ${selected.size}")
        selected.map { QueueItem(it.song, QueueSource.AUTOPLAY, reason = it.reason) }
    }

    private fun recommendationScore(
        seed: SongRecommendationProfile,
        candidate: SongRecommendationProfile,
        historyCounts: Map<String, Int>,
        recentMetadataKeys: Set<String>,
        recentBaseCounts: Map<String, Int>,
        activeBaseCounts: Map<String, Int>,
        context: RecommendationContext
    ): Int {
        var score = 0
        if (candidate.primaryArtist == seed.primaryArtist) score += 20 // Reduced from 25
        if (candidate.styles.intersect(seed.styles).isNotEmpty()) score += 15
        if (candidate.tokens.intersect(seed.tokens).isNotEmpty()) score += 10
        
        score += (historyCounts[candidate.metadataKey] ?: 0) * 3 // Increased weight
        if (recentMetadataKeys.contains(candidate.metadataKey)) score -= 10 // Reduced penalty
        
        val baseCount = activeBaseCounts[candidate.titleBase] ?: 0
        score -= baseCount * 25
        
        return score
    }

    private fun recommendationReason(seed: Song, candidate: Song): String {
        return if (seed.artist == candidate.artist) "Más de ${seed.artist}" else "Similar a ${seed.title}"
    }

    private fun getRecommendationQueries(song: Song, context: RecommendationContext): List<String> {
        val primaryArtist = primaryArtistName(song.artist) ?: song.artist
        return listOf(
            "$primaryArtist radio",
            "${song.title} ${song.artist} audio"
        )
    }

    private suspend fun getCachedRemoteSearch(query: String): List<YouTubeResult> {
        return autoplayRemoteSearchCache.getOrPut(query) {
            try {
                YouTube.search(query)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun resultToSong(result: YouTubeResult): Song {
        return Song(
            id = result.videoId.hashCode().toLong(),
            title = result.title,
            artist = result.artist,
            data = result.videoId,
            duration = 0,
            albumId = 0,
            uri = Uri.parse("https://www.youtube.com/watch?v=${result.videoId}"),
            artworkUri = Uri.parse(result.thumbnailUrl),
            album = "YouTube"
        )
    }

    private fun selectDiverseRecommendations(
        candidates: List<RecommendationCandidate>,
        anchor: Song,
        reserved: List<Song>
    ): List<RecommendationCandidate> {
        return candidates.sortedByDescending { it.score }.take(10)
    }

    private fun getRecommendationContext(state: PlaybackQueueState, anchor: Song): RecommendationContext {
        val profile = getSongProfile(anchor)
        return RecommendationContext(
            tokens = profile.tokens,
            styles = profile.styles,
            artists = setOf(profile.primaryArtist)
        )
    }

    private fun recommendationTokens(song: Song): Set<String> {
        val tokens = mutableSetOf<String>()
        tokens.addAll(AutoplayDiversity.baseTitleKey(song.title).split(" ").filter { it.length >= 3 })
        tokens.addAll(AutoplayDiversity.styleTokens("${song.title} ${song.artist} ${song.album}"))
        return tokens
    }

    private fun primaryArtistName(artist: String): String? {
        return artist.split(Regex("[,&x]|\\b(with|feat|ft)\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun songIdentityKey(song: Song): String {
        return if (song.uri.toString().startsWith("http")) song.data else song.id.toString()
    }

    private fun metadataIdentity(title: String, artist: String): String =
        "${title.lowercase()}|${artist.lowercase()}"

    fun clearCache() {
        recommendationProfileCache.clear()
        autoplayRemoteSearchCache.clear()
        autoplayRecommendationCache.clear()
    }
}
