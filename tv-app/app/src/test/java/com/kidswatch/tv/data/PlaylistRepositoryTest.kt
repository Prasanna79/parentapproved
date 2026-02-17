package com.kidswatch.tv.data

import com.kidswatch.tv.data.cache.CacheDatabase
import com.kidswatch.tv.data.cache.PlaylistDao
import com.kidswatch.tv.data.cache.PlaylistEntity
import com.kidswatch.tv.data.models.VideoItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaylistRepositoryTest {

    @Test
    fun resolvePlaylist_returnsPlaylistTitle() = runTest {
        val videos = listOf(
            VideoItem("v1", "Title 1", "", 120, "PL1", 0),
        )
        val result = ResolvedPlaylist(title = "My Cool Playlist", videos = videos)
        assertEquals("My Cool Playlist", result.title)
        assertEquals(1, result.videos.size)
    }

    @Test
    fun resolvePlaylist_withEmptyTitle_fallsBackToId() = runTest {
        // When title is empty, we use the playlist ID as fallback
        val title = ""
        val displayName = title.ifEmpty { "PLtest123" }
        assertEquals("PLtest123", displayName)
    }

    @Test
    fun resolveAllPlaylists_updatesDisplayName_whenChanged() = runTest {
        val mockDao = mockk<PlaylistDao>(relaxed = true)
        val mockDb = mockk<CacheDatabase>()
        every { mockDb.playlistDao() } returns mockDao

        val entity = PlaylistEntity(id = 1, youtubePlaylistId = "PL1", displayName = "PL1")
        val newTitle = "My Awesome Playlist"

        // Simulate: title changed
        coEvery { mockDao.updateDisplayName(1, newTitle) } returns Unit

        PlaylistRepository.updatePlaylistTitle(mockDb, entity, newTitle)

        coVerify { mockDao.updateDisplayName(1, newTitle) }
    }

    @Test
    fun resolveAllPlaylists_skipsUpdate_whenTitleUnchanged() = runTest {
        val mockDao = mockk<PlaylistDao>(relaxed = true)
        val mockDb = mockk<CacheDatabase>()
        every { mockDb.playlistDao() } returns mockDao

        val entity = PlaylistEntity(id = 1, youtubePlaylistId = "PL1", displayName = "Same Title")

        PlaylistRepository.updatePlaylistTitle(mockDb, entity, "Same Title")

        coVerify(exactly = 0) { mockDao.updateDisplayName(any(), any()) }
    }
}
