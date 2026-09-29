/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.generate

/**
 * Snapshot of the user's listening signals, hydrated into sets for O(1) lookup.
 * Shared by My Mix, My Recommendations, Discover Feed, and Genre Detail scoring.
 * Cached for 1 hour by [TasteProfileProvider].
 */
data class TasteProfile(
    val topArtistNames: Set<String>,
    val recentArtists: Set<String>,
    val topTags: Set<String>,
    val topTrackKeys: Set<String>,
    val recentTrackKeys: Set<String>,
    val topTracksRaw: List<GeneratedTrack>,
    val recentTracksRaw: List<GeneratedTrack>,
    val topArtistsRaw: List<String>,
    val builtAtMillis: Long,
    val artistAffinity: Map<String, Double> = emptyMap(),
    val ytMusicRecentRaw: List<GeneratedTrack> = emptyList(),
    val ytMusicLikedRaw: List<GeneratedTrack> = emptyList(),
    val ytMusicFeedRaw: List<GeneratedTrack> = emptyList(),
    val hasPersonalSignals: Boolean = false,
)
