/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audiosource

import moe.rukamori.archivetune.constants.AudioSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {
    @Test
    fun rejectsIdenticalTitleByDifferentArtist() {
        val result = evaluate(stream(title = "Stay", artist = "The Kid LAROI", durationMs = 141_000))

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("artist"))
    }

    @Test
    fun acceptsMatchingCatalogMetadata() {
        val result =
            evaluate(
                stream(
                    title = "Stay",
                    artist = "Rihanna feat. Mikky Ekko",
                    album = "Unapologetic",
                    durationMs = 241_500,
                ),
            )

        assertTrue(result.accepted)
    }

    @Test
    fun rejectsWrongVersionEvenWhenArtistAndBaseTitleMatch() {
        val result = evaluate(stream(title = "Stay (Live)", artist = "Rihanna", durationMs = 241_000))

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("version"))
    }

    @Test
    fun rejectsImplausibleDuration() {
        val result = evaluate(stream(title = "Stay", artist = "Rihanna", durationMs = 310_000))

        assertFalse(result.accepted)
        assertTrue(result.reason.contains("duration"))
    }

    @Test
    fun preservesNonLatinTitles() {
        val result =
            TitleMatch.evaluate(
                wantedTitle = "カワキヲアメク",
                wantedArtists = listOf("美波"),
                wantedAlbum = null,
                wantedDurationMs = 251_000,
                stream = stream("カワキヲアメク", "美波", durationMs = 250_000),
            )

        assertTrue(result.accepted)
    }

    @Test
    fun rejectsMissingMatchMetadataInsteadOfAssumingExact() {
        val result = evaluate(stream(title = null, artist = null, durationMs = null))

        assertFalse(result.accepted)
    }

    @Test
    fun fastGate_acceptsExactIsrcMatchEvenWhenTitlesDifferAcrossLanguages() {
        val result =
            TitleMatch.evaluate(
                wantedTitle = "色彩",
                wantedArtists = listOf("yama"),
                wantedAlbum = null,
                wantedDurationMs = 191_000,
                wantedIsrc = "JPES02202685",
                stream = stream(
                    title = "color",
                    artist = "yama",
                    durationMs = 191_500,
                    matchedIsrc = "JPES02202685",
                ),
            )

        assertTrue(result.accepted)
        assertEquals("isrc verified match", result.reason)
    }

    @Test
    fun dualTitleMatch_acceptsLocalizedEnglishTitleWhenJapaneseTitleUsedOnYouTube() {
        val result =
            TitleMatch.evaluate(
                wantedTitle = "色彩",
                wantedArtists = listOf("yama"),
                wantedAlbum = null,
                wantedDurationMs = 191_000,
                localizedTitle = "color",
                stream = stream(
                    title = "color",
                    artist = "yama",
                    durationMs = 191_500,
                ),
            )

        assertTrue(result.accepted)
    }

    private fun evaluate(stream: DirectStream): TitleMatch.Result =
        TitleMatch.evaluate(
            wantedTitle = "Stay",
            wantedArtists = listOf("Rihanna", "Mikky Ekko"),
            wantedAlbum = "Unapologetic",
            wantedDurationMs = 242_000,
            stream = stream,
        )

    private fun stream(
        title: String?,
        artist: String?,
        album: String? = null,
        durationMs: Long?,
        matchedIsrc: String? = null,
    ) = DirectStream(
        uri = "https://example.invalid/audio.flac",
        mimeType = "audio/flac",
        codecs = "flac",
        contentLength = null,
        label = "test",
        source = AudioSourceType.TIDAL,
        matchedTitle = title,
        matchedArtist = artist,
        matchedAlbum = album,
        matchedDurationMs = durationMs,
        matchedIsrc = matchedIsrc,
    )
}
