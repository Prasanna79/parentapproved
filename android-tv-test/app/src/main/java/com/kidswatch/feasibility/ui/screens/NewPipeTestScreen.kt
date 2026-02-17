package com.kidswatch.feasibility.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.kidswatch.feasibility.ui.components.LogEntry
import com.kidswatch.feasibility.ui.components.LogLevel
import com.kidswatch.feasibility.ui.components.ResultLogPanel
import com.kidswatch.feasibility.ui.theme.TvAccent
import com.kidswatch.feasibility.ui.theme.TvBackground
import com.kidswatch.feasibility.ui.theme.TvPrimary
import com.kidswatch.feasibility.ui.theme.TvSuccess
import com.kidswatch.feasibility.ui.theme.TvText
import com.kidswatch.feasibility.ui.theme.TvWarning
import com.kidswatch.feasibility.ui.util.NewPipeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

private val TEST_VIDEOS = listOf(
    "dQw4w9WgXcQ" to "Rick Astley",
    "9bZkp7q19f0" to "Gangnam Style",
    "rfscVS0vtbw" to "Sesame Street",
    "XqZsoesa55w" to "Peppa Pig",
    "M7lc1UVf-VE" to "BabyShark",
)

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewPipeTestScreen(onBack: () -> Unit) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newPipeReady by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logs.add(LogEntry(message, level))
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Init NewPipe on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                NewPipe.init(NewPipeDownloader.instance)
                newPipeReady = true
                log("NewPipe initialized OK", LogLevel.SUCCESS)
            } catch (e: Exception) {
                log("NewPipe init FAILED: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    // Player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val stateName = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($state)"
                }
                log("Player: $stateName", if (state == Player.STATE_READY) LogLevel.SUCCESS else LogLevel.INFO)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) log("Player: PLAYING", LogLevel.SUCCESS)
            }

            override fun onPlayerError(error: PlaybackException) {
                log("Player ERROR: ${error.errorCodeName} - ${error.message}", LogLevel.ERROR)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    fun extractAndPlay(videoId: String) {
        if (!newPipeReady) {
            log("NewPipe not ready yet", LogLevel.WARNING)
            return
        }
        if (extracting) {
            log("Already extracting, wait...", LogLevel.WARNING)
            return
        }
        extracting = true
        log("--- Extracting: $videoId ---")
        val startTime = System.currentTimeMillis()

        scope.launch {
            try {
                val (videoStreams, audioStreams) = withContext(Dispatchers.IO) {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()

                    val name = extractor.name
                    val duration = extractor.length
                    log("Title: $name (${duration}s)")

                    val vs = extractor.videoStreams ?: emptyList()
                    val vOnly = extractor.videoOnlyStreams ?: emptyList()
                    val audio = extractor.audioStreams ?: emptyList()

                    log("Progressive streams: ${vs.size}")
                    vs.forEach { s ->
                        log("  ${s.resolution} ${s.format?.name ?: "?"} ${s.codec ?: "?"}")
                    }
                    log("Video-only streams: ${vOnly.size}")
                    log("Audio streams: ${audio.size}")

                    Pair(vs + vOnly, audio)
                }

                val elapsed = System.currentTimeMillis() - startTime
                log("Extraction took ${elapsed}ms", LogLevel.SUCCESS)

                // Pick best progressive stream (has video+audio combined)
                val progressive = videoStreams.filterIsInstance<VideoStream>()
                    .filter { it.isVideoOnly == false }
                    .maxByOrNull { it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 }

                if (progressive != null) {
                    log("Using progressive: ${progressive.resolution} ${progressive.format?.name}")
                    val dataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
                    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(progressive.content))
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                } else {
                    // Fallback: merge video-only + audio
                    val videoOnly = videoStreams.filterIsInstance<VideoStream>()
                        .filter { it.isVideoOnly == true }
                        .maxByOrNull { it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 }
                    val audio = audioStreams.filterIsInstance<AudioStream>()
                        .maxByOrNull { it.averageBitrate }

                    if (videoOnly != null && audio != null) {
                        log("Using merged: video=${videoOnly.resolution} + audio=${audio.averageBitrate}kbps")
                        val dataSourceFactory = DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
                        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(videoOnly.content))
                        val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(audio.content))
                        exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
                        exoPlayer.prepare()
                    } else {
                        log("No playable streams found!", LogLevel.ERROR)
                    }
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                log("Extraction FAILED (${elapsed}ms): ${e.javaClass.simpleName}: ${e.message}", LogLevel.ERROR)
            } finally {
                extracting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Header
        Text(
            text = "Test 4: NewPipe + ExoPlayer" + if (extracting) " (extracting...)" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = TvText,
        )

        // Video selector buttons
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TEST_VIDEOS.forEach { (id, label) ->
                NpBtn(label, TvAccent) { extractAndPlay(id) }
            }
            NpBtn("Stop", TvWarning) { exoPlayer.stop(); log("Stopped") }
            NpBtn("Clear", TvPrimary.copy(alpha = 0.6f)) { logs.clear() }
            NpBtn("Back", TvPrimary.copy(alpha = 0.4f)) { onBack() }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ExoPlayer view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // Log panel
        ResultLogPanel(
            logs = logs,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        )
    }
}

@Composable
private fun NpBtn(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(28.dp),
    ) {
        Text(text, color = TvText, fontSize = 10.sp)
    }
}
