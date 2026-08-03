/*
 * ArchiveTune (2026)
 * Stub: MoriCipherRuntime -- proprietary module not available on Mhsm.
 * All cipher calls return failure; callers fall back to NewPipe JS-player path.
 */
package moe.rukamori.archivetune.morideobfuscator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MoriCipherRuntime {
    val snapshot: StateFlow<CipherSnapshot> =
        MutableStateFlow(CipherSnapshot(status = CipherRuntimeStatus.UNINITIALIZED))

    fun signatureTimestamp(videoId: String): Result<Int> =
        Result.failure(UnsupportedOperationException("MoriCipherRuntime stub"))

    fun transformNParameter(videoId: String, url: String): Result<String> =
        Result.failure(UnsupportedOperationException("MoriCipherRuntime stub"))

    fun resolveStreamUrl(videoId: String, cipherString: String): Result<String> =
        Result.failure(UnsupportedOperationException("MoriCipherRuntime stub"))

    suspend fun refresh(force: Boolean): Result<CipherRefreshResult> =
        Result.failure(UnsupportedOperationException("MoriCipherRuntime stub"))
}
