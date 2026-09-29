/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.generate

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class GenerationProgress(
    val isGenerating: Boolean = false,
    val message: String = "",
)

@Singleton
class GenerationStatus @Inject constructor() {
    private val _state = MutableStateFlow(GenerationProgress())
    val state: StateFlow<GenerationProgress> = _state.asStateFlow()

    fun update(isGenerating: Boolean, message: String = "") {
        _state.value = GenerationProgress(isGenerating, message)
    }
}
