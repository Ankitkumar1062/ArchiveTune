/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.podcast.LoadPodcastContinuationUseCase
import moe.rukamori.archivetune.podcast.LoadPodcastUseCase
import moe.rukamori.archivetune.podcast.ObservePodcastLibraryMembershipUseCase
import moe.rukamori.archivetune.podcast.PodcastAction
import moe.rukamori.archivetune.podcast.PodcastEpisodeUiModel
import moe.rukamori.archivetune.podcast.PodcastEvent
import moe.rukamori.archivetune.podcast.PodcastPlaybackRequest
import moe.rukamori.archivetune.podcast.PodcastScreenState
import moe.rukamori.archivetune.podcast.PodcastUiState
import moe.rukamori.archivetune.podcast.ToggleEpisodeLibraryUseCase
import moe.rukamori.archivetune.podcast.TogglePodcastSaveUseCase
import javax.inject.Inject

private const val KEY_BROWSE_ID = "browseId"

@HiltViewModel
class PodcastViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val loadPodcast: LoadPodcastUseCase,
        private val loadContinuation: LoadPodcastContinuationUseCase,
        private val observeLibraryMembership: ObservePodcastLibraryMembershipUseCase,
        private val togglePodcastSave: TogglePodcastSaveUseCase,
        private val toggleEpisodeLibrary: ToggleEpisodeLibraryUseCase,
    ) : ViewModel() {
        private val browseId: String = checkNotNull(savedStateHandle[KEY_BROWSE_ID])

        // Mutable source-of-truth for episode list + continuation token
        private val _episodes = MutableStateFlow<ImmutableList<PodcastEpisodeUiModel>>(ImmutableList.of())
        private val _continuation = MutableStateFlow<String?>(null)
        private val _isLoadingMore = MutableStateFlow(false)
        private val _isInitialLoading = MutableStateFlow(true)
        private val _baseState = MutableStateFlow<PodcastScreenState>(PodcastScreenState.Loading)

        private val _events = MutableSharedFlow<PodcastEvent>(extraBufferCapacity = 8)
        val events = _events.asSharedFlow()

        val screenState = _baseState.asStateFlow()

        private var membershipJob: Job? = null

        init {
            loadPodcast()
        }

        fun onAction(action: PodcastAction) {
            when (action) {
                PodcastAction.Retry -> loadPodcast()
                PodcastAction.LoadMore -> loadMore()
                PodcastAction.PlayAll -> playAll()
                PodcastAction.TogglePodcastSave -> toggleSave()
                is PodcastAction.PlayEpisode -> playEpisode(action.episodeId)
                is PodcastAction.ToggleEpisodeLibrary -> toggleEpisode(action.episodeId)
            }
        }

        private fun loadPodcast() {
            viewModelScope.launch {
                _baseState.value = PodcastScreenState.Loading
                val result = loadPodcast(browseId)
                result.fold(
                    onSuccess = { loadResult ->
                        _episodes.value = loadResult.uiState.episodes
                        _continuation.value = loadResult.continuation
                        _isLoadingMore.value = false
                        observeMembership(
                            browseId = loadResult.uiState.browseId,
                            episodes = loadResult.uiState.episodes,
                            base = loadResult.uiState,
                        )
                    },
                    onFailure = {
                        _baseState.value = PodcastScreenState.Error(R.string.podcast_load_error)
                    },
                )
            }
        }

        private fun observeMembership(
            browseId: String,
            episodes: ImmutableList<PodcastEpisodeUiModel>,
            base: PodcastUiState,
        ) {
            membershipJob?.cancel()
            membershipJob =
                combine(
                    observeLibraryMembership(browseId, episodes.map { it.id }),
                    _episodes,
                    _continuation,
                    _isLoadingMore,
                ) { membership, currentEpisodes, continuation, isLoadingMore ->
                    val updatedEpisodes =
                        ImmutableList.copyOf(
                            currentEpisodes.map { episode ->
                                episode.copy(isInLibrary = episode.id in membership.episodeIds)
                            },
                        )
                    PodcastScreenState.Success(
                        base.copy(
                            isSaved = membership.isPodcastSaved,
                            isSavePending = false,
                            episodes = updatedEpisodes,
                            isLoadingMore = isLoadingMore,
                            canLoadMore = continuation != null,
                        ),
                    )
                }.onEach { _baseState.value = it }
                    .launchIn(viewModelScope)
        }

        private fun loadMore() {
            val cont = _continuation.value ?: return
            if (_isLoadingMore.value) return
            val currentState = _baseState.value
            if (currentState !is PodcastScreenState.Success) return

            viewModelScope.launch {
                _isLoadingMore.value = true
                val result =
                    loadContinuation(
                        continuation = cont,
                        podcastTitle = currentState.uiState.title,
                        podcastBrowseId = currentState.uiState.browseId,
                    )
                result.fold(
                    onSuccess = { contResult ->
                        _episodes.value =
                            ImmutableList.copyOf(_episodes.value + contResult.episodes)
                        _continuation.value = contResult.continuation
                        _isLoadingMore.value = false
                    },
                    onFailure = {
                        _isLoadingMore.value = false
                        _events.tryEmit(PodcastEvent.ShowMessage(R.string.error))
                    },
                )
            }
        }

        private fun playAll() {
            val state = _baseState.value
            if (state !is PodcastScreenState.Success) return
            _events.tryEmit(
                PodcastEvent.Play(
                    PodcastPlaybackRequest(
                        title = state.uiState.title,
                        items = state.uiState.episodes.map { it.playbackMetadata }.let { ImmutableList.copyOf(it) },
                        startIndex = 0,
                    ),
                ),
            )
        }

        private fun playEpisode(episodeId: String) {
            val state = _baseState.value
            if (state !is PodcastScreenState.Success) return
            val idx = state.uiState.episodes.indexOfFirst { it.id == episodeId }
            if (idx < 0) return
            _events.tryEmit(
                PodcastEvent.Play(
                    PodcastPlaybackRequest(
                        title = state.uiState.title,
                        items = state.uiState.episodes.map { it.playbackMetadata }.let { ImmutableList.copyOf(it) },
                        startIndex = idx,
                    ),
                ),
            )
        }

        private fun toggleSave() {
            val state = _baseState.value
            if (state !is PodcastScreenState.Success || state.uiState.isSavePending) return
            val saving = !state.uiState.isSaved
            _baseState.value = PodcastScreenState.Success(state.uiState.copy(isSavePending = true))

            viewModelScope.launch {
                togglePodcastSave(browseId, saving).fold(
                    onSuccess = {
                        _events.tryEmit(
                            PodcastEvent.ShowMessage(
                                if (saving) R.string.podcast_saved else R.string.podcast_unsaved,
                            ),
                        )
                    },
                    onFailure = {
                        _baseState.value = PodcastScreenState.Success(state.uiState.copy(isSavePending = false))
                        _events.tryEmit(PodcastEvent.ShowMessage(R.string.podcast_save_error))
                    },
                )
            }
        }

        private fun toggleEpisode(episodeId: String) {
            val state = _baseState.value
            if (state !is PodcastScreenState.Success) return
            val episode = state.uiState.episodes.firstOrNull { it.id == episodeId } ?: return
            if (episode.isLibraryPending) return

            val addingToLibrary = !episode.isInLibrary
            _baseState.value =
                PodcastScreenState.Success(
                    state.uiState.copy(
                        episodes =
                            ImmutableList.copyOf(
                                state.uiState.episodes.map {
                                    if (it.id == episodeId) it.copy(isLibraryPending = true) else it
                                },
                            ),
                    ),
                )

            viewModelScope.launch {
                toggleEpisodeLibrary(episode.playbackMetadata, addingToLibrary).fold(
                    onSuccess = {
                        _events.tryEmit(
                            PodcastEvent.ShowMessage(
                                if (addingToLibrary) R.string.episode_saved else R.string.episode_unsaved,
                            ),
                        )
                    },
                    onFailure = {
                        // Revert optimistic pending flag
                        val current = _baseState.value
                        if (current is PodcastScreenState.Success) {
                            _baseState.value =
                                PodcastScreenState.Success(
                                    current.uiState.copy(
                                        episodes =
                                            ImmutableList.copyOf(
                                                current.uiState.episodes.map {
                                                    if (it.id == episodeId) it.copy(isLibraryPending = false) else it
                                                },
                                            ),
                                    ),
                                )
                        }
                        _events.tryEmit(PodcastEvent.ShowMessage(R.string.episode_library_error))
                    },
                )
            }
        }
    }
