package tv.parentapproved.app.ui.screens

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
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.parentapproved.app.BuildConfig
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
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.start()
    }

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
                Text("ParentApproved", style = MaterialTheme.typography.headlineMedium, color = KidText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = KidSurface),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Refresh Videos",
                            tint = KidText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
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
