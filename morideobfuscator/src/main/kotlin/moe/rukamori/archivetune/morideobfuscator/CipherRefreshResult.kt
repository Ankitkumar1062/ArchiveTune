package moe.rukamori.archivetune.morideobfuscator

sealed class CipherRefreshResult {
    data class Success(val playerId: String) : CipherRefreshResult()
    data class Failure(val cause: Throwable) : CipherRefreshResult()
}
