/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.generate

import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_SUGGESTIONS = 50
private const val PLAY_WEIGHT = 2.0f
private const val LIKED_BONUS = 5.0f
private const val RECENCY_BONUS = 1.5f
private const val TIME_OF_DAY_BONUS = 1.3f
private val MORNING = 6..11
private val AFTERNOON = 12..17
private val EVENING = 18..21

@Singleton
class LocalTasteSuggestionEngine @Inject constructor(
    private val database: MusicDatabase,
) {
    private fun currentTimeOfDay(): String = when (LocalTime.now().hour) {
        in MORNING -> "morning"
        in AFTERNOON -> "afternoon"
        in EVENING -> "evening"
        else -> "night"
    }

    private fun isJunkTitle(titleLower: String): Boolean {
        val keywords = listOf(
            "mashup", "mash up", "mash-up", "jukebox", "mega mix", "megamix",
            "non stop", "nonstop", "audio jukebox", "full album", "full songs", "compilation",
            "slowed + reverb", "slowed reverb", "slowed & reverb", "bass boosted", "8d audio",
            "karaoke", "ringtone", "instrumental", "1 hour", "10 hour", "clean version", "sped up",
        )
        return keywords.any { titleLower.contains(it) }
    }

    /**
     * Builds the local "For You" suggestion list based on real user playback history
     * from ArchiveTune's Database, then seeds YouTube Music's related songs.
     */
    suspend fun run(total: Int = MAX_SUGGESTIONS): List<GeneratedTrack> {
        val timeOfDay = currentTimeOfDay()
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 86_400_000L

        val recentSongs: List<Song> = try {
            database.mostPlayedSongs(fromTimeStamp = thirtyDaysAgo, limit = 100).first()
                .ifEmpty { database.allSongs().first().take(50) }
        } catch (_: Exception) {
            emptyList()
        }

        val likedPlaylist = try {
            database.getPlaylistById(PlaylistEntity.LIKED_PLAYLIST_ID)
        } catch (_: Exception) {
            null
        }

        val seedVideoIds = mutableListOf<String>()
        for (song in recentSongs.take(8)) {
            val vid = song.song.id
            if (vid.isNotBlank() && vid !in seedVideoIds) {
                seedVideoIds.add(vid)
            }
            if (seedVideoIds.size >= 5) break
        }

        if (seedVideoIds.isEmpty()) {
            return recentSongs.map { it.toGeneratedTrack() }.take(total)
        }

        val suggestions = mutableListOf<GeneratedTrack>()
        val seenKeys = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()

        for (seedVideoId in seedVideoIds) {
            if (suggestions.size >= total) break
            val related = runCatching {
                val nextResult = YouTube.next(
                    endpoint = WatchEndpoint(videoId = seedVideoId),
                    followAutomixPreview = true,
                ).getOrNull()
                nextResult?.items.orEmpty()
            }.getOrDefault(emptyList())

            val filtered = related
                .map { it.toGeneratedTrack() }
                .filter { track ->
                    val normArtist = track.artist.trim().lowercase()
                    val count = artistCounts.getOrDefault(normArtist, 0)
                    track.key !in seenKeys &&
                        count < 3 &&
                        !isJunkTitle(track.name.lowercase())
                }
                .shuffled()
                .take(10)

            for (track in filtered) {
                suggestions.add(track)
                seenKeys.add(track.key)
                val normArtist = track.artist.trim().lowercase()
                artistCounts[normArtist] = (artistCounts[normArtist] ?: 0) + 1
            }
        }

        // If suggestions are fewer than requested, fill with recent library favorites
        if (suggestions.size < total && recentSongs.isNotEmpty()) {
            for (song in recentSongs.shuffled()) {
                if (suggestions.size >= total) break
                val track = song.toGeneratedTrack()
                if (track.key !in seenKeys) {
                    suggestions.add(track)
                    seenKeys.add(track.key)
                }
            }
        }

        return suggestions.take(total)
    }
}
