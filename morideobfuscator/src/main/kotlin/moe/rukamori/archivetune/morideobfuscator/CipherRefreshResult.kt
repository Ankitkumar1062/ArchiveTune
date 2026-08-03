package moe.rukamori.archivetune.morideobfuscator

/**
 * Stub: result of a cipher refresh. On Mhsm the refresh always fails, so this is
 * only ever constructed by the (absent) proprietary implementation.
 */
data class CipherRefreshResult(
    val refreshedAtMillis: Long,
    val playerId: String? = null,
)
