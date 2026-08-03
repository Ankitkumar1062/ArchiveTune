/*
 * ArchiveTune (2026)
 * Mhsm stub: :core already provides `object MoriCipherRuntime` with only the three
 * synchronous cipher helpers. The proprietary module additionally exposes a runtime
 * snapshot flow and an async refresh; those are supplied here as extensions so the
 * app compiles and degrades gracefully (refresh always fails -> NewPipe JS fallback).
 *
 * These extensions live in :app (same compilation unit as callers) so Kotlin can
 * resolve them without cross-module extension-function visibility issues.
 */
package moe.rukamori.archivetune.morideobfuscator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val unavailableSnapshot: MutableStateFlow<CipherSnapshot> =
    MutableStateFlow(CipherSnapshot(status = CipherRuntimeStatus.UNINITIALIZED))

/** Always-unavailable runtime snapshot on Mhsm builds. */
val MoriCipherRuntime.snapshot: StateFlow<CipherSnapshot>
    get() = unavailableSnapshot

/** No-op refresh: always fails so callers fall back to the JS player path. */
@Suppress("UNUSED_PARAMETER")
suspend fun MoriCipherRuntime.refresh(force: Boolean): Result<CipherRefreshResult> =
    Result.failure(UnsupportedOperationException("MoriCipherRuntime stub: refresh unavailable"))
