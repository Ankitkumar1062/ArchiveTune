/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyHtmlSanitizerTest {
    @Test
    fun stripsUnquotedAndQuotedAnchorTags() {
        val input = "<a href=spotify:playlist:37i9dQZF1EIX3vr6UVonYQ>Sabrina Carpenter</a>, <a href=\"spotify:playlist:123\">Dua Lipa</a>"
        val expected = "Sabrina Carpenter, Dua Lipa"
        assertEquals(expected, SpotifyHtmlSanitizer.clean(input))
    }

    @Test
    fun handlesLeadingTextAndMultipleLinks() {
        val input = "Here's some <a href=\"spotify:playlist:37i9dQZF1EIdviGX1GoHom\">chant</a>, <a href=\"spotify:playlist:37i9dQZF1EIf\">j-tracks</a>"
        val expected = "Here's some chant, j-tracks"
        assertEquals(expected, SpotifyHtmlSanitizer.clean(input))
    }

    @Test
    fun decodesNamedAndNumericHtmlEntities() {
        val input = "Rock &amp; Roll &bull; &quot;Hits&#39;&quot; &apos;test&#x27; &#8212; 2026 &copy;"
        val expected = "Rock & Roll • \"Hits'\" 'test' — 2026 ©"
        assertEquals(expected, SpotifyHtmlSanitizer.clean(input))
    }

    @Test
    fun handlesBreakAndParagraphTags() {
        val input = "Line 1<br/>Line 2</p><div>Line 3</div>"
        val expected = "Line 1 Line 2 Line 3"
        assertEquals(expected, SpotifyHtmlSanitizer.clean(input))
    }

    @Test
    fun returnsNullForBlankOrNull() {
        assertNull(SpotifyHtmlSanitizer.clean(null))
        assertNull(SpotifyHtmlSanitizer.clean("   "))
        assertNull(SpotifyHtmlSanitizer.clean("<p></p>"))
        assertNull(SpotifyHtmlSanitizer.clean("<br/> <br />"))
    }
}
