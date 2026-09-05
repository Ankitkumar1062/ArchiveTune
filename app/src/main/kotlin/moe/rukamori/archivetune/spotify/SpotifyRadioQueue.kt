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

class SpotifyRadioQueue(
    private val seedTrackId: String,
    private val seedTitle: String? = null,
    private val seedTrack: SpotifyTrack? = null,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val seenTrackIds = mutableSetOf(seedTrackId)
    private var hasMore = true
    private var pagesLoaded = 0

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            val initialItem =
                seedTrack?.toMediaItem()
                    ?: preloadItem?.toMediaItem()
                    ?: Spotify.track(seedTrackId).getOrNull()?.toMediaItem()

            val initialList = mutableListOf<MediaItem>()
            if (initialItem != null) {
                initialList.add(initialItem)
            }

            val recResult =
                Spotify.recommendations(
                    seedTrackIds = listOf(seedTrackId),
                    limit = 25,
                ).getOrNull()

            val recItems =
                recResult?.tracks.orEmpty()
                    .filter { seenTrackIds.add(it.id) }
                    .map { it.toMediaItem() }

            initialList.addAll(recItems)
            if (recItems.isEmpty()) {
                hasMore = false
            }

            Queue.Status(
                title = seedTitle ?: preloadItem?.title ?: "Radio",
                items = initialList,
                mediaItemIndex = 0,
            )
        }

    override fun hasNextPage(): Boolean = hasMore

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (!hasMore) return@withContext emptyList()
            pagesLoaded++
            if (pagesLoaded > 10) {
                hasMore = false
                return@withContext emptyList()
            }

            val seeds = seenTrackIds.toList().takeLast(5)
            if (seeds.isEmpty()) {
                hasMore = false
                return@withContext emptyList()
            }

            val recResult =
                Spotify.recommendations(
                    seedTrackIds = seeds,
                    limit = 20,
                ).getOrNull()

            val recItems =
                recResult?.tracks.orEmpty()
                    .filter { seenTrackIds.add(it.id) }
                    .map { it.toMediaItem() }

            if (recItems.isEmpty()) {
                hasMore = false
            }

            recItems
        }
}
