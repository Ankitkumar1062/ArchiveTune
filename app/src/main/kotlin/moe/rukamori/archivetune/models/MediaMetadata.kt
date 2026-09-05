/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.models

import androidx.compose.runtime.Immutable
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val spotifyTrackId: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val isMusicVideo: Boolean = false,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    data class Artist(
        val id: String?,
        val name: String,
        val thumbnailUrl: String? = null,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            explicit = explicit,
            isMusicVideo = isMusicVideo,
            liked = liked,
            likedDate = likedDate,
            inLibrary = inLibrary,
        )
}

fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists =
            artists.map {
                MediaMetadata.Artist(
                    id = it.id,
                    name = it.name,
                    thumbnailUrl = it.thumbnailUrl,
                )
            },
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album =
            album?.let {
                MediaMetadata.Album(
                    id = it.id,
                    title = it.title,
                )
            } ?: song.albumId?.let { albumId ->
                MediaMetadata.Album(
                    id = albumId,
                    title = song.albumName.orEmpty(),
                )
            },
        explicit = song.explicit,
        isMusicVideo = song.isMusicVideo,
    )

fun SongItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists =
            artists.map {
                MediaMetadata.Artist(
                    id = it.id,
                    name = it.name,
                    thumbnailUrl = null,
                )
            },
        duration = duration ?: -1,
        thumbnailUrl =
            thumbnail.resize(
                width = 1080,
                height = 1080,
                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
            ),
        album =
            album?.let {
                MediaMetadata.Album(
                    id = it.id,
                    title = it.name,
                )
            },
        explicit = explicit,
        setVideoId = setVideoId,
        isMusicVideo =
            endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType in
                listOf(MUSIC_VIDEO_TYPE_OMV, MUSIC_VIDEO_TYPE_UGC),
    )

fun moe.rukamori.archivetune.spotify.models.SpotifyTrack.toMediaMetadata(): MediaMetadata {
    val durationSec = if (durationMs > 0) durationMs / 1000 else -1
    val artistsList =
        artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
                thumbnailUrl = null,
            )
        }
    val albumMeta =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name,
            )
        }
    val thumb = moe.rukamori.archivetune.spotify.SpotifyMapper.getTrackThumbnail(this)
    val mediaId = "spotify:track:$id"

    if (!isrc.isNullOrBlank()) {
        moe.rukamori.archivetune.audiosource.IsrcResolver.cacheIsrc(
            mediaId = mediaId,
            title = name,
            artists = artistsList.map { it.name },
            isrc = isrc!!,
            isExplicit = explicit,
            localizedTitle = name,
            localizedArtist = artistsList.joinToString(", ") { it.name }.takeIf { it.isNotBlank() },
        )
    }

    return MediaMetadata(
        id = mediaId,
        title = name,
        artists = artistsList,
        duration = durationSec,
        thumbnailUrl = thumb,
        album = albumMeta,
        explicit = explicit,
        spotifyTrackId = id,
    )
}

fun moe.rukamori.archivetune.spotify.models.SpotifyRadioTrack.toMediaMetadata(): MediaMetadata {
    val meta = metadata
    val dur = meta?.duration
    val durationSec =
        when {
            dur == null -> -1
            dur > 10000 -> (dur / 1000).toInt()
            else -> dur.toInt()
        }
    val artistName = meta?.artistName
    val artistsList =
        if (!artistName.isNullOrBlank()) {
            listOf(
                MediaMetadata.Artist(
                    id = null,
                    name = artistName,
                    thumbnailUrl = null,
                ),
            )
        } else {
            emptyList()
        }
    val albumMeta =
        meta?.albumTitle?.takeIf { it.isNotBlank() }?.let {
            MediaMetadata.Album(
                id = "",
                title = it,
            )
        }
    val thumb =
        meta?.imageUrl?.let { rawUrl ->
            if (rawUrl.startsWith("spotify:image:")) {
                val imgId = rawUrl.removePrefix("spotify:image:")
                "https://i.scdn.co/image/$imgId"
            } else {
                rawUrl
            }
        }
    val mediaId = "spotify:track:$id"

    return MediaMetadata(
        id = mediaId,
        title = meta?.title.orEmpty(),
        artists = artistsList,
        duration = durationSec,
        thumbnailUrl = thumb,
        album = albumMeta,
        explicit = false,
        spotifyTrackId = id,
    )
}

