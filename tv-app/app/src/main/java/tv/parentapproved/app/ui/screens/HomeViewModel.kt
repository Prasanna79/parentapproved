package tv.parentapproved.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.data.ChannelMeta
import tv.parentapproved.app.data.ContentSourceRepository
import tv.parentapproved.app.data.SourceResult
import tv.parentapproved.app.data.models.VideoItem
import tv.parentapproved.app.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlaylistRow(
    val id: Long,
    val sourceId: String,
    val sourceType: String,
    val displayName: String,
    val videoCount: Int,
    val videos: List<VideoItem>,
    val isOffline: Boolean,
    val error: String?,
    val isLoading: Boolean,
) {
    // Keep backward compat for HomeScreen navigation
    val youtubePlaylistId: String get() = sourceId
}

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
            val channels = db.channelDao().getAll()

            if (channels.isEmpty()) {
                _uiState.value = HomeUiState(isEmpty = true, isLoading = false)
                return@launch
            }

            // Show loading with cached data first
            val cachedRows = channels.map { entity ->
                val cached = ContentSourceRepository.getCachedVideos(db, entity.sourceId)
                PlaylistRow(
                    id = entity.id,
                    sourceId = entity.sourceId,
                    sourceType = entity.sourceType,
                    displayName = entity.displayName,
                    videoCount = entity.videoCount,
                    videos = cached,
                    isOffline = false,
                    error = null,
                    isLoading = true,
                )
            }
            _uiState.value = HomeUiState(rows = cachedRows, isLoading = true)

            // Resolve fresh
            val metas = channels.map { entity ->
                ChannelMeta(
                    id = entity.id,
                    sourceType = entity.sourceType,
                    sourceId = entity.sourceId,
                    sourceUrl = entity.sourceUrl,
                    displayName = entity.displayName,
                )
            }

            val results = ContentSourceRepository.resolveAllChannels(metas, db)

            // Re-read entities from DB after resolve (display names may have been updated)
            val updatedChannels = db.channelDao().getAll()
            val entityMap = updatedChannels.associateBy { it.sourceId }

            val rows = channels.map { entity ->
                val updated = entityMap[entity.sourceId] ?: entity
                when (val result = results[entity.sourceId]) {
                    is SourceResult.Success -> PlaylistRow(
                        id = entity.id,
                        sourceId = entity.sourceId,
                        sourceType = entity.sourceType,
                        displayName = updated.displayName,
                        videoCount = result.videos.size,
                        videos = result.videos,
                        isOffline = false, error = null, isLoading = false,
                    )
                    is SourceResult.CachedFallback -> PlaylistRow(
                        id = entity.id,
                        sourceId = entity.sourceId,
                        sourceType = entity.sourceType,
                        displayName = updated.displayName,
                        videoCount = result.videos.size,
                        videos = result.videos,
                        isOffline = true, error = null, isLoading = false,
                    )
                    is SourceResult.Error -> PlaylistRow(
                        id = entity.id,
                        sourceId = entity.sourceId,
                        sourceType = entity.sourceType,
                        displayName = updated.displayName,
                        videoCount = 0,
                        videos = emptyList(),
                        isOffline = false, error = result.message, isLoading = false,
                    )
                    null -> PlaylistRow(
                        id = entity.id,
                        sourceId = entity.sourceId,
                        sourceType = entity.sourceType,
                        displayName = updated.displayName,
                        videoCount = 0,
                        videos = emptyList(),
                        isOffline = false, error = "Not resolved", isLoading = false,
                    )
                }
            }
            _uiState.value = HomeUiState(rows = rows, isLoading = false)
            AppLogger.success("All sources resolved")
        }
    }
}
