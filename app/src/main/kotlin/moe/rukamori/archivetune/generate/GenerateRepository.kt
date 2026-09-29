/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.generate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

const val RECOMMENDATION_TRACK_COUNT = 35

@Singleton
class GenerateRepository @Inject constructor(
    private val database: MusicDatabase,
    private val localTasteEngine: LocalTasteSuggestionEngine,
    private val tasteProfileProvider: TasteProfileProvider,
) {
    suspend fun titles(): List<String> = withContext(Dispatchers.IO) {
        try {
            database.getAllPlaylistNames()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun topTracksForSeed(): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 86_400_000L
        val songs: List<Song> = try {
            database.mostPlayedSongs(fromTimeStamp = thirtyDaysAgo, limit = 20).first()
                .ifEmpty { database.allSongs().first().take(20) }
        } catch (_: Exception) {
            emptyList()
        }
        songs.map { it.toGeneratedTrack() }
    }

    suspend fun topArtistsForSeed(): List<String> = withContext(Dispatchers.IO) {
        val profile = tasteProfileProvider.get()
        profile.topArtistsRaw.take(15)
    }

    suspend fun searchTracks(name: String, artist: String?): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        val query = if (artist.isNullOrBlank()) name else "$name $artist"
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        result?.items.orEmpty().filterIsInstance<SongItem>().map { it.toGeneratedTrack() }
    }

    suspend fun searchArtists(query: String): List<String> = withContext(Dispatchers.IO) {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
        result?.items.orEmpty().filterIsInstance<ArtistItem>().map { it.title }
    }

    suspend fun fetchTopTracks(limit: Int, period: String): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val since = when (period) {
            "7day" -> now - 7L * 86_400_000L
            "1month" -> now - 30L * 86_400_000L
            "3month" -> now - 90L * 86_400_000L
            "6month" -> now - 180L * 86_400_000L
            "12month" -> now - 365L * 86_400_000L
            else -> 0L // "overall"
        }
        val songs: List<Song> = try {
            database.mostPlayedSongs(fromTimeStamp = since, limit = limit).first()
                .ifEmpty { database.allSongs().first().take(limit) }
        } catch (_: Exception) {
            emptyList()
        }
        songs.map { it.toGeneratedTrack() }.take(limit)
    }

    suspend fun fetchRecentTracks(limit: Int): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 86_400_000L
        val songs: List<Song> = try {
            database.mostPlayedSongs(fromTimeStamp = sevenDaysAgo, limit = limit).first()
                .ifEmpty { database.allSongs().first().take(limit) }
        } catch (_: Exception) {
            emptyList()
        }
        songs.map { it.toGeneratedTrack() }.take(limit)
    }

    suspend fun fetchSimilarTracks(
        track: String,
        artist: String,
        limit: Int,
        seedVideoId: String? = null,
    ): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        var videoId = seedVideoId
        if (videoId.isNullOrBlank()) {
            val search = YouTube.search("$track $artist", YouTube.SearchFilter.FILTER_SONG).getOrNull()
            videoId = search?.items?.filterIsInstance<SongItem>()?.firstOrNull()?.id
        }
        if (videoId.isNullOrBlank()) return@withContext emptyList()

        val nextResult = runCatching {
            YouTube.next(WatchEndpoint(videoId = videoId), followAutomixPreview = true).getOrNull()
        }.getOrNull()

        val related = nextResult?.items.orEmpty().map { it.toGeneratedTrack() }
        deduplicate(related).take(limit)
    }

    suspend fun fetchSimilarArtistTracks(artistQuery: String, limit: Int): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        val search = YouTube.search(artistQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        val songs = search?.items.orEmpty().filterIsInstance<SongItem>().map { it.toGeneratedTrack() }
        if (songs.isNotEmpty()) {
            val seedId = songs.firstOrNull()?.videoId ?: songs.firstOrNull()?.youtubeVideoIdOrNull()
            if (seedId != null) {
                val next = runCatching {
                    YouTube.next(WatchEndpoint(videoId = seedId), followAutomixPreview = true).getOrNull()
                }.getOrNull()
                val related = next?.items.orEmpty().map { it.toGeneratedTrack() }
                return@withContext deduplicate(songs + related).take(limit)
            }
        }
        deduplicate(songs).take(limit)
    }

    suspend fun fetchTagTracks(tag: String, limit: Int): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        val search = YouTube.search("$tag music", YouTube.SearchFilter.FILTER_SONG).getOrNull()
        val songs = search?.items.orEmpty().filterIsInstance<SongItem>().map { it.toGeneratedTrack() }
        deduplicate(songs).take(limit)
    }

    suspend fun fetchMix(limit: Int, onProgress: (String) -> Unit): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        onProgress("Analyzing playback patterns\u2026")
        val suggestions = localTasteEngine.run(limit * 2)
        onProgress("Curating personalized mix\u2026")
        val top = fetchTopTracks(limit / 2, "1month")
        deduplicate(suggestions + top).take(limit)
    }

    suspend fun fetchRecommendations(limit: Int, onProgress: (String) -> Unit): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        onProgress("Gathering taste signals\u2026")
        val tasteProfile = tasteProfileProvider.get()
        onProgress("Discovering fresh tracks\u2026")
        val localTracks = localTasteEngine.run(limit * 2)
        val combined = mutableListOf<GeneratedTrack>()
        combined.addAll(localTracks)
        if (tasteProfile.topArtistsRaw.isNotEmpty()) {
            for (artist in tasteProfile.topArtistsRaw.take(3)) {
                if (combined.size >= limit * 2) break
                val artistTracks = fetchSimilarArtistTracks(artist, 10)
                combined.addAll(artistTracks)
            }
        }
        deduplicate(combined).take(limit)
    }

    suspend fun fetchNeverHeardTracks(limit: Int, onProgress: (String) -> Unit): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        onProgress("Querying library to exclude played tracks\u2026")
        val localSongs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())
        val localKeys = localSongs.map { "${it.song.title}|${it.artists.joinToString { a -> a.name }}".lowercase() }.toSet()
        val localIds = localSongs.map { it.song.id }.toSet()

        onProgress("Finding untouched discoveries\u2026")
        val candidates = localTasteEngine.run(limit * 3)
        val filtered = candidates.filter {
            it.key !in localKeys && it.videoId !in localIds && (it.youtubeVideoIdOrNull() !in localIds)
        }
        deduplicate(filtered).take(limit)
    }

    suspend fun fetchLibraryMix(limit: Int, period: String): List<GeneratedTrack> = withContext(Dispatchers.IO) {
        val songs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())
        songs.shuffled().map { it.toGeneratedTrack() }.take(limit)
    }

    suspend fun savedPlaylistTrackKeys(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            database.allSongs().first().map { it.song.id }.toSet()
        }.getOrDefault(emptySet())
    }

    fun deduplicate(tracks: List<GeneratedTrack>): List<GeneratedTrack> {
        val seenKeys = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()
        val result = mutableListOf<GeneratedTrack>()
        for (track in tracks) {
            val key = track.key
            val normArtist = track.artist.trim().lowercase()
            val count = artistCounts.getOrDefault(normArtist, 0)
            if (key !in seenKeys && count < 3 && track.name.isNotBlank()) {
                seenKeys.add(key)
                artistCounts[normArtist] = count + 1
                result.add(track)
            }
        }
        return result
    }

    fun precheck(tracks: List<GeneratedTrack>): List<GeneratedTrack> = deduplicate(tracks)

    fun preferPlaylistFreshness(
        tracks: List<GeneratedTrack>,
        limit: Int,
        savedKeys: Set<String>,
    ): List<GeneratedTrack> {
        val unsaved = tracks.filter { it.videoId !in savedKeys && (it.youtubeVideoIdOrNull() !in savedKeys) }
        val saved = tracks.filter { it.videoId in savedKeys || (it.youtubeVideoIdOrNull() in savedKeys) }
        return (unsaved + saved).take(limit)
    }

    fun filterRecommendationExclusions(tracks: List<GeneratedTrack>): List<GeneratedTrack> = tracks

    fun discoverSignature(tracks: List<GeneratedTrack>): String =
        tracks.take(20).joinToString(";") { it.key }

    suspend fun findByDiscoverSignature(signature: String): PlaylistEntity? = withContext(Dispatchers.IO) {
        val targetBrowseId = "discover:$signature"
        runCatching {
            database.playlistEntityByBrowseId(targetBrowseId)
        }.getOrNull()
    }

    suspend fun save(
        title: String,
        subtitle: String,
        mode: String,
        tracks: List<GeneratedTrack>,
        discoverSignature: String? = null,
    ): PlaylistEntity = withContext(Dispatchers.IO) {
        val playlist = PlaylistEntity(
            name = title,
            browseId = discoverSignature?.let { "discover:$it" },
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
        )
        database.withTransaction {
            insert(playlist)
            tracks.forEachIndexed { index, track ->
                val songId = track.videoId ?: track.youtubeVideoIdOrNull() ?: "gen_${System.currentTimeMillis()}_$index"
                insert(
                    SongEntity(
                        id = songId,
                        title = track.name,
                        thumbnailUrl = track.artworkUrl,
                    ),
                )
                val artistId = ArtistEntity.generateArtistId()
                insert(
                    ArtistEntity(
                        id = artistId,
                        name = track.artist,
                    ),
                )
                insert(
                    SongArtistMap(
                        songId = songId,
                        artistId = artistId,
                        position = 0,
                    ),
                )
                insert(
                    PlaylistSongMap(
                        playlistId = playlist.id,
                        songId = songId,
                        position = index,
                    ),
                )
            }
        }
        playlist
    }
}
