package com.kidswatch.tv.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.kidswatch.tv.BuildConfig
import com.kidswatch.tv.ui.components.VideoCard
import com.kidswatch.tv.ui.theme.TvAccent
import com.kidswatch.tv.ui.theme.TvBackground
import com.kidswatch.tv.ui.theme.TvPrimary
import com.kidswatch.tv.ui.theme.TvText
import com.kidswatch.tv.ui.theme.TvTextDim
import com.kidswatch.tv.ui.theme.TvWarning

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
            .background(TvBackground)
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
                Text("KidsWatch", style = MaterialTheme.typography.headlineMedium, color = TvText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = TvPrimary),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Refresh Videos", color = TvText)
                    }
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(containerColor = TvAccent),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Connect Phone", color = TvBackground)
                    }
                    Button(
                        onClick = onSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = TvPrimary.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Settings", color = TvText)
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
                                text = "No playlists yet!",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TvText,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Connect your phone to add YouTube playlists",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TvTextDim,
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
                            CircularProgressIndicator(color = TvAccent)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading playlists...", color = TvTextDim)
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
                color = TvTextDim,
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
    onPlayVideo: (com.kidswatch.tv.data.models.VideoItem) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = TvText,
            )
            if (row.isOffline) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvWarning,
                )
            }
            if (row.isLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.height(14.dp).width(14.dp),
                    strokeWidth = 2.dp,
                    color = TvAccent,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            row.error != null -> {
                Text(
                    text = "Playlist no longer available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvAccent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            row.videos.isEmpty() && !row.isLoading -> {
                Text(
                    text = "No videos found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvTextDim,
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
