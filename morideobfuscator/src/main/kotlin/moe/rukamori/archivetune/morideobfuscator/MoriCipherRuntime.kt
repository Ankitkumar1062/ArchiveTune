/*
 * ArchiveTune (2026)
 * Stub: MoriCipherRuntime is not available on the Mhsm public branch.
 * All methods return Result.failure so core falls back to NewPipe extractor paths.
 */
package moe.rukamori.archivetune.morideobfuscator

object MoriCipherRuntime {
    fun signatureTimestamp(videoId: String): Result<Int> =
        Result.failure(UnsupportedOperationException("MoriCipherRuntime stub"))

    fun transformNParameter(videoId: String, url: String): Result<String> =
        Result.failure(UnsupportedOperationException("MoriCipherRuntime stub"))

    fun resolveStreamUrl(videoId: String, cipherString: String): Result<String> =
        Result.failure(UnsupportedOperationException("MoriCipherRuntime stub"))
}
