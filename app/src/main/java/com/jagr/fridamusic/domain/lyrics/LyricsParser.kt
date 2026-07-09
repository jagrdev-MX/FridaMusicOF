package com.jagr.fridamusic.domain.lyrics

enum class LyricsSyncState {
    LOADING,
    SYNCED,
    UNSYNCED,
    NOT_AVAILABLE,
    ERROR
}

sealed interface LyricsResult {
    val syncState: LyricsSyncState

    data object Loading : LyricsResult {
        override val syncState = LyricsSyncState.LOADING
    }

    data class Available(
        val lines: List<LyricsLine>,
        val plainText: String?,
        val source: String,
        override val syncState: LyricsSyncState,
        val offsetMs: Long = 0L
    ) : LyricsResult

    data object NotAvailable : LyricsResult {
        override val syncState = LyricsSyncState.NOT_AVAILABLE
    }

    data class Error(val message: String? = null) : LyricsResult {
        override val syncState = LyricsSyncState.ERROR
    }
}

data class LyricsLine(
    val startTime: Long,
    val endTime: Long = 0,
    val content: String,
    val words: List<LyricsWord> = emptyList()
)

data class LyricsWord(
    val startTime: Long,
    val endTime: Long,
    val content: String
)

object LyricsParser {
    fun parseLrc(lrc: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        lrc.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val ms = match.groupValues[3].toLong()
                val content = match.groupValues[4].trim()
                
                val startTime = (min * 60 * 1000) + (sec * 1000) + (if (match.groupValues[3].length == 2) ms * 10 else ms)
                lines.add(LyricsLine(startTime = startTime, content = content))
            }
        }
        
        // Calculate end times based on next line start time
        for (i in 0 until lines.size - 1) {
            lines[i] = lines[i].copy(endTime = lines[i+1].startTime)
        }
        
        return lines.sortedBy { it.startTime }
    }

    fun parseWebVtt(vtt: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val cueTimeRegex = Regex("""(\d{1,2}:)?(\d{2}):(\d{2})\.(\d{3})\s+-->\s+(\d{1,2}:)?(\d{2}):(\d{2})\.(\d{3})""")
        val blocks = vtt
            .replace("\r\n", "\n")
            .split(Regex("\n{2,}"))

        blocks.forEach { block ->
            val blockLines = block.lines().filter { it.isNotBlank() && !it.startsWith("WEBVTT") }
            val timeLineIndex = blockLines.indexOfFirst { cueTimeRegex.containsMatchIn(it) }
            if (timeLineIndex == -1) return@forEach

            val match = cueTimeRegex.find(blockLines[timeLineIndex]) ?: return@forEach
            val text = blockLines
                .drop(timeLineIndex + 1)
                .joinToString(" ")
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (text.isNotBlank()) {
                lines.add(
                    LyricsLine(
                        startTime = match.webVttTimeMs(startHourGroup = 1, startMinuteGroup = 2, startSecondGroup = 3, startMsGroup = 4),
                        endTime = match.webVttTimeMs(startHourGroup = 5, startMinuteGroup = 6, startSecondGroup = 7, startMsGroup = 8),
                        content = text
                    )
                )
            }
        }

        return lines.sortedBy { it.startTime }
    }

    fun parseYouTubeTimedText(xml: String): List<LyricsLine> {
        val regex = Regex("""<text[^>]*start="([^"]+)"[^>]*(?:dur="([^"]+)")?[^>]*>(.*?)</text>""", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(xml)
            .mapNotNull { match ->
                val startMs = match.groupValues[1].toDoubleOrNull()?.times(1000.0)?.toLong() ?: return@mapNotNull null
                val durationMs = match.groupValues[2].toDoubleOrNull()?.times(1000.0)?.toLong() ?: 0L
                val content = match.groupValues[3]
                    .replace(Regex("<[^>]+>"), "")
                    .decodeXmlEntities()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (content.isBlank()) {
                    null
                } else {
                    LyricsLine(
                        startTime = startMs,
                        endTime = if (durationMs > 0L) startMs + durationMs else 0L,
                        content = content
                    )
                }
            }
            .toList()
            .sortedBy { it.startTime }
    }

    fun toResult(rawLyrics: String, source: String): LyricsResult {
        val syncedLines = parseLrc(rawLyrics)
        return if (syncedLines.isNotEmpty()) {
            LyricsResult.Available(
                lines = syncedLines,
                plainText = rawLyrics,
                source = source,
                syncState = LyricsSyncState.SYNCED
            )
        } else {
            LyricsResult.Available(
                lines = emptyList(),
                plainText = rawLyrics.trim().takeIf { it.isNotBlank() },
                source = source,
                syncState = LyricsSyncState.UNSYNCED
            )
        }
    }

    private fun MatchResult.webVttTimeMs(
        startHourGroup: Int,
        startMinuteGroup: Int,
        startSecondGroup: Int,
        startMsGroup: Int
    ): Long {
        val hour = groupValues[startHourGroup].trimEnd(':').toLongOrNull() ?: 0L
        val minute = groupValues[startMinuteGroup].toLongOrNull() ?: 0L
        val second = groupValues[startSecondGroup].toLongOrNull() ?: 0L
        val millis = groupValues[startMsGroup].toLongOrNull() ?: 0L
        return hour * 3_600_000L + minute * 60_000L + second * 1_000L + millis
    }

    private fun String.decodeXmlEntities(): String =
        replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
}
