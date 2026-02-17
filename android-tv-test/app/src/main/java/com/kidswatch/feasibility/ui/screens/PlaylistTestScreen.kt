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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor

private val TEST_PLAYLISTS = listOf(
    "PLRqwX-V7Uu6ZiZxtDDRCi6uhfTH4FilpH" to "~245 videos (Coding Train)",
    "PL8dPuuaLjXtPW_ofbxdHNciuLoTRLPMgB" to "~50 videos (CrashCourse Bio)",
    "PL8dPuuaLjXtMwmepBjTSG593eG7ObzO7s" to "~47 videos (CrashCourse US)",
    "PLxxx_INVALID_xxx" to "Invalid/Private",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistTestScreen(onBack: () -> Unit) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    val scope = rememberCoroutineScope()
    var newPipeReady by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        logs.add(LogEntry(message, level))
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

    fun resolvePlaylist(playlistId: String, label: String) {
        if (!newPipeReady) {
            log("NewPipe not ready yet", LogLevel.WARNING)
            return
        }
        if (resolving) {
            log("Already resolving, wait...", LogLevel.WARNING)
            return
        }
        resolving = true
        log("--- Resolving: $label ---")
        val startTime = System.currentTimeMillis()

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = "https://www.youtube.com/playlist?list=$playlistId"
                    val extractor = ServiceList.YouTube.getPlaylistExtractor(url)
                    extractor.fetchPage()

                    val name = extractor.name
                    log("Playlist: $name")

                    val videos = mutableListOf<VideoInfo>()
                    var page: org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<org.schabi.newpipe.extractor.stream.StreamInfoItem>? = null
                    var pageNum = 0

                    // First page
                    val initialPage = extractor.initialPage
                    pageNum++
                    initialPage.items.forEachIndexed { i, item ->
                        videos.add(VideoInfo(
                            index = videos.size,
                            title = item.name ?: "(no title)",
                            duration = item.duration,
                            hasThumbnail = !item.thumbnails.isNullOrEmpty(),
                        ))
                    }
                    log("Page $pageNum: ${initialPage.items.size} items (total: ${videos.size})")

                    // Subsequent pages
                    var nextPage = initialPage.nextPage
                    while (nextPage != null) {
                        page = extractor.getPage(nextPage)
                        pageNum++
                        page!!.items.forEachIndexed { i, item ->
                            videos.add(VideoInfo(
                                index = videos.size,
                                title = item.name ?: "(no title)",
                                duration = item.duration,
                                hasThumbnail = !item.thumbnails.isNullOrEmpty(),
                            ))
                        }
                        log("Page $pageNum: ${page!!.items.size} items (total: ${videos.size})")
                        nextPage = page!!.nextPage
                    }

                    videos
                }

                val elapsed = System.currentTimeMillis() - startTime

                // Log per-video summary (first 10 + last 5 for large lists)
                val showCount = if (result.size <= 15) result.size else 15
                result.take(10).forEach { v ->
                    val thumbIcon = if (v.hasThumbnail) "Y" else "N"
                    log("  [${v.index}] ${v.duration}s thumb=$thumbIcon ${v.title.take(50)}")
                }
                if (result.size > 15) {
                    log("  ... (${result.size - 15} more) ...")
                    result.takeLast(5).forEach { v ->
                        val thumbIcon = if (v.hasThumbnail) "Y" else "N"
                        log("  [${v.index}] ${v.duration}s thumb=$thumbIcon ${v.title.take(50)}")
                    }
                }

                // Summary
                val withTitle = result.count { it.title != "(no title)" }
                val withThumb = result.count { it.hasThumbnail }
                val withDuration = result.count { it.duration > 0 }
                val metadataPct = if (result.isNotEmpty()) {
                    ((withTitle + withThumb + withDuration) * 100) / (result.size * 3)
                } else 0

                log("=== SUMMARY ===", LogLevel.SUCCESS)
                log("Total videos: ${result.size}", LogLevel.SUCCESS)
                log("Elapsed: ${elapsed}ms", LogLevel.SUCCESS)
                log("With title: $withTitle/${result.size}", LogLevel.SUCCESS)
                log("With thumbnail: $withThumb/${result.size}", LogLevel.SUCCESS)
                log("With duration: $withDuration/${result.size}", LogLevel.SUCCESS)
                log("Metadata completeness: $metadataPct%", LogLevel.SUCCESS)

                if (result.size >= 50 && elapsed < 10000) {
                    log("EXIT CRITERIA MET: 50+ videos in <10s", LogLevel.SUCCESS)
                } else if (result.size < 50) {
                    log("Playlist has <50 videos (not exit criteria target)", LogLevel.WARNING)
                } else {
                    log("EXIT CRITERIA FAILED: took ${elapsed}ms (>10s)", LogLevel.ERROR)
                }

            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                log("Resolution FAILED (${elapsed}ms): ${e.javaClass.simpleName}: ${e.message}", LogLevel.ERROR)
            } finally {
                resolving = false
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
            text = "Test 5: Playlist Resolution" + if (resolving) " (resolving...)" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = TvText,
        )

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TEST_PLAYLISTS.forEachIndexed { i, (id, label) ->
                val color = when (i) {
                    0 -> TvSuccess
                    1 -> TvAccent
                    2 -> TvWarning
                    else -> TvPrimary.copy(alpha = 0.7f)
                }
                PlBtn(label, color) { resolvePlaylist(id, label) }
            }
            PlBtn("Clear", TvPrimary.copy(alpha = 0.6f)) { logs.clear() }
            PlBtn("Back", TvPrimary.copy(alpha = 0.4f)) { onBack() }
        }

        Spacer(modifier = Modifier.height(4.dp))

        ResultLogPanel(
            logs = logs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

private data class VideoInfo(
    val index: Int,
    val title: String,
    val duration: Long,
    val hasThumbnail: Boolean,
)

@Composable
private fun PlBtn(
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
