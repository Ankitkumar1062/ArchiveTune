/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.library.LibrarySyncTarget
import moe.rukamori.archivetune.library.RefreshLibraryUseCase
import moe.rukamori.archivetune.podcast.ObserveSavedPodcastsUseCase
import moe.rukamori.archivetune.podcast.PodcastLibraryScreenState
import moe.rukamori.archivetune.podcast.PodcastLibraryUiState
import javax.inject.Inject

@HiltViewModel
class PodcastLibraryViewModel
    @Inject
    constructor(
        private val observeSavedPodcasts: ObserveSavedPodcastsUseCase,
        refreshUseCase: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshUseCase, LibrarySyncTarget.Podcasts) {
        val screenState =
            observeSavedPodcasts()
                .map { state ->
                    if (state.podcasts.isEmpty()) {
                        PodcastLibraryScreenState.Empty
                    } else {
                        PodcastLibraryScreenState.Success(state)
                    }
                }.catch { emit(PodcastLibraryScreenState.Error(R.string.error)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = PodcastLibraryScreenState.Loading,
                )
    }
