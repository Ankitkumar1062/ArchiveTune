package moe.rukamori.archivetune.morideobfuscator

data class CipherSnapshot(
    val status: CipherRuntimeStatus = CipherRuntimeStatus.UNINITIALIZED,
    val lastSuccessfulRefreshMillis: Long? = null,
    val nextRefreshAtMillis: Long? = null,
    val playerId: String? = null,
    val refreshProgressPercent: Int? = null,
    val lastFailure: Throwable? = null,
)
