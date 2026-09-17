/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.podcast

import com.google.common.collect.ImmutableSet
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PodcastEntity
import moe.rukamori.archivetune.innertube.InnerTube
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.SyncUtils
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
    ) {
        private val innerTube = InnerTube()

        suspend fun loadPodcast(browseId: String): Result<PodcastPage> =
            withContext(Dispatchers.IO) {
                try {
                    val normalizedBrowseId =
                        browseId.takeIf { it.startsWith(PODCAST_SHOW_BROWSE_PREFIX) }
                            ?: "$PODCAST_SHOW_BROWSE_PREFIX$browseId"
                    innerTube.cookie = YouTube.cookie
                    val response =
                        innerTube
                            .browse(
                                client = WEB_REMIX,
                                browseId = normalizedBrowseId,
                                setLogin = true,
                            ).body<BrowseResponse>()
                    val page = PodcastPage.fromResponse(response, normalizedBrowseId)
                    persistPodcast(page)
                    Result.success(page)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Result.failure(throwable)
                }
            }

        suspend fun loadContinuation(continuation: String): Result<PodcastPage.Continuation> =
            withContext(Dispatchers.IO) {
                try {
                    innerTube.cookie = YouTube.cookie
                    val response =
                        innerTube
                            .browse(
                                client = WEB_REMIX,
                                continuation = continuation,
                                setLogin = true,
                            ).body<BrowseResponse>()
                    val page = PodcastPage.continuationFromResponse(response)
                    Result.success(page)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Result.failure(throwable)
                }
            }

        fun observeSavedPodcasts(): Flow<List<PodcastEntity>> =
            database.podcasts()

        fun observeLibraryMembership(
            browseId: String,
            episodeIds: List<String>,
        ): Flow<PodcastLibraryMembership> =
            combine(
                database.podcast(browseId),
                database.songsByIds(episodeIds),
            ) { podcast, episodes ->
                PodcastLibraryMembership(
                    isPodcastSaved = podcast?.isSaved == true,
                    episodeIds =
                        ImmutableSet.copyOf(
                            episodes
                                .asSequence()
                                .filter { song -> song.song.inLibrary != null }
                                .map { song -> song.id }
                                .toList(),
                        ),
                )
            }

        suspend fun setPodcastSaved(
            browseId: String,
            save: Boolean,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val current = requireNotNull(database.getPodcast(browseId))
                    val now = LocalDateTime.now()
                    database.withTransaction {
                        val latest = getPodcast(browseId) ?: current
                        upsert(
                            latest.copy(
                                localSavedAt = if (save) now else null,
                                lastUpdateTime = now,
                            ),
                        )
                    }
                    if (!save && current.remoteSavedAt == null) return@withContext Result.success(Unit)
                    val playlistId = current.playlistId?.takeIf(String::isNotBlank) ?: return@withContext Result.success(Unit)
                    syncUtils.requireLibrarySyncEnabled()
                    YouTube.likePlaylist(playlistId, save).getOrThrow()
                    database.withTransaction {
                        val latest = getPodcast(browseId) ?: current
                        upsert(
                            latest.copy(
                                remoteSavedAt = if (save) now else null,
                                lastUpdateTime = now,
                            ),
                        )
                    }
                    Result.success(Unit)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    Result.failure(failure)
                }
            }

        suspend fun setEpisodeInLibrary(
            metadata: MediaMetadata,
            addToLibrary: Boolean,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val requestedSong =
                        database.withTransaction {
                            val now = LocalDateTime.now()
                            val currentSong = getSongById(metadata.id)?.song
                            if (currentSong == null) {
                                insert(metadata) { song ->
                                    song.copy(
                                        liked = addToLibrary,
                                        likedDate = if (addToLibrary) now else null,
                                        inLibrary = if (addToLibrary) now else null,
                                        isPodcast = true,
                                    )
                                }
                                getSongById(metadata.id)?.song ?: error("Song insertion failed")
                            } else {
                                val songToUpdate =
                                    currentSong.copy(
                                        liked = addToLibrary,
                                        likedDate = if (addToLibrary) now else null,
                                        inLibrary = if (addToLibrary) now else null,
                                        isPodcast = true,
                                    )
                                update(songToUpdate)
                                songToUpdate
                            }
                        }
                    syncUtils.likeSong(requestedSong)
                    Result.success(Unit)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    Result.failure(failure)
                }
            }

        private suspend fun persistPodcast(page: PodcastPage) {
            val item = page.podcast
            val now = LocalDateTime.now()
            database.withTransaction {
                val existing = getPodcast(item.browseId)
                upsert(
                    PodcastEntity(
                        browseId = item.browseId,
                        playlistId = item.playlistId,
                        title = item.title,
                        authorName = item.author?.name,
                        authorId = item.author?.id,
                        thumbnailUrl = item.thumbnail,
                        localSavedAt = existing?.localSavedAt,
                        remoteSavedAt = if (page.isSaved) existing?.remoteSavedAt ?: now else null,
                        lastUpdateTime = now,
                    ),
                )
            }
        }
    }
