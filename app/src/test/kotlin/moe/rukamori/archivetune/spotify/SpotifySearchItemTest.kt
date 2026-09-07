/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyPaging
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifySearchResult
import moe.rukamori.archivetune.spotify.models.SpotifySimpleArtist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.utils.resize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifySearchItemTest {
    @Test
    fun flattensAndDeduplicatesAllSpotifySearchTypes() {
        val track = SpotifyTrack(id = "track-1", name = "Song")
        val result =
            SpotifySearchResult(
                tracks = SpotifyPaging(items = listOf(track, track), total = 2),
                albums = SpotifyPaging(items = listOf(SpotifyAlbum(id = "album-1", name = "Album"),), total = 1),
                artists = SpotifyPaging(items = listOf(SpotifyArtist(id = "artist-1", name = "Artist")), total = 1),
                playlists = SpotifyPaging(items = listOf(SpotifyPlaylist(id = "playlist-1", name = "Playlist")), total = 1),
            )

        assertEquals(
            listOf("track:track-1", "album:album-1", "artist:artist-1", "playlist:playlist-1"),
            result.toSearchItems().map(SpotifySearchItem::key),
        )
    }

    @Test
    fun mapperUsesSpotifyArtistAndTitleForIdentification() {
        val track =
            SpotifyTrack(
                id = "track-1",
                name = "Long Way Home",
                artists = listOf(SpotifySimpleArtist(name = "Example Artist")),
                durationMs = 201_000,
            )

        assertTrue(
            SpotifyMapper.matchScore(
                spotifyTitle = track.name,
                spotifyArtist = "Example Artist",
                spotifyDurationMs = track.durationMs,
                candidateTitle = "Long Way Home",
                candidateArtist = "Example Artist",
                candidateDurationSec = 201,
            ) > 0.9,
        )
    }

    @Test
    fun toHighResSpotifyUrlUpgradesAllVariantsTo640() {
        // Track/Album medium & small -> high res
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b2731234567890abcdef12345678",
            SpotifyMapper.toHighResSpotifyUrl("https://i.scdn.co/image/ab67616d00001e021234567890abcdef12345678"),
        )
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b2731234567890abcdef12345678",
            SpotifyMapper.toHighResSpotifyUrl("https://i.scdn.co/image/ab67616d000048511234567890abcdef12345678"),
        )

        // Artist avatar medium & small -> high res
        assertEquals(
            "https://i.scdn.co/image/ab6761610000e5eb1234567890abcdef12345678",
            SpotifyMapper.toHighResSpotifyUrl("https://i.scdn.co/image/ab676161000051741234567890abcdef12345678"),
        )

        // Playlist custom image medium & small -> high res
        assertEquals(
            "https://i.scdn.co/image/ab67706c0000da841234567890abcdef12345678",
            SpotifyMapper.toHighResSpotifyUrl("https://i.scdn.co/image/ab67706c0000bebb1234567890abcdef12345678"),
        )

        // Mosaic playlist -> high res 640
        assertEquals(
            "https://mosaic.scdn.co/640/ab67616d0000b2731111ab67616d0000b2732222",
            SpotifyMapper.toHighResSpotifyUrl("https://mosaic.scdn.co/300/ab67616d0000b2731111ab67616d0000b2732222"),
        )
    }

    @Test
    fun largestImageUrlPicksAndUpgradesBestResolution() {
        val images = listOf(
            moe.rukamori.archivetune.spotify.models.SpotifyImage(url = "https://i.scdn.co/image/ab67616d00004851aaaa"),
            moe.rukamori.archivetune.spotify.models.SpotifyImage(url = "https://i.scdn.co/image/ab67616d00001e02aaaa"),
        )
        val result = SpotifyMapper.largestImageUrl(images)
        assertEquals("https://i.scdn.co/image/ab67616d0000b273aaaa", result)
    }

    @Test
    fun stringResizeCorrectlyHandlesSpotifyUrls() {
        val mediumUrl = "https://i.scdn.co/image/ab67616d00001e02aaaa"
        // Hero / full-screen resize (> 320)
        assertEquals(
            "https://i.scdn.co/image/ab67616d0000b273aaaa",
            mediumUrl.resize(width = 1200, height = 1200),
        )
        // Thumbnail list resize (<= 64)
        assertEquals(
            "https://i.scdn.co/image/ab67616d00004851aaaa",
            mediumUrl.resize(width = 48, height = 48),
        )
    }
}
