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
    private var nextPageUrl: String? = null
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

            var queueTitle = seedTitle ?: preloadItem?.title ?: "Radio"

            // Tier 1: Query official Spotify radio-apollo microservice
            val station = Spotify.radioStation(seedTrackId = seedTrackId, count = 50).getOrNull()
            if (station != null && station.tracks.isNotEmpty()) {
                val radioItems =
                    station.tracks
                        .filter { seenTrackIds.add(it.id) }
                        .map { it.toMediaItem() }
                initialList.addAll(radioItems)
                nextPageUrl = station.nextPageUrl
                hasMore = !nextPageUrl.isNullOrBlank()
                val stationTitle = station.title
                if (!stationTitle.isNullOrBlank()) {
                    queueTitle = stationTitle
                }
            } else {
                // Tier 2 Fallback: Spotify Radio Playlist via Search / GraphQL
                val query = (seedTitle ?: preloadItem?.title ?: seedTrack?.name)?.let { "$it Radio" } ?: "Radio"
                val searchResult = Spotify.search(query = query, types = listOf("playlist"), limit = 5).getOrNull()
                val radioPlaylist =
                    searchResult?.playlists?.items?.firstOrNull {
                        it.name.contains("Radio", ignoreCase = true) || it.name.contains("Mix", ignoreCase = true)
                    } ?: searchResult?.playlists?.items?.firstOrNull()

                if (radioPlaylist != null) {
                    val result = Spotify.playlistTracks(playlistId = radioPlaylist.id, limit = 50, offset = 0).getOrNull()
                    val plTracks = result?.items.orEmpty().mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
                    val plItems =
                        plTracks
                            .filter { seenTrackIds.add(it.id) }
                            .map { it.toMediaItem() }
                    initialList.addAll(plItems)
                    hasMore = false
                    queueTitle = radioPlaylist.name
                } else {
                    hasMore = false
                }
            }

            Queue.Status(
                title = queueTitle,
                items = initialList,
                mediaItemIndex = 0,
            )
        }

    override fun hasNextPage(): Boolean = hasMore && !nextPageUrl.isNullOrBlank()

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val url = nextPageUrl
            if (!hasMore || url.isNullOrBlank()) {
                hasMore = false
                return@withContext emptyList()
            }
            pagesLoaded++
            if (pagesLoaded > 10) {
                hasMore = false
                return@withContext emptyList()
            }

            val nextStation = Spotify.radioNextPage(url).getOrNull()
            if (nextStation != null && nextStation.tracks.isNotEmpty()) {
                nextPageUrl = nextStation.nextPageUrl
                hasMore = !nextPageUrl.isNullOrBlank()
                nextStation.tracks
                    .filter { seenTrackIds.add(it.id) }
                    .map { it.toMediaItem() }
            } else {
                hasMore = false
                emptyList()
            }
        }
}
