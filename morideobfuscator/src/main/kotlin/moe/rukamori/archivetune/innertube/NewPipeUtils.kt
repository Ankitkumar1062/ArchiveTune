package moe.rukamori.archivetune.innertube

import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.models.YouTubeClient

/**
 * Stub: NewPipeUtils -- provided by morideobfuscator on private builds.
 * Always returns failure so YTPlayerUtils falls back to the built-in JS path.
 */
object NewPipeUtils {
    suspend fun getSignatureTimestamp(videoId: String): Result<Int> =
        Result.failure(UnsupportedOperationException("NewPipeUtils stub"))

    suspend fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient?,
        authState: PlaybackAuthState,
    ): Result<String> =
        Result.failure(UnsupportedOperationException("NewPipeUtils stub"))
}
