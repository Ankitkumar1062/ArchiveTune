/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.audiosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchingTest {
    @Test
    fun normalizePreservesUnicodeCharacters() {
        val title = "カワキヲアメク"
        val normalized = TrackMatching.normalize(title)
        assertEquals("カワキヲアメク", normalized)
    }

    @Test
    fun matchesJapaneseCandidates() {
        val target = TrackMatching.Target(
            title = "カワキヲアメク",
            artists = listOf("美波"),
            album = "カワキヲアメク",
            durationMs = 251_000L,
        )
        val candidate = TrackMatching.Candidate(
            id = "12345",
            title = "カワキヲアメク",
            artists = listOf("美波"),
            album = "カワキヲアメク",
            durationMs = 250_800L,
        )

        val best = TrackMatching.best(target, listOf(candidate))
        assertNotNull(best)
        assertEquals("12345", best?.id)
    }

    @Test
    fun rejectsMusicBoxCoverEvenWithSameBaseTitle() {
        val target = TrackMatching.Target(
            title = "絆ノ奇跡",
            artists = listOf("MAN WITH A MISSION"),
            album = null,
            durationMs = 210_000L,
        )
        val candidate = TrackMatching.Candidate(
            id = "box-999",
            title = "絆ノ奇跡 (Music Box)",
            artists = listOf("MAN WITH A MISSION"),
            album = null,
            durationMs = 251_000L,
        )

        val best = TrackMatching.best(target, listOf(candidate))
        assertNull(best)
    }

    @Test
    fun isrcMatchBypassesTextVariationWhenDurationMatches() {
        val target = TrackMatching.Target(
            title = "Training Season",
            artists = listOf("Dua Lipa"),
            album = "Radical Optimism",
            durationMs = 209_000L,
            isrc = "GBAHT2301292",
        )
        val candidate = TrackMatching.Candidate(
            id = "tidal-target-id",
            title = "Training Season (Radio Edit)",
            artists = listOf("Dua Lipa"),
            album = "Training Season",
            durationMs = 209_200L,
            isrc = "GBAHT2301292",
        )

        val best = TrackMatching.best(target, listOf(candidate))
        assertNotNull(best)
        assertEquals("tidal-target-id", best?.id)
    }

    @Test
    fun rejectsCandidateWhenDurationDiffersSubstantially() {
        val target = TrackMatching.Target(
            title = "Stay",
            artists = listOf("Rihanna"),
            album = null,
            durationMs = 242_000L,
        )
        val candidate = TrackMatching.Candidate(
            id = "long-mix",
            title = "Stay",
            artists = listOf("Rihanna"),
            album = null,
            durationMs = 300_000L,
        )

        val best = TrackMatching.best(target, listOf(candidate))
        assertNull(best)
    }
}
