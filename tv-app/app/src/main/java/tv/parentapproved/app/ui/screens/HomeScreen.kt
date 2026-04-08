package tv.parentapproved.app.ui.screens

import android.app.ActivityOptions
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.parentapproved.app.BuildConfig
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.timelimits.TimeLimitStatus
import tv.parentapproved.app.ui.components.AppCard
import tv.parentapproved.app.ui.components.VideoCard
import tv.parentapproved.app.ui.theme.KidAccent
import tv.parentapproved.app.ui.theme.KidBackground
import tv.parentapproved.app.ui.theme.KidSurface
import tv.parentapproved.app.ui.theme.KidText
import tv.parentapproved.app.ui.theme.KidTextDim
import tv.parentapproved.app.ui.theme.OverscanPadding
import tv.parentapproved.app.ui.theme.StatusError
import tv.parentapproved.app.ui.theme.StatusWarning

@Composable
fun HomeScreen(
    onPlayVideo: (videoId: String, playlistId: String, videoIndex: Int) -> Unit,
    onSettings: () -> Unit,
    onConnect: () -> Unit,
    onLocked: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showingVideos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    // Check time limits periodically
    LaunchedEffect(Unit) {
        while (true) {
            val status = ServiceLocator.timeLimitManager.canPlay()
            if (status is TimeLimitStatus.Blocked) {
                // If kiosk is active with enforceTimeLimitsOnAllApps, revoke third-party app access
                if (ServiceLocator.isKioskManagerInitialized()) {
                    val kiosk = ServiceLocator.kioskManager
                    if (kiosk.isDeviceOwner()) {
                        val config = ServiceLocator.database.kioskDao().getConfig()
                        if (config?.kioskEnabled == true && config.enforceTimeLimitsOnAllApps) {
                            kiosk.enforceTimeLimitExpiry()
                        }
                    }
                }
                onLocked(status.reason.name.lowercase())
                return@LaunchedEffect
            } else {
                // If time is allowed again and kiosk had previously revoked access, restore it
                if (ServiceLocator.isKioskManagerInitialized()) {
                    val kiosk = ServiceLocator.kioskManager
                    if (kiosk.isDeviceOwner()) {
                        val config = ServiceLocator.database.kioskDao().getConfig()
                        if (config?.kioskEnabled == true && config.enforceTimeLimitsOnAllApps) {
                            val whitelisted = ServiceLocator.database.whitelistDao().getWhitelisted()
                            kiosk.restoreAfterTimeLimitExpiry(whitelisted.map { it.packageName })
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(5_000)
        }
    }

    val isKioskHome = uiState.kioskEnabled && uiState.whitelistedApps.isNotEmpty() && !showingVideos

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KidBackground)
            .padding(OverscanPadding)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showingVideos && uiState.kioskEnabled) {
                    // Back button when in videos view within kiosk mode
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { showingVideos = false },
                            colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back to Apps",
                                tint = KidText,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Videos", style = MaterialTheme.typography.headlineMedium, color = KidText)
                    }
                } else {
                    Text("ParentApproved", style = MaterialTheme.typography.headlineMedium, color = KidText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Refresh",
                            tint = KidText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (!isKioskHome) {
                        Button(
                            onClick = onConnect,
                            colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = null,
                                tint = KidText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Phone", color = KidText)
                        }
                    }
                    Button(
                        onClick = onSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = KidText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (isKioskHome) {
                // Kiosk home: apps grid with a "Videos" card
                KioskAppsContent(
                    apps = uiState.whitelistedApps,
                    onLaunchApp = { packageName ->
                        val context = viewModel.getApplication<android.app.Application>()
                        val intent = ServiceLocator.kioskManager.getLeanbackLaunchIntent(packageName)
                        if (intent != null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                && ServiceLocator.kioskManager.isDeviceOwner()) {
                                val options = ActivityOptions.makeBasic()
                                options.setLockTaskEnabled(true)
                                context.startActivity(intent, options.toBundle())
                            } else {
                                context.startActivity(intent)
                            }
                        }
                    },
                    onShowVideos = { showingVideos = true },
                )
            } else {
                // Normal video content (original HomeScreen behavior)
                VideoContent(
                    uiState = uiState,
                    onPlayVideo = onPlayVideo,
                )
            }
        }

        // Version overlay
        if (BuildConfig.IS_DEBUG) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}-debug",
                style = MaterialTheme.typography.bodySmall,
                color = KidTextDim,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun KioskAppsContent(
    apps: List<WhitelistedApp>,
    onLaunchApp: (String) -> Unit,
    onShowVideos: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Apps",
            style = MaterialTheme.typography.titleLarge,
            color = KidText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // "Videos" card — opens PA's video content
            item {
                AppCard(
                    appName = "Videos",
                    icon = null,
                    onClick = onShowVideos,
                    leadingIcon = Icons.Rounded.PlayCircle,
                )
            }
            // Whitelisted apps
            items(apps) { app ->
                AppCard(
                    appName = app.displayName,
                    icon = app.icon,
                    onClick = { onLaunchApp(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun VideoContent(
    uiState: HomeUiState,
    onPlayVideo: (videoId: String, playlistId: String, videoIndex: Int) -> Unit,
) {
    when {
        uiState.isEmpty -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No videos yet!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = KidText,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect your phone to add YouTube videos, playlists, or channels",
                        style = MaterialTheme.typography.bodyLarge,
                        color = KidTextDim,
                    )
                }
            }
        }
        uiState.isLoading && uiState.rows.all { it.videos.isEmpty() } -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KidAccent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading playlists...", color = KidTextDim)
                }
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(uiState.rows) { row ->
                    PlaylistRowSection(
                        row = row,
                        onPlayVideo = { video ->
                            onPlayVideo(video.videoId, row.youtubePlaylistId, video.position)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRowSection(
    row: PlaylistRow,
    onPlayVideo: (tv.parentapproved.app.data.models.VideoItem) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (row.videoCount > 0) "${row.displayName} \u2014 ${row.videoCount} videos" else row.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = KidText,
            )
            if (row.isOffline) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning,
                )
            }
            if (row.isLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.height(14.dp).width(14.dp),
                    strokeWidth = 2.dp,
                    color = KidAccent,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            row.error != null -> {
                Text(
                    text = "Playlist no longer available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusError,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            row.videos.isEmpty() && !row.isLoading -> {
                Text(
                    text = "No videos found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KidTextDim,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(row.videos) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onPlayVideo(video) },
                        )
                    }
                }
            }
        }
    }
}
