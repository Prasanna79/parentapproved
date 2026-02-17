package com.kidswatch.feasibility.ui.screens

import android.util.Log
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
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.kidswatch.feasibility.debug.DebugAction
import com.kidswatch.feasibility.debug.DebugActionBus
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
    "XqZsoesa55w" to "BabyShark",
    "dQw4w9WgXcQ" to "RickAstley",
    "rfscVS0vtbw" to "SesameStreet",
)

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamQualityTestScreen(onBack: () -> Unit) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var newPipeReady by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf(false) }

    // Store extracted streams for playback buttons
    var progressiveStreams by remember { mutableStateOf<List<VideoStream>>(emptyList()) }
    var videoOnlyStreams by remember { mutableStateOf<List<VideoStream>>(emptyList()) }
    var audioStreams by remember { mutableStateOf<List<AudioStream>>(emptyList()) }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        Log.d("KW-Test6", "[${level.name}] $message")
        logs.add(LogEntry(message, level))
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

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
                if (state == Player.STATE_READY) {
                    val format = exoPlayer.videoFormat
                    if (format != null) {
                        log("Rendered: ${format.width}x${format.height} ${format.codecs ?: ""} ${format.bitrate / 1000}kbps", LogLevel.SUCCESS)
                    }
                }
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

    fun extractStreams(videoId: String) {
        if (!newPipeReady || extracting) return
        extracting = true
        progressiveStreams = emptyList()
        videoOnlyStreams = emptyList()
        audioStreams = emptyList()
        log("--- Extracting streams: $videoId ---")
        val startTime = System.currentTimeMillis()

        scope.launch {
            try {
                val (prog, vOnly, audio) = withContext(Dispatchers.IO) {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()
                    log("Title: ${extractor.name} (${extractor.length}s)")

                    val p = extractor.videoStreams?.filter { !it.isVideoOnly } ?: emptyList()
                    val v = extractor.videoOnlyStreams ?: emptyList()
                    val a = extractor.audioStreams ?: emptyList()
                    Triple(p, v, a)
                }

                val elapsed = System.currentTimeMillis() - startTime
                log("Extraction: ${elapsed}ms", LogLevel.SUCCESS)

                log("=== PROGRESSIVE (video+audio) ===", LogLevel.SUCCESS)
                prog.forEach { s ->
                    log("  ${s.resolution} ${s.format?.name ?: "?"} codec=${s.codec ?: "?"} bitrate=${s.bitrate / 1000}kbps")
                }
                if (prog.isEmpty()) log("  (none available)", LogLevel.WARNING)

                log("=== VIDEO-ONLY ===")
                vOnly.forEach { s ->
                    log("  ${s.resolution} ${s.format?.name ?: "?"} codec=${s.codec ?: "?"} bitrate=${s.bitrate / 1000}kbps")
                }

                log("=== AUDIO ===")
                audio.forEach { s ->
                    log("  ${s.format?.name ?: "?"} codec=${s.codec ?: "?"} bitrate=${s.averageBitrate}kbps")
                }

                progressiveStreams = prog
                videoOnlyStreams = vOnly
                audioStreams = audio

                // Check for 720p+ availability
                val has720prog = prog.any { resolutionToInt(it.resolution) >= 720 }
                val has720merge = vOnly.any { resolutionToInt(it.resolution) >= 720 } && audio.isNotEmpty()
                if (has720prog) {
                    log("720p+ progressive available", LogLevel.SUCCESS)
                } else if (has720merge) {
                    log("720p+ via merge available (no progressive)", LogLevel.WARNING)
                } else {
                    log("No 720p+ streams at all!", LogLevel.ERROR)
                }

            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                log("Extraction FAILED (${elapsed}ms): ${e.javaClass.simpleName}: ${e.message}", LogLevel.ERROR)
            } finally {
                extracting = false
            }
        }
    }

    fun playProgressive(resolution: String) {
        val stream = progressiveStreams.find { it.resolution == resolution }
        if (stream == null) {
            log("No progressive $resolution stream", LogLevel.ERROR)
            return
        }
        log("Playing progressive $resolution...")
        val factory = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
        val source = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(stream.content))
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
    }

    fun playMerged(resolution: String) {
        val video = videoOnlyStreams.find { it.resolution == resolution }
        val audio = audioStreams.maxByOrNull { it.averageBitrate }
        if (video == null || audio == null) {
            log("Can't merge $resolution: video=${video != null} audio=${audio != null}", LogLevel.ERROR)
            return
        }
        log("Playing merged $resolution + audio ${audio.averageBitrate}kbps...")
        val factory = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
        val videoSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(video.content))
        val audioSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(audio.content))
        exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
        exoPlayer.prepare()
    }

    // Listen for ADB debug actions
    LaunchedEffect(Unit) {
        DebugActionBus.actions.collect { action ->
            when (action) {
                is DebugAction.ExtractStreams -> {
                    val (id, _) = TEST_VIDEOS.getOrElse(action.index) { TEST_VIDEOS[0] }
                    extractStreams(id)
                }
                is DebugAction.PlayProgressive -> playProgressive(action.resolution)
                is DebugAction.PlayMerged -> playMerged(action.resolution)
                is DebugAction.StopPlayer -> { exoPlayer.stop(); log("Stopped") }
                is DebugAction.ClearLogs -> logs.clear()
                else -> {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Test 6: Stream Quality" + if (extracting) " (extracting...)" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = TvText,
        )

        // Row 1: Extract buttons
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TEST_VIDEOS.forEach { (id, label) ->
                SqBtn("Extract $label", TvAccent) { extractStreams(id) }
            }
            SqBtn("Stop", TvWarning) { exoPlayer.stop(); log("Stopped") }
            SqBtn("Clear", TvPrimary.copy(alpha = 0.6f)) { logs.clear() }
            SqBtn("Back", TvPrimary.copy(alpha = 0.4f)) { onBack() }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Row 2: Play buttons (only when streams extracted)
        if (progressiveStreams.isNotEmpty() || videoOnlyStreams.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // Progressive play buttons
                progressiveStreams.sortedByDescending { resolutionToInt(it.resolution) }.forEach { s ->
                    SqBtn("${s.resolution} Prog", TvSuccess) { playProgressive(s.resolution) }
                }
                // Merge play buttons for 720p and 1080p
                listOf("720p", "1080p").forEach { res ->
                    if (videoOnlyStreams.any { it.resolution == res }) {
                        SqBtn("$res Merge", TvWarning) { playMerged(res) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

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

        ResultLogPanel(
            logs = logs,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        )
    }
}

private fun resolutionToInt(resolution: String?): Int {
    return resolution?.replace("p", "")?.toIntOrNull() ?: 0
}

@Composable
private fun SqBtn(
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
