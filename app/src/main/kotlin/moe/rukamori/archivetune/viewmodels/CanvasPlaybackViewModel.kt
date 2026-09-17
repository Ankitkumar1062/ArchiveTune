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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasPlaybackRequest
import moe.rukamori.archivetune.canvas.CanvasPlaybackUseCase
import moe.rukamori.archivetune.canvas.CanvasVideo
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface CanvasPlaybackState {
    data object Loading : CanvasPlaybackState
    data class Success(val request: CanvasPlaybackRequest, val video: CanvasVideo) : CanvasPlaybackState
    data object Empty : CanvasPlaybackState
    data class Error(val messageRes: Int) : CanvasPlaybackState
}

private data class CanvasPlaybackParams(
    val request: CanvasPlaybackRequest?,
    val policy: moe.rukamori.archivetune.canvas.CanvasPolicy,
    val revision: Long,
    val spotifyConnected: Boolean,
)

@HiltViewModel
class CanvasPlaybackViewModel @Inject constructor(
    private val useCase: CanvasPlaybackUseCase,
) : ViewModel() {
    private val request = MutableStateFlow<CanvasPlaybackRequest?>(null)
    private val mutableState = MutableStateFlow<CanvasPlaybackState>(CanvasPlaybackState.Empty)
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(request, useCase.policy, useCase.revision, useCase.spotifyConnected) { req, pol, rev, conn ->
                CanvasPlaybackParams(req, pol, rev, conn)
            }
            .distinctUntilChanged { old, new ->
                old.request?.mediaId == new.request?.mediaId &&
                    old.request?.requireVertical == new.request?.requireVertical &&
                    old.policy.ready == new.policy.ready &&
                    old.policy.configuration == new.policy.configuration &&
                    old.policy.networkAllowed == new.policy.networkAllowed &&
                    old.revision == new.revision &&
                    old.spotifyConnected == new.spotifyConnected
            }
            .collectLatest { params ->
                val request = params.request
                val policy = params.policy
                if (request == null) {
                    mutableState.value = CanvasPlaybackState.Empty
                    return@collectLatest
                }
                if (!policy.ready) {
                    // Policy is still initializing; wait for next ready emission rather than dumping to Empty
                    return@collectLatest
                }
                if (!policy.configuration.enabled) {
                    mutableState.value = CanvasPlaybackState.Empty
                    return@collectLatest
                }
                val current = mutableState.value
                if (current is CanvasPlaybackState.Success &&
                    current.request.mediaId == request.mediaId &&
                    current.request.requireVertical == request.requireVertical &&
                    params.revision == 0L
                ) {
                    return@collectLatest
                }

                mutableState.value = CanvasPlaybackState.Loading
                try {
                    val video = useCase.load(request, policy)
                    mutableState.value = if (video == null) {
                        CanvasPlaybackState.Empty
                    } else {
                        CanvasPlaybackState.Success(request, video)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.w(error, "Canvas artwork resolution failed")
                    mutableState.value = CanvasPlaybackState.Error(R.string.canvas_refetch_failed)
                }
            }
        }
    }

    fun setRequest(value: CanvasPlaybackRequest?) {
        request.value = value
    }
}
