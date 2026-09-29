/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.generate

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import javax.inject.Inject
import javax.inject.Singleton

private const val TASTE_PROFILE_TTL_MILLIS = 60L * 60 * 1000

@Singleton
class TasteProfileProvider @Inject constructor(
    private val database: MusicDatabase,
) {
    private val mutex = Mutex()
    private var cached: TasteProfile? = null
    private var lastBuiltMillis: Long = 0L

    suspend fun get(forceRefresh: Boolean = false): TasteProfile = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached != null && now - lastBuiltMillis < TASTE_PROFILE_TTL_MILLIS) {
            return@withLock cached!!
        }

        val thirtyDaysAgo = now - 30L * 86_400_000L
        val topSongs: List<Song> = try {
            database.mostPlayedSongs(fromTimeStamp = thirtyDaysAgo, limit = 50).first()
                .ifEmpty { database.allSongs().first().take(50) }
        } catch (_: Exception) {
            emptyList()
        }

        val topTracksRaw = topSongs.map { it.toGeneratedTrack() }
        val topTrackKeys = topTracksRaw.map { it.key }.toSet()

        val topArtistsRaw = topSongs
            .flatMap { it.artists.map { a -> a.name } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }

        val topArtistNames = topArtistsRaw.map { it.trim().lowercase() }.toSet()

        val totalPlays = topArtistsRaw.size.toDouble().coerceAtLeast(1.0)
        val artistAffinity = topArtistsRaw.associate { artist ->
            val count = topSongs.count { s -> s.artists.any { it.name.equals(artist, ignoreCase = true) } }
            artist.lowercase() to (count.toDouble() / totalPlays).coerceIn(0.05, 1.0)
        }

        val profile = TasteProfile(
            topArtistNames = topArtistNames,
            recentArtists = topArtistNames.take(15).toSet(),
            topTags = setOf("pop", "rock", "indie", "electronic", "r&b", "hip-hop", "ambient", "jazz"),
            topTrackKeys = topTrackKeys,
            recentTrackKeys = topTrackKeys.take(20).toSet(),
            topTracksRaw = topTracksRaw,
            recentTracksRaw = topTracksRaw.take(20),
            topArtistsRaw = topArtistsRaw,
            builtAtMillis = now,
            artistAffinity = artistAffinity,
            hasPersonalSignals = topTracksRaw.isNotEmpty(),
        )

        cached = profile
        lastBuiltMillis = now
        profile
    }
}
