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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import moe.rukamori.archivetune.generate.GenerateRepository
import moe.rukamori.archivetune.generate.GeneratedTrack
import moe.rukamori.archivetune.generate.GenerationStatus
import moe.rukamori.archivetune.generate.RECOMMENDATION_TRACK_COUNT
import moe.rukamori.archivetune.generate.youtubeVideoIdOrNull
import moe.rukamori.archivetune.utils.PlaylistNamer
import javax.inject.Inject

private const val GENERATION_TIMEOUT_MS = 60_000L

enum class GenerateMode(val label: String, val description: String, val storageValue: String) {
    TOP("Top Tracks", "Your most played tracks of all time", "top"),
    RECENT("Recent Tracks", "What you've been listening to lately", "recent"),
    SIMILAR_TRACKS("Song Radio", "YouTube Music radio from any song", "similar-tracks"),
    SIMILAR_ARTISTS("Similar Artists", "YouTube-first artist discovery", "similar-artists"),
    TAG("By Tag / Genre", "YouTube-first genre picks", "tag"),
    MIX("My Mix", "Your taste, mixes & local favorites", "mix"),
    RECOMMENDATIONS("My Recommendation", "35 YouTube-first discoveries", "recommendations"),
    NEVER_HEARD("Never Heard", "Fresh discoveries you've never listened to before", "never-heard"),
    LIBRARY("My Library", "Re-discover the sounds of your past", "library"),
}

val GENERATE_PERIODS = listOf(
    "overall" to "All Time",
    "12month" to "12 Months",
    "6month" to "6 Months",
    "3month" to "3 Months",
    "1month" to "1 Month",
    "7day" to "7 Days",
)

val GENRE_QUICK_CHIPS = listOf("pop", "rock", "hip-hop", "electronic", "jazz", "lofi", "metal", "indie", "classical", "r&b", "ambient", "punk")

@Immutable
data class GenerateUiState(
    val selectedMode: GenerateMode? = null,
    val trackCount: Int = 25,
    val period: String = "overall",
    val seedTrackName: String = "",
    val seedArtistName: String = "",
    val seedVideoId: String? = null,
    val seedArtistQuery: String = "",
    val tagInput: String = "",
    val seedTrackResults: List<GeneratedTrack> = emptyList(),
    val seedArtistResults: List<String> = emptyList(),
    val isSearchingSeed: Boolean = false,
    val isGenerating: Boolean = false,
    val loadingMessage: String = "",
    val error: String? = null,
)

sealed interface GenerateNavEvent {
    data class NavigateToPlaylist(val playlistId: String) : GenerateNavEvent
}

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository: GenerateRepository,
    private val generationStatus: GenerationStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<GenerateNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<GenerateNavEvent> = _navEvents

    val lastSavedPlaylistId = MutableStateFlow<String?>(null)

    fun selectMode(mode: GenerateMode) {
        if (_uiState.value.isGenerating) return
        _uiState.update {
            if (it.selectedMode == mode) it.copy(selectedMode = null)
            else it.copy(selectedMode = mode, error = null, seedTrackResults = emptyList(), seedArtistResults = emptyList())
        }
    }

    fun setTrackCount(value: Int) {
        if (!_uiState.value.isGenerating) {
            _uiState.update { it.copy(trackCount = value.coerceIn(5, 35)) }
        }
    }

    fun setPeriod(value: String) = _uiState.update { it.copy(period = value) }
    fun setTagInput(value: String) = _uiState.update { it.copy(tagInput = value) }
    fun setSeedArtistQuery(value: String) = _uiState.update { it.copy(seedArtistQuery = value) }
    fun setSeedTrackName(value: String) = _uiState.update { it.copy(seedTrackName = value, seedVideoId = null) }
    fun setSeedArtistName(value: String) = _uiState.update { it.copy(seedArtistName = value, seedVideoId = null) }

    fun loadTopTracksForSeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingSeed = true) }
            try {
                val results = repository.topTracksForSeed()
                _uiState.update { it.copy(isSearchingSeed = false, seedTrackResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingSeed = false, error = e.message) }
            }
        }
    }

    fun loadTopArtistsForSeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingSeed = true) }
            try {
                val results = repository.topArtistsForSeed()
                _uiState.update { it.copy(isSearchingSeed = false, seedArtistResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingSeed = false, error = e.message) }
            }
        }
    }

    fun searchSeedTrack() {
        val state = _uiState.value
        if (state.seedTrackName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingSeed = true) }
            try {
                val results = repository.searchTracks(state.seedTrackName, state.seedArtistName.ifBlank { null })
                _uiState.update { it.copy(isSearchingSeed = false, seedTrackResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingSeed = false, error = e.message) }
            }
        }
    }

    fun searchSeedArtist() {
        val state = _uiState.value
        if (state.seedArtistQuery.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingSeed = true) }
            try {
                val results = repository.searchArtists(state.seedArtistQuery)
                _uiState.update { it.copy(isSearchingSeed = false, seedArtistResults = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingSeed = false, error = e.message) }
            }
        }
    }

    fun pickSeedTrack(track: GeneratedTrack) = _uiState.update {
        it.copy(
            seedTrackName = track.name,
            seedArtistName = track.artist,
            seedVideoId = track.youtubeVideoIdOrNull(),
            seedTrackResults = emptyList(),
        )
    }

    fun pickSeedArtist(name: String) = _uiState.update {
        it.copy(seedArtistQuery = name, seedArtistResults = emptyList())
    }

    fun setGenreChip(tag: String) = _uiState.update { it.copy(tagInput = tag) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun generate() {
        val state = _uiState.value
        if (state.isGenerating) return
        val mode = state.selectedMode ?: return

        when (mode) {
            GenerateMode.SIMILAR_TRACKS -> if (state.seedTrackName.isBlank() || state.seedArtistName.isBlank()) {
                _uiState.update { it.copy(error = "Enter a seed track and artist") }; return
            }
            GenerateMode.SIMILAR_ARTISTS -> if (state.seedArtistQuery.isBlank()) {
                _uiState.update { it.copy(error = "Enter a seed artist") }; return
            }
            GenerateMode.TAG -> if (state.tagInput.isBlank()) {
                _uiState.update { it.copy(error = "Enter a genre/tag") }; return
            }
            else -> Unit
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null, loadingMessage = "Analyzing taste profile\u2026") }
            generationStatus.update(isGenerating = true, message = "Analyzing taste profile\u2026")
            try {
                val onProgress: (String) -> Unit = { msg ->
                    _uiState.update { s -> s.copy(loadingMessage = msg) }
                    generationStatus.update(isGenerating = true, message = msg)
                }

                val targetCount = if (mode == GenerateMode.RECOMMENDATIONS) {
                    RECOMMENDATION_TRACK_COUNT
                } else {
                    state.trackCount
                }
                val candidateCount = if (mode == GenerateMode.RECOMMENDATIONS) {
                    targetCount
                } else {
                    (targetCount + maxOf(10, targetCount / 2)).coerceAtMost(60)
                }

                val raw: List<GeneratedTrack> = withTimeout(GENERATION_TIMEOUT_MS) {
                    when (mode) {
                        GenerateMode.TOP -> repository.fetchTopTracks(candidateCount, state.period)
                        GenerateMode.LIBRARY -> repository.fetchLibraryMix(candidateCount, state.period)
                        GenerateMode.RECENT -> repository.fetchRecentTracks(candidateCount)
                        GenerateMode.SIMILAR_TRACKS -> repository.fetchSimilarTracks(
                            track = state.seedTrackName,
                            artist = state.seedArtistName,
                            limit = candidateCount,
                            seedVideoId = state.seedVideoId,
                        )
                        GenerateMode.SIMILAR_ARTISTS -> repository.fetchSimilarArtistTracks(state.seedArtistQuery, candidateCount)
                        GenerateMode.TAG -> repository.fetchTagTracks(state.tagInput, candidateCount)
                        GenerateMode.MIX -> repository.fetchMix(candidateCount, onProgress)
                        GenerateMode.RECOMMENDATIONS -> repository.fetchRecommendations(targetCount, onProgress)
                        GenerateMode.NEVER_HEARD -> repository.fetchNeverHeardTracks(candidateCount, onProgress)
                    }
                }

                onProgress("Preparing tracks\u2026")
                val preparedTracks = repository.precheck(raw)
                val finalTracks = if (mode == GenerateMode.NEVER_HEARD) {
                    preparedTracks.take(targetCount)
                } else {
                    repository.preferPlaylistFreshness(
                        tracks = preparedTracks,
                        limit = targetCount,
                        savedKeys = repository.savedPlaylistTrackKeys(),
                    )
                }

                if (finalTracks.isEmpty()) {
                    val message = when {
                        mode == GenerateMode.SIMILAR_TRACKS && state.seedTrackName.isNotBlank() ->
                            "No similar songs found to mix for \"${state.seedTrackName}\"."
                        mode == GenerateMode.SIMILAR_ARTISTS && state.seedArtistQuery.isNotBlank() ->
                            "No similar artists found for \"${state.seedArtistQuery}\"."
                        mode == GenerateMode.TAG && state.tagInput.isNotBlank() ->
                            "No songs found for tag \"${state.tagInput}\"."
                        else -> "No songs found to create this mix."
                    }
                    throw IllegalStateException(message)
                }

                onProgress("Saving playlist\u2026")
                val existingTitles = repository.titles()
                val title = PlaylistNamer.generateUniqueName(existingTitles)
                val subtitle = PlaylistNamer.subtitleFor(
                    mode = mode.storageValue,
                    tagInput = state.tagInput,
                    seedTrackName = state.seedTrackName,
                    seedArtistInput = state.seedArtistQuery,
                )
                val saved = repository.save(title, subtitle, mode.storageValue, finalTracks)
                lastSavedPlaylistId.value = saved.id
                _navEvents.tryEmit(GenerateNavEvent.NavigateToPlaylist(saved.id))
            } catch (_: TimeoutCancellationException) {
                _uiState.update { it.copy(error = "Playlist generation timed out. Please try again.") }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Couldn't generate a playlist") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
                generationStatus.update(isGenerating = false)
            }
        }
    }
}
