package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jagr.fridamusic.domain.model.PlaybackQueueState
import com.jagr.fridamusic.domain.model.QueueItem
import com.jagr.fridamusic.domain.model.QueueSource
import com.jagr.fridamusic.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    private val _queueState = MutableStateFlow(PlaybackQueueState())
    val queueState = _queueState.asStateFlow()

    fun clearManualQueue() {
        _queueState.value = _queueState.value.copy(
            upNext = emptyList(),
            autoplay = emptyList(),
            autoplayError = null,
            isAutoplayLoading = false
        )
    }

    fun addToQueue(song: Song) {
        val state = _queueState.value
        _queueState.value = state.copy(
            upNext = state.upNext + QueueItem(song, QueueSource.USER)
        )
    }

    fun addNext(song: Song) {
        val state = _queueState.value
        _queueState.value = state.copy(
            upNext = listOf(QueueItem(song, QueueSource.USER)) + state.upNext
        )
    }

    fun removeFromQueue(index: Int) {
        val state = _queueState.value
        if (index in state.upNext.indices) {
            _queueState.value = state.copy(
                upNext = state.upNext.filterIndexed { i, _ -> i != index }
            )
        }
    }
}
