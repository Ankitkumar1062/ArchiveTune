/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeResolutionTest {

    @Test
    fun testHikaruNaraResolution() = runBlocking {
        val result = IsrcResolver.resolve(
            mediaId = null,
            title = "Hikaru Nara",
            artists = listOf("Goose house"),
            durationMs = 255_000L,
            isExplicit = false,
        )
        println(">>> HIKARU NARA (Goose house) RESOLVED: $result")
        assertNotNull(result?.isrc)
    }

    @Test
    fun testRenaiCirculationResolution() = runBlocking {
        val result = IsrcResolver.resolve(
            mediaId = null,
            title = "Renai Circulation",
            artists = listOf("MONOGATARI Series", "Kana Hanazawa"),
            durationMs = 256_000L,
            isExplicit = false,
        )
        println(">>> RENAI CIRCULATION RESOLVED: $result")
        assertNotNull(result?.isrc)
    }

    @Test
    fun testExplicitTraps() = runBlocking {
        // 1. STAY (The Kid LAROI & Justin Bieber) -> Explicit master
        val stayResult = IsrcResolver.resolve(
            mediaId = null,
            title = "STAY",
            artists = listOf("The Kid LAROI", "Justin Bieber"),
            durationMs = 141_000L,
            isExplicit = true,
        )
        println(">>> STAY RESOLVED: $stayResult")
        assertNotNull(stayResult?.isrc)

        // 2. HUMBLE. (Kendrick Lamar) -> Must avoid clean radio edit USUM71703150
        val humbleResult = IsrcResolver.resolve(
            mediaId = null,
            title = "HUMBLE.",
            artists = listOf("Kendrick Lamar"),
            durationMs = 177_000L,
            isExplicit = true,
        )
        println(">>> HUMBLE. RESOLVED: $humbleResult")
        assertEquals("USUM71703085", humbleResult?.isrc)

        // 3. INDUSTRY BABY (Lil Nas X & Jack Harlow) -> Must avoid clean edit USSM12104540
        val industryResult = IsrcResolver.resolve(
            mediaId = null,
            title = "INDUSTRY BABY",
            artists = listOf("Lil Nas X", "Jack Harlow"),
            durationMs = 212_000L,
            isExplicit = true,
        )
        println(">>> INDUSTRY BABY RESOLVED: $industryResult")
        assertEquals("USSM12104539", industryResult?.isrc)
    }

    @Test
    fun testMultilingualResolution() = runBlocking {
        val testCases = listOf(
            Triple("Shinzou wo Sasageyo!", listOf("Linked Horizon"), 341_000L),
            Triple("Lemon", listOf("Kenshi Yonezu"), 256_000L),
            Triple("Ditto", listOf("NewJeans"), 186_000L),
            Triple("Kesariya", listOf("Arijit Singh", "Pritam"), 268_000L),
        )

        for ((title, artists, durationMs) in testCases) {
            println("\n=======================================================")
            println(">>> TESTING QUERY: \"$title\" by ${artists.joinToString(", ")} ($durationMs ms)")
            val result = IsrcResolver.resolve(
                mediaId = null,
                title = title,
                artists = artists,
                durationMs = durationMs,
                isExplicit = false,
            )
            println(">>> RESOLVED: $result")
            assertNotNull("Expected ISRC for $title", result?.isrc)
        }
    }
}
