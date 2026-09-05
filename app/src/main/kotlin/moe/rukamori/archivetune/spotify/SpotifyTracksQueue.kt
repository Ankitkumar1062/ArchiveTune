/*
 * YumaPlayer (2026) | Modified work by MuwMx
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

class SpotifyTracksQueue(
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val allTracks = initialTracks.toList()
    private var resolveOffset = 0
    private var isInitialized = false

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            try {
                if (allTracks.isEmpty()) {
                    return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                }

                val targetIndex = startIndex.coerceIn(allTracks.indices)
                // Fast path: resolve the target track plus a few following tracks first
                val initialWindowEnd = (targetIndex + INITIAL_RESOLVE_WINDOW).coerceAtMost(allTracks.size)
                val initialBatch = allTracks.subList(targetIndex, initialWindowEnd)

                val initialResolved = resolveTrackEntries(initialBatch, offset = targetIndex)
                val resolvedItems = initialResolved.map { it.second }.toMutableList()

                resolveOffset = initialWindowEnd

                if (resolvedItems.isEmpty()) {
                    // Fallback: try remaining tracks if the initial window yielded no candidate
                    val remaining = allTracks.drop(initialWindowEnd)
                    val remainingResolved = resolveTrackEntries(remaining, offset = initialWindowEnd)
                    resolvedItems.addAll(remainingResolved.map { it.second })
                    resolveOffset = allTracks.size
                }

                if (resolvedItems.isEmpty()) {
                    return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
                }

                Queue.Status(
                    title = title,
                    items = resolvedItems,
                    mediaItemIndex = 0,
                )
            } finally {
                isInitialized = true
            }
        }

    override fun hasNextPage(): Boolean = isInitialized && resolveOffset < allTracks.size

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (resolveOffset >= allTracks.size) return@withContext emptyList()

            val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
            val batch = allTracks.subList(resolveOffset, end)
            val currentOffset = resolveOffset
            resolveOffset = end
            resolveTracks(batch, offset = currentOffset)
        }

    private suspend fun resolveTracks(tracks: List<SpotifyTrack>, offset: Int = 0): List<MediaItem> =
        resolveTrackEntries(tracks, offset).map { it.second }

    private suspend fun resolveTrackEntries(tracks: List<SpotifyTrack>, offset: Int = 0): List<Pair<Int, MediaItem>> =
        buildList {
            tracks.chunked(RESOLVE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                val chunkOffset = offset + chunkIndex * RESOLVE_BATCH_SIZE
                val resolvedChunk =
                    coroutineScope {
                        chunk
                            .mapIndexed { index, track ->
                                async {
                                    SpotifyPlaybackResolver
                                        .resolveToMediaItem(track)
                                        ?.let { mediaItem -> chunkOffset + index to mediaItem }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }
                addAll(resolvedChunk)
            }
        }

    companion object {
        private const val INITIAL_RESOLVE_WINDOW = 3
        private const val RESOLVE_BATCH_SIZE = 10
    }
}
