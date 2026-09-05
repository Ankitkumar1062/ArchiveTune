/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyRadioStation(
    val uri: String? = null,
    val title: String? = null,
    val tracks: List<SpotifyRadioTrack> = emptyList(),
    @SerialName("next_page_url") val nextPageUrl: String? = null,
)

@Serializable
data class SpotifyRadioTrack(
    val uri: String = "",
    val original_uri: String? = null,
    val metadata: SpotifyRadioMetadata? = null,
) {
    val id: String get() = uri.removePrefix("spotify:track:").removePrefix("spotify:")
}

@Serializable
data class SpotifyRadioMetadata(
    val title: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("album_title") val albumTitle: String? = null,
    val duration: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)
