package com.kidswatch.tv.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.kidswatch.tv.data.FirebaseManager
import com.kidswatch.tv.data.PlaylistMeta
import com.kidswatch.tv.data.PlaylistRepository
import com.kidswatch.tv.data.PlaylistResult
import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.events.PlayEventRecorder
import com.kidswatch.tv.data.models.VideoItem
import com.kidswatch.tv.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlaylistRow(
    val meta: PlaylistMeta,
    val videos: List<VideoItem>,
    val isOffline: Boolean,
    val error: String?,
    val isLoading: Boolean,
)

data class HomeUiState(
    val rows: List<PlaylistRow> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = CacheDatabase.getInstance(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var playlistListener: ListenerRegistration? = null
    private var currentPlaylists: List<PlaylistMeta> = emptyList()
    var familyId: String? = null
        private set

    fun start(familyId: String) {
        this.familyId = familyId
        PlayEventRecorder.init(db, familyId)

        playlistListener?.remove()
        playlistListener = FirebaseManager.listenPlaylists(familyId) { playlists ->
            currentPlaylists = playlists
            if (playlists.isEmpty()) {
                _uiState.value = HomeUiState(isEmpty = true, isLoading = false)
            } else {
                resolveAll(playlists)
            }
        }
    }

    fun refresh() {
        if (currentPlaylists.isNotEmpty()) {
            resolveAll(currentPlaylists)
        }
    }

    private fun resolveAll(playlists: List<PlaylistMeta>) {
        viewModelScope.launch {
            // Show loading with cached data first
            val cachedRows = playlists.map { meta ->
                val cached = PlaylistRepository.getCachedVideos(db, meta.youtubePlaylistId)
                PlaylistRow(meta, cached, isOffline = false, error = null, isLoading = true)
            }
            _uiState.value = HomeUiState(rows = cachedRows, isLoading = true)

            // Resolve fresh
            val results = PlaylistRepository.resolveAllPlaylists(playlists, db)

            val rows = playlists.map { meta ->
                when (val result = results[meta.youtubePlaylistId]) {
                    is PlaylistResult.Success -> PlaylistRow(
                        meta, result.videos, isOffline = false, error = null, isLoading = false
                    )
                    is PlaylistResult.CachedFallback -> PlaylistRow(
                        meta, result.videos, isOffline = true, error = null, isLoading = false
                    )
                    is PlaylistResult.Error -> PlaylistRow(
                        meta, emptyList(), isOffline = false, error = result.message, isLoading = false
                    )
                    null -> PlaylistRow(
                        meta, emptyList(), isOffline = false, error = "Not resolved", isLoading = false
                    )
                }
            }
            _uiState.value = HomeUiState(rows = rows, isLoading = false)
            AppLogger.success("All playlists resolved")
        }
    }

    override fun onCleared() {
        playlistListener?.remove()
    }
}
