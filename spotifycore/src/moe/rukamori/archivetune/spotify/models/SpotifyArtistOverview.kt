/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyArtistOverview(
    val id: String = "",
    val name: String = "",
    val uri: String? = null,
    val avatarImageUrl: String? = null,
    val headerImageUrl: String? = null,
    val biography: String? = null,
    val monthlyListeners: Long? = null,
    val worldRank: Int? = null,
    val topTracks: List<SpotifyTrack> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val singles: List<SpotifyAlbum> = emptyList(),
    val appearsOn: List<SpotifyAlbum> = emptyList(),
    val compilations: List<SpotifyAlbum> = emptyList(),
    val relatedArtists: List<SpotifyArtist> = emptyList(),
)
