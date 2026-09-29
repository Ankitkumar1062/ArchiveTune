/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.generate.DiscoverRepository
import moe.rukamori.archivetune.generate.GenerateRepository
import moe.rukamori.archivetune.generate.GeneratedTrack
import moe.rukamori.archivetune.generate.INITIAL_BATCH_SIZE
import moe.rukamori.archivetune.generate.PAGE_SIZE
import moe.rukamori.archivetune.utils.PlaylistNamer
import javax.inject.Inject

@Immutable
data class DiscoverUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val tracks: List<GeneratedTrack> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val saveResultMessage: String? = null,
    val endReached: Boolean = false,
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repository: DiscoverRepository,
    private val generateRepository: GenerateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.feed.collect { feed ->
                if (feed.isNotEmpty() || _uiState.value.tracks.isNotEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = if (feed.isNotEmpty()) false else it.isLoading, tracks = feed)
                    }
                }
            }
        }
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, endReached = false) }
            try {
                val cached = repository.allowedCachedFeed()
                val needed = (INITIAL_BATCH_SIZE - cached.size).coerceAtLeast(0)
                if (needed > 0) repository.nextBatch(needed)
                val feed = repository.getCachedFeed()
                _uiState.update { it.copy(isLoading = false, tracks = feed) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load recommendations") }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || _uiState.value.endReached) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val more = repository.nextBatch(PAGE_SIZE)
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        endReached = more.isEmpty(),
                        tracks = repository.getCachedFeed(),
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, endReached = false) }
            repository.reset()
            try {
                repository.nextBatch(INITIAL_BATCH_SIZE)
                _uiState.update { it.copy(isRefreshing = false, tracks = repository.getCachedFeed()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun surpriseMe() {
        viewModelScope.launch {
            repository.reset()
            _uiState.update { it.copy(isLoading = true, endReached = false) }
            try {
                repository.nextBatch(INITIAL_BATCH_SIZE)
                _uiState.update { it.copy(isLoading = false, tracks = repository.getCachedFeed()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveResultMessage = null) }
    }

    fun saveAsPlaylist() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            val signature = generateRepository.discoverSignature(tracks)
            val existing = generateRepository.findByDiscoverSignature(signature)
            if (existing != null) {
                _uiState.update { it.copy(saveResultMessage = "Already saved as \"${existing.name}\"") }
                return@launch
            }
            val titles = generateRepository.titles()
            val title = PlaylistNamer.generateUniqueName(titles)
            val subtitle = PlaylistNamer.subtitleFor("discover")
            generateRepository.save(title, subtitle, "discover", tracks, discoverSignature = signature)
            _uiState.update { it.copy(saveResultMessage = "Saved as \"$title\"") }
        }
    }
}
