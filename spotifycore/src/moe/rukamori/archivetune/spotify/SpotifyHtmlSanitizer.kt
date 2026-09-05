/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

/**
 * Utility for sanitizing HTML markup, anchor tags, and unescaping HTML entities
 * from Spotify metadata (playlist descriptions, home feed descriptions, search
 * subtitles, artist bios).
 */
object SpotifyHtmlSanitizer {
    private val HTML_TAG_PATTERN = Regex("<[^>]+>")
    private val BR_P_TAG_PATTERN = Regex("(?i)<(?:br|/p|/div)\\s*/?>")
    private val MULTI_WHITESPACE_PATTERN = Regex("\\s+")
    private val NUMERIC_ENTITY_PATTERN = Regex("&#(?:x([0-9a-fA-F]+)|(\\d+));")

    private val NAMED_ENTITIES =
        mapOf(
            "&amp;" to "&",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&#39;" to "'",
            "&#x27;" to "'",
            "&lt;" to "<",
            "&gt;" to ">",
            "&nbsp;" to " ",
            "&copy;" to "©",
            "&reg;" to "®",
            "&trade;" to "™",
            "&hellip;" to "…",
            "&ndash;" to "–",
            "&mdash;" to "—",
            "&bull;" to "•",
            "&lsquo;" to "‘",
            "&rsquo;" to "’",
            "&ldquo;" to "“",
            "&rdquo;" to "”",
        )

    /**
     * Strips HTML tags, decodes HTML entities, and normalizes whitespace.
     * Returns `null` if the cleaned string is null or blank.
     */
    fun clean(text: String?): String? {
        if (text == null || text.isBlank()) return null

        // 1. Replace break and paragraph ending tags with a single space
        var result = text.replace(BR_P_TAG_PATTERN, " ")

        // 2. Strip all remaining HTML tags (<a href=...>, </a>, <b>, <i>, etc.)
        result = result.replace(HTML_TAG_PATTERN, "")

        // 3. Decode named HTML entities
        for ((entity, replacement) in NAMED_ENTITIES) {
            if (result.contains(entity, ignoreCase = true)) {
                result = result.replace(entity, replacement, ignoreCase = true)
            }
        }

        // 4. Decode numeric HTML entities (&#1234; or &#xABCD;)
        result =
            NUMERIC_ENTITY_PATTERN.replace(result) { matchResult ->
                try {
                    val hex = matchResult.groups[1]?.value
                    val dec = matchResult.groups[2]?.value
                    val codePoint =
                        when {
                            hex != null -> hex.toInt(16)
                            dec != null -> dec.toInt(10)
                            else -> return@replace matchResult.value
                        }
                    if (Character.isValidCodePoint(codePoint)) {
                        String(Character.toChars(codePoint))
                    } else {
                        matchResult.value
                    }
                } catch (_: Exception) {
                    matchResult.value
                }
            }

        // 5. Normalize whitespace and trim
        result = result.replace(MULTI_WHITESPACE_PATTERN, " ").trim()

        return result.ifBlank { null }
    }
}
