package com.kidswatch.tv.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.data.PlaylistRepository
import com.kidswatch.tv.data.PlaylistResult
import com.kidswatch.tv.data.cache.PlaylistEntity
import com.kidswatch.tv.data.models.VideoItem
import com.kidswatch.tv.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlaylistRow(
    val id: Long,
    val youtubePlaylistId: String,
    val displayName: String,
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
    private val db = ServiceLocator.database
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    fun start() {
        loadAndResolve()
    }

    fun refresh() {
        loadAndResolve()
    }

    private fun loadAndResolve() {
        viewModelScope.launch {
            val playlists = db.playlistDao().getAll()

            if (playlists.isEmpty()) {
                _uiState.value = HomeUiState(isEmpty = true, isLoading = false)
                return@launch
            }

            // Show loading with cached data first
            val cachedRows = playlists.map { entity ->
                val cached = PlaylistRepository.getCachedVideos(db, entity.youtubePlaylistId)
                PlaylistRow(
                    id = entity.id,
                    youtubePlaylistId = entity.youtubePlaylistId,
                    displayName = entity.displayName,
                    videos = cached,
                    isOffline = false,
                    error = null,
                    isLoading = true,
                )
            }
            _uiState.value = HomeUiState(rows = cachedRows, isLoading = true)

            // Resolve fresh
            val metas = playlists.map { entity ->
                com.kidswatch.tv.data.PlaylistMeta(
                    id = entity.id,
                    youtubePlaylistId = entity.youtubePlaylistId,
                    displayName = entity.displayName,
                )
            }

            val results = PlaylistRepository.resolveAllPlaylists(metas, db)

            val rows = playlists.map { entity ->
                when (val result = results[entity.youtubePlaylistId]) {
                    is PlaylistResult.Success -> PlaylistRow(
                        id = entity.id,
                        youtubePlaylistId = entity.youtubePlaylistId,
                        displayName = entity.displayName,
                        videos = result.videos,
                        isOffline = false, error = null, isLoading = false,
                    )
                    is PlaylistResult.CachedFallback -> PlaylistRow(
                        id = entity.id,
                        youtubePlaylistId = entity.youtubePlaylistId,
                        displayName = entity.displayName,
                        videos = result.videos,
                        isOffline = true, error = null, isLoading = false,
                    )
                    is PlaylistResult.Error -> PlaylistRow(
                        id = entity.id,
                        youtubePlaylistId = entity.youtubePlaylistId,
                        displayName = entity.displayName,
                        videos = emptyList(),
                        isOffline = false, error = result.message, isLoading = false,
                    )
                    null -> PlaylistRow(
                        id = entity.id,
                        youtubePlaylistId = entity.youtubePlaylistId,
                        displayName = entity.displayName,
                        videos = emptyList(),
                        isOffline = false, error = "Not resolved", isLoading = false,
                    )
                }
            }
            _uiState.value = HomeUiState(rows = rows, isLoading = false)
            AppLogger.success("All playlists resolved")
        }
    }
}
