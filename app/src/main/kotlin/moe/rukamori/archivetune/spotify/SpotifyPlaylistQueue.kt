/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

internal const val SPOTIFY_LIKED_SONGS_ID = "__spotify_liked_songs__"

class SpotifyPlaylistQueue(
    private val playlistId: String,
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val allTracks = mutableListOf<SpotifyTrack>()
    private var apiFetchOffset = 0
    private var apiTotal = 0
    private var apiHasMore = true

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            if (initialTracks.isNotEmpty()) {
                allTracks += initialTracks
                apiTotal = initialTracks.size
                apiFetchOffset = apiTotal
                apiHasMore = false
            } else {
                fetchNextApiPage()
            }

            while (startIndex >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }

            if (allTracks.isEmpty()) {
                return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }

            val targetIndex = startIndex.coerceIn(allTracks.indices)
            val items = allTracks.map { it.toMediaItem() }

            Queue.Status(
                title = title,
                items = items,
                mediaItemIndex = targetIndex,
            )
        }

    override fun hasNextPage(): Boolean = apiHasMore

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (!apiHasMore) return@withContext emptyList()
            val beforeSize = allTracks.size
            fetchNextApiPage()
            val newTracks = allTracks.subList(beforeSize, allTracks.size)
            newTracks.map { it.toMediaItem() }
        }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        if (playlistId == SPOTIFY_LIKED_SONGS_ID) {
            val page = Spotify.likedSongs(limit = SPOTIFY_PAGE_SIZE, offset = apiFetchOffset).getOrThrow()
            apiTotal = page.total
            allTracks += page.items.mapNotNull { it.track.takeUnless(SpotifyTrack::isLocal) }
            apiFetchOffset += page.items.size
            apiHasMore = apiFetchOffset < apiTotal
            return
        }
        val result =
            Spotify
                .playlistTracks(
                    playlistId = playlistId,
                    limit = SPOTIFY_PAGE_SIZE,
                    offset = apiFetchOffset,
                ).getOrThrow()
        apiTotal = result.total
        val fetched = result.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
        allTracks += fetched
        apiFetchOffset += result.items.size
        apiHasMore = apiFetchOffset < apiTotal
    }

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
    }
}

