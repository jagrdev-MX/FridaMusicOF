package com.jagr.fridamusic.domain.lyrics

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
}
