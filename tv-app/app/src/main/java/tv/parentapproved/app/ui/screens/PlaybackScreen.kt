package tv.parentapproved.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.data.ContentSourceRepository
import tv.parentapproved.app.data.events.PlayEventRecorder
import tv.parentapproved.app.data.models.VideoItem
import tv.parentapproved.app.playback.DpadKeyHandler
import tv.parentapproved.app.playback.PlaybackCommand
import tv.parentapproved.app.playback.PlaybackCommandBus
import tv.parentapproved.app.playback.StreamSelector
import tv.parentapproved.app.ui.theme.KidBackground
import tv.parentapproved.app.ui.theme.KidTextDim
import tv.parentapproved.app.ui.theme.StatusError
import tv.parentapproved.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(
    videoId: String,
    playlistId: String,
    startIndex: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ServiceLocator.database }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentVideoIndex by remember { mutableIntStateOf(startIndex) }
    var playlist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var playStartTime by remember { mutableStateOf(0L) }
    var playlistTitle by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Mutable refs for callbacks that need latest state
    val playlistRef = remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    val indexRef = remember { mutableIntStateOf(startIndex) }
    playlistRef.value = playlist
    indexRef.intValue = currentVideoIndex

    // Helper object to break circular references
    val controller = remember {
        object {
            var extractAndPlay: ((String) -> Unit)? = null
            var advanceToNext: (() -> Unit)? = null
            var goToPrev: (() -> Unit)? = null
            var skipAfterDelay: (() -> Unit)? = null
        }
    }

    controller.advanceToNext = {
        val elapsed = ((System.currentTimeMillis() - playStartTime) / 1000).toInt()
        PlayEventRecorder.endEvent(elapsed, 100)

        val nextIndex = indexRef.intValue + 1
        if (nextIndex < playlistRef.value.size) {
            currentVideoIndex = nextIndex
            indexRef.intValue = nextIndex
            controller.extractAndPlay?.invoke(playlistRef.value[nextIndex].videoId)
        } else {
            AppLogger.log("Playlist ended")
            onBack()
        }
    }

    controller.goToPrev = {
        val elapsedSec = ((System.currentTimeMillis() - playStartTime) / 1000).toInt()
        PlayEventRecorder.endEvent(elapsedSec, 0)

        if (elapsedSec > 5) {
            // Restart current video
            controller.extractAndPlay?.invoke(playlistRef.value[indexRef.intValue].videoId)
        } else {
            val prevIndex = indexRef.intValue - 1
            if (prevIndex >= 0) {
                currentVideoIndex = prevIndex
                indexRef.intValue = prevIndex
                controller.extractAndPlay?.invoke(playlistRef.value[prevIndex].videoId)
            } else {
                // Already at first video, restart it
                controller.extractAndPlay?.invoke(playlistRef.value[indexRef.intValue].videoId)
            }
        }
    }

    controller.skipAfterDelay = {
        scope.launch {
            delay(3000)
            controller.advanceToNext?.invoke()
        }
    }

    controller.extractAndPlay = { vid ->
        errorMessage = null
        AppLogger.log("Extracting stream: $vid")
        playStartTime = System.currentTimeMillis()

        val currentVideo = playlistRef.value.getOrNull(indexRef.intValue)
        val title = currentVideo?.title ?: vid
        val durationMs = (currentVideo?.durationSeconds ?: 0) * 1000L

        PlayEventRecorder.startEvent(
            videoId = vid,
            playlistId = playlistId,
            title = title,
            playlistTitle = playlistTitle,
            durationMs = durationMs,
        )

        scope.launch {
            try {
                val stream = withContext(Dispatchers.IO) {
                    val url = "https://www.youtube.com/watch?v=$vid"
                    val extractor = ServiceList.YouTube.getStreamExtractor(url)
                    extractor.fetchPage()

                    // Update title from extractor if we only had the video ID
                    val extractedTitle = try { extractor.name } catch (_: Exception) { null }
                    if (!extractedTitle.isNullOrBlank() && extractedTitle != vid) {
                        PlayEventRecorder.updateTitle(extractedTitle)
                    }

                    val progressive = (extractor.videoStreams ?: emptyList()).filter { !it.isVideoOnly }
                    val videoOnly = extractor.videoOnlyStreams ?: emptyList()
                    val audio = extractor.audioStreams ?: emptyList()

                    StreamSelector.selectBest(progressive, videoOnly, audio)
                }

                if (stream == null) {
                    errorMessage = "Can't play this video"
                    controller.skipAfterDelay?.invoke()
                    return@launch
                }

                val factory = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
                if (stream.audioUrl != null) {
                    val videoSource = ProgressiveMediaSource.Factory(factory)
                        .createMediaSource(MediaItem.fromUri(stream.videoUrl))
                    val audioSource = ProgressiveMediaSource.Factory(factory)
                        .createMediaSource(MediaItem.fromUri(stream.audioUrl))
                    exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
                } else {
                    val source = ProgressiveMediaSource.Factory(factory)
                        .createMediaSource(MediaItem.fromUri(stream.videoUrl))
                    exoPlayer.setMediaSource(source)
                }
                exoPlayer.prepare()
                AppLogger.success("Playing $vid at ${stream.resolution}")

            } catch (e: Exception) {
                AppLogger.error("Extraction failed: ${e.message}")
                errorMessage = "Can't play this video"
                controller.skipAfterDelay?.invoke()
            }
        }
    }

    // Load playlist for auto-advance
    LaunchedEffect(playlistId) {
        val cached = ContentSourceRepository.getCachedVideos(db, playlistId)
        playlist = cached
        playlistRef.value = cached

        // Try to get source display name from DB
        try {
            val entity = withContext(Dispatchers.IO) {
                db.channelDao().getBySourceId(playlistId)
            }
            playlistTitle = entity?.displayName ?: playlistId
        } catch (_: Exception) {
            playlistTitle = playlistId
        }
    }

    // Collect commands from PlaybackCommandBus
    LaunchedEffect(Unit) {
        PlaybackCommandBus.commands.collect { command ->
            when (command) {
                PlaybackCommand.Stop -> {
                    onBack()
                }
                PlaybackCommand.SkipNext -> {
                    controller.advanceToNext?.invoke()
                }
                PlaybackCommand.SkipPrev -> {
                    controller.goToPrev?.invoke()
                }
                PlaybackCommand.TogglePause -> {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        PlayEventRecorder.onPause()
                    } else {
                        exoPlayer.play()
                        PlayEventRecorder.onResume()
                    }
                }
            }
        }
    }

    // Player listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    controller.advanceToNext?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.error("Player error: ${error.errorCodeName}")
                errorMessage = "Can't play this video"
                controller.skipAfterDelay?.invoke()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val elapsed = ((System.currentTimeMillis() - playStartTime) / 1000).toInt()
            if (elapsed > 0) {
                val position = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                val pct = if (duration > 0) ((position * 100) / duration).toInt() else 0
                PlayEventRecorder.endEvent(elapsed, pct)
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Periodic event update
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(10_000)
            if (exoPlayer.isPlaying) {
                val elapsed = ((System.currentTimeMillis() - playStartTime) / 1000).toInt()
                val position = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                val pct = if (duration > 0) ((position * 100) / duration).toInt() else 0
                PlayEventRecorder.updateEvent(elapsed, pct)
            }
        }
    }

    // Start playback
    LaunchedEffect(videoId) {
        controller.extractAndPlay?.invoke(videoId)
    }

    // Request focus for D-pad
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KidBackground)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val command = DpadKeyHandler.mapKeyToCommand(event.nativeKeyEvent.keyCode)
                    if (command != null) {
                        PlaybackCommandBus.send(command)
                        true
                    } else false
                } else false
            }
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    keepScreenOn = true
                    player = object : ForwardingPlayer(exoPlayer) {
                        override fun hasNextMediaItem(): Boolean =
                            indexRef.intValue < playlistRef.value.size - 1

                        override fun hasPreviousMediaItem(): Boolean = true

                        override fun seekToNextMediaItem() {
                            controller.advanceToNext?.invoke()
                        }

                        override fun seekToPreviousMediaItem() {
                            controller.goToPrev?.invoke()
                        }

                        override fun seekToNext() {
                            controller.advanceToNext?.invoke()
                        }

                        override fun seekToPrevious() {
                            controller.goToPrev?.invoke()
                        }
                    }
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KidBackground.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.headlineMedium,
                        color = StatusError,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Skipping in 3 seconds...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KidTextDim,
                    )
                }
            }
        }
    }
}
