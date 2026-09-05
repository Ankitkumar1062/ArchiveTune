/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.constants.DefaultMetadataSourceKey
import moe.rukamori.archivetune.constants.MetadataSource
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyPlaybackResolver {
    private const val MIN_MATCH_THRESHOLD = 0.55
    private const val CACHE_MAX_SIZE = 512

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<String, MediaMetadata>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean = size > CACHE_MAX_SIZE
        }

    suspend fun resolveToMediaItem(track: SpotifyTrack): MediaItem? = resolveToMetadata(track)?.toMediaItem()

    suspend fun resolveToMetadata(track: SpotifyTrack): MediaMetadata? =
        withContext(Dispatchers.IO) {
            val metadataSource =
                PreferenceStore
                    .get(DefaultMetadataSourceKey)
                    .toEnum(MetadataSource.YOUTUBE)
            val cacheKey = "${track.id}:${metadataSource.name}"
            mutex.withLock {
                cache[cacheKey]?.let { return@withContext it }
            }

            val youtubeQuery = SpotifyMapper.buildSearchQuery(track)
            // Spotify catalog playback is intentionally resolved through the existing audio-source
            // chain, so identifying the matching YouTube item must also work without a YouTube
            // login. Try anonymous search first; a stale account context must not make Spotify
            // playlist tracks appear unplayable for signed-out users.
            val anonymousResult = YouTube.search(
                query = youtubeQuery,
                filter = YouTube.SearchFilter.FILTER_SONG,
                useAccountContext = false,
            )
            currentCoroutineContext().ensureActive()
            val anonymousCandidates = anonymousResult.getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
            val candidates = anonymousCandidates.ifEmpty {
                val fallback = YouTube.search(query = youtubeQuery, filter = YouTube.SearchFilter.FILTER_SONG)
                currentCoroutineContext().ensureActive()
                fallback.getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
            }.distinctBy { it.id }
            if (candidates.isEmpty()) return@withContext null

            val precomputed =
                mutex.withLock {
                    SpotifyMapper.precompute(
                        title = track.name,
                        artist = track.artists.joinToString(" ") { it.name },
                        durationMs = track.durationMs,
                    )
                }

            val (best, score) =
                mutex.withLock {
                    candidates
                        .map { candidate ->
                            candidate to
                                SpotifyMapper.matchScorePrecomputed(
                                    precomputed = precomputed,
                                    candidateTitle = candidate.title,
                                    candidateArtist = candidate.artists.joinToString(" ") { it.name },
                                    candidateDurationSec = candidate.duration,
                                )
                        }.maxByOrNull { it.second }
                } ?: return@withContext null
            if (score < MIN_MATCH_THRESHOLD) return@withContext null

            val bestMetadata = best.toMediaMetadata()
            val metadata =
                bestMetadata.copy(
                    title = track.name.takeIf(String::isNotBlank) ?: best.title,
                    artists =
                        track.artists
                            .filter { it.name.isNotBlank() }
                            .map { artist ->
                                MediaMetadata.Artist(
                                    id = artist.id,
                                    name = artist.name,
                                )
                            }.ifEmpty { bestMetadata.artists },
                    thumbnailUrl = SpotifyMapper.getTrackThumbnail(track) ?: best.thumbnail,
                    duration = if (track.durationMs > 0) track.durationMs / 1000 else best.duration ?: -1,
                    explicit = track.explicit || best.explicit,
                    album = track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) } ?: bestMetadata.album,
                    spotifyTrackId = track.id.takeIf(String::isNotBlank),
                )

            // Direct 0ms fast-path: Prime IsrcResolver so lossless chain has the verified ISRC instantly
            if (track.isrc?.isNotBlank() == true) {
                moe.rukamori.archivetune.audiosource.IsrcResolver.cacheIsrc(
                    mediaId = best.id,
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                    isrc = track.isrc!!,
                    isExplicit = metadata.explicit,
                    localizedTitle = track.name,
                    localizedArtist = track.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                )
                moe.rukamori.archivetune.audiosource.IsrcResolver.cacheIsrc(
                    mediaId = "spotify:track:${track.id}",
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                    isrc = track.isrc!!,
                    isExplicit = metadata.explicit,
                    localizedTitle = track.name,
                    localizedArtist = track.artists.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
                )
            }

            mutex.withLock {
                cache[cacheKey] = metadata
                cache[track.id] = metadata
                cache["yt:${track.id}"] = metadata
                cache["spotify:track:${track.id}"] = metadata
            }
            metadata
        }

    suspend fun resolveToYouTubeVideoId(
        mediaId: String,
        title: String,
        artists: List<String>,
        durationMs: Long,
        isrc: String? = null,
    ): String? =
        withContext(Dispatchers.IO) {
            val spotifyId = mediaId.removePrefix("spotify:track:").removePrefix("spotify:")
            val cacheKey = "yt:$spotifyId"
            mutex.withLock {
                cache[cacheKey]?.let { return@withContext it.id }
                cache[spotifyId]?.let { return@withContext it.id }
                cache[mediaId]?.let { return@withContext it.id }
            }

            val youtubeQuery = buildString {
                append(title)
                if (artists.isNotEmpty()) {
                    append(" ")
                    append(artists.joinToString(" "))
                }
            }.trim()

            if (youtubeQuery.isBlank()) return@withContext null

            val searchResult =
                YouTube
                    .search(
                        query = youtubeQuery,
                        filter = YouTube.SearchFilter.FILTER_SONG,
                        useAccountContext = false,
                    ).getOrNull()
                    ?: YouTube
                        .search(
                            query = youtubeQuery,
                            filter = YouTube.SearchFilter.FILTER_SONG,
                        ).getOrNull()
                    ?: return@withContext null

            val candidates =
                searchResult.items
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
            if (candidates.isEmpty()) return@withContext null

            val precomputed =
                mutex.withLock {
                    SpotifyMapper.precompute(
                        title = title,
                        artist = artists.joinToString(" "),
                        durationMs = durationMs.toInt(),
                    )
                }

            val (best, score) =
                mutex.withLock {
                    candidates
                        .map { candidate ->
                            candidate to
                                SpotifyMapper.matchScorePrecomputed(
                                    precomputed = precomputed,
                                    candidateTitle = candidate.title,
                                    candidateArtist = candidate.artists.joinToString(" ") { it.name },
                                    candidateDurationSec = candidate.duration,
                                )
                        }.maxByOrNull { it.second }
                } ?: return@withContext null
            if (score < MIN_MATCH_THRESHOLD) return@withContext null

            val bestMetadata = best.toMediaMetadata()
            val metadata =
                bestMetadata.copy(
                    title = title.takeIf(String::isNotBlank) ?: best.title,
                    artists =
                        artists
                            .filter { it.isNotBlank() }
                            .map { artistName ->
                                MediaMetadata.Artist(
                                    id = null,
                                    name = artistName,
                                )
                            }.ifEmpty { bestMetadata.artists },
                    duration = if (durationMs > 0) (durationMs / 1000).toInt() else best.duration ?: -1,
                    spotifyTrackId = spotifyId.takeIf(String::isNotBlank),
                )

            if (!isrc.isNullOrBlank()) {
                moe.rukamori.archivetune.audiosource.IsrcResolver.cacheIsrc(
                    mediaId = best.id,
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                    isrc = isrc,
                    isExplicit = metadata.explicit,
                    localizedTitle = title,
                    localizedArtist = artists.joinToString(", ").takeIf { it.isNotBlank() },
                )
                moe.rukamori.archivetune.audiosource.IsrcResolver.cacheIsrc(
                    mediaId = mediaId,
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                    isrc = isrc,
                    isExplicit = metadata.explicit,
                    localizedTitle = title,
                    localizedArtist = artists.joinToString(", ").takeIf { it.isNotBlank() },
                )
            }

            mutex.withLock {
                cache[cacheKey] = metadata
                cache[spotifyId] = metadata
                cache[mediaId] = metadata
            }
            best.id
        }
}

