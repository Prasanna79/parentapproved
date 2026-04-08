package tv.parentapproved.app.ui.screens

import android.app.Application
import android.graphics.drawable.Drawable
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

data class WhitelistedApp(
    val packageName: String,
    val displayName: String,
    val icon: Drawable?,
)

data class HomeUiState(
    val rows: List<PlaylistRow> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val kioskEnabled: Boolean = false,
    val whitelistedApps: List<WhitelistedApp> = emptyList(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ServiceLocator.database
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
    private var started = false
    private var lastChannelSignature = ""

    fun start() {
        if (started) return // Prevent duplicate poll loops on re-composition
        started = true
        loadAndResolve()
        loadKioskApps()
        pollForChanges()
    }

    fun refresh() {
        loadAndResolve()
        loadKioskApps()
    }

    /**
     * Single poll loop that watches for both content and kiosk config changes.
     * Runs every 10s. Detects: channels added/removed, kiosk toggled, whitelist changed.
     */
    private fun pollForChanges() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)

                // Check content changes: channel list signature (ids + video counts)
                val channels = db.channelDao().getAll()
                val sig = channels.joinToString(",") { "${it.id}:${it.videoCount}" }
                if (sig != lastChannelSignature) {
                    lastChannelSignature = sig
                    loadAndResolve()
                }

                // Check kiosk changes
                val config = db.kioskDao().getConfig()
                val shouldBeEnabled = config?.kioskEnabled == true
                val whitelisted = db.whitelistDao().getWhitelisted()
                val currentState = _uiState.value
                if (shouldBeEnabled != currentState.kioskEnabled
                    || whitelisted.map { it.packageName }.toSet() != currentState.whitelistedApps.map { it.packageName }.toSet()
                ) {
                    loadKioskApps()
                }
            }
        }
    }

    private fun loadKioskApps() {
        viewModelScope.launch {
            val config = db.kioskDao().getConfig()
            if (config?.kioskEnabled != true) {
                // Kiosk disabled — clear kiosk state
                _uiState.value = _uiState.value.copy(
                    kioskEnabled = false,
                    whitelistedApps = emptyList(),
                )
                return@launch
            }

            val whitelisted = db.whitelistDao().getWhitelisted()
            val pm = getApplication<Application>().packageManager
            val apps = whitelisted.map { entity ->
                val icon = try {
                    pm.getApplicationIcon(entity.packageName)
                } catch (e: Exception) {
                    null
                }
                WhitelistedApp(
                    packageName = entity.packageName,
                    displayName = entity.displayName,
                    icon = icon,
                )
            }
            _uiState.value = _uiState.value.copy(
                kioskEnabled = true,
                whitelistedApps = apps,
            )
        }
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
