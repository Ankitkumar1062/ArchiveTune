/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.generate

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import moe.rukamori.archivetune.innertube.models.SongItem

private val YOUTUBE_VIDEO_ID_REGEX = Regex("[A-Za-z0-9_-]{11}")

@Immutable
@Serializable
data class GeneratedTrack(
    val name: String,
    val artist: String,
    val artworkUrl: String? = null,
    val url: String = "",
    val listeners: Long? = null,
    val playcount: Long? = null,
    val match: Double? = null,
    val album: String? = null,
    val videoId: String? = null,
) {
    val key: String get() = "$name|$artist".lowercase()
}

fun SongItem.toGeneratedTrack(): GeneratedTrack =
    GeneratedTrack(
        name = title,
        artist = artists.joinToString(", ") { it.name },
        artworkUrl = thumbnail,
        url = "https://music.youtube.com/watch?v=$id",
        album = album?.name,
        videoId = id,
    )

fun moe.rukamori.archivetune.db.entities.Song.toGeneratedTrack(): GeneratedTrack =
    GeneratedTrack(
        name = song.title,
        artist = artists.joinToString(", ") { it.name },
        artworkUrl = song.thumbnailUrl,
        url = "https://music.youtube.com/watch?v=${song.id}",
        album = album?.title,
        videoId = song.id,
    )

fun GeneratedTrack.youtubeVideoIdOrNull(): String? {
    if (!videoId.isNullOrBlank()) return videoId
    val value = url.trim()
    if (YOUTUBE_VIDEO_ID_REGEX.matches(value)) return value
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    return when {
        host == "youtu.be" -> uri.pathSegments.firstOrNull()
        host == "youtube.com" || host.endsWith(".youtube.com") ->
            uri.getQueryParameter("v")
                ?: uri.pathSegments
                    .takeIf { it.firstOrNull() in setOf("embed", "shorts", "live") }
                    ?.getOrNull(1)
        else -> null
    }
}
