package com.jagr.fridamusic.domain.model

enum class QueueSource {
    LIBRARY,
    PLAYLIST,
    SEARCH,
    ARTIST,
    HISTORY,
    USER,
    AUTOPLAY,
    RESTORED
}

data class QueueItem(
    val song: Song,
    val source: QueueSource,
    val reason: String? = null,
    val userInserted: Boolean = false
)

data class PlaybackQueueState(
    val current: QueueItem? = null,
    val previous: List<QueueItem> = emptyList(),
    val upNext: List<QueueItem> = emptyList(),
    val autoplay: List<QueueItem> = emptyList(),
    val source: QueueSource = QueueSource.LIBRARY,
    val sourceName: String? = null,
    val shuffleSnapshot: List<QueueItem> = emptyList(),
    val isAutoplayLoading: Boolean = false,
    val autoplayError: String? = null
) {
    val hasPlayableNext: Boolean
        get() = upNext.isNotEmpty() || autoplay.isNotEmpty()
}

data class SleepTimerState(
    val minutes: Int = 0,
    val endOfSong: Boolean = false
)
