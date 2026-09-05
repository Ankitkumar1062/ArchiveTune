/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.artist.ArtistBlockRequest
import moe.rukamori.archivetune.artist.ObserveArtistBlockedUseCase
import moe.rukamori.archivetune.artist.SetArtistBlockedUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.filterExplicitAlbums
import moe.rukamori.archivetune.extensions.filterVideo
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.Album
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.ArtistSection
import moe.rukamori.archivetune.innertube.pages.ArtistSectionLayout
import moe.rukamori.archivetune.spotify.Spotify
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface ArtistBlockState {
    data object Loading : ArtistBlockState

    @Immutable
    data class Success(
        val isBlocked: Boolean,
    ) : ArtistBlockState

    data object Empty : ArtistBlockState

    @Immutable
    data class Error(
        @StringRes val messageRes: Int,
    ) : ArtistBlockState
}

sealed interface ArtistFetchState {
    data object Pending : ArtistFetchState

    data object Success : ArtistFetchState

    data class Failed(val isNotFound: Boolean = false) : ArtistFetchState
}

sealed interface ArtistUiState {
    data object Loading : ArtistUiState

    data object Content : ArtistUiState

    data class Error(val isNotFound: Boolean = false) : ArtistUiState
}

sealed interface ArtistAction {
    data object Share : ArtistAction

    data object CopyLink : ArtistAction

    data object ToggleBlock : ArtistAction
}

sealed interface ArtistEvent {
    @Immutable
    data class Share(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class CopyLink(
        val link: String,
    ) : ArtistEvent

    @Immutable
    data class ShowMessage(
        @StringRes val messageRes: Int,
    ) : ArtistEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        observeArtistBlocked: ObserveArtistBlockedUseCase,
        private val setArtistBlocked: SetArtistBlockedUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val artistId = savedStateHandle.get<String>("artistId")!!
        var artistPage by mutableStateOf<ArtistPage?>(null)
        private val _fetchState = MutableStateFlow<ArtistFetchState>(ArtistFetchState.Pending)
        val fetchState: StateFlow<ArtistFetchState> = _fetchState.asStateFlow()

        private val eventChannel = Channel<ArtistEvent>(capacity = Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private var blockJob: Job? = null

        val libraryArtist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        val blockState =
            observeArtistBlocked(artistId)
                .map { blocked ->
                    if (blocked == null) {
                        ArtistBlockState.Empty
                    } else {
                        ArtistBlockState.Success(isBlocked = blocked)
                    }
                }.catch {
                    emit(ArtistBlockState.Error(R.string.error_unknown))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistBlockState.Loading)
        val librarySongs =
            context.dataStore.data
                .map { preferences ->
                    (preferences[HideExplicitKey] ?: false) to (preferences[HideVideoKey] ?: false)
                }
                .distinctUntilChanged()
                .flatMapLatest { (hideExplicit, hideVideo) ->
                    database.artistSongsByCreateDateAsc(artistId).map {
                        it.filterExplicit(hideExplicit).filterVideo(hideVideo)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        val libraryAlbums =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val uiState: StateFlow<ArtistUiState> =
            combine(
                snapshotFlow { artistPage },
                librarySongs,
                libraryAlbums,
                _fetchState,
            ) { page, songs, albums, fetch ->
                when {
                    page != null || songs.isNotEmpty() || albums.isNotEmpty() -> ArtistUiState.Content
                    fetch is ArtistFetchState.Pending -> ArtistUiState.Loading
                    fetch is ArtistFetchState.Failed -> ArtistUiState.Error(fetch.isNotFound)
                    else -> ArtistUiState.Loading
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState.Loading)

        init {
            viewModelScope.launch {
                context.dataStore.data
                    .map { preferences ->
                        (preferences[HideExplicitKey] ?: false) to (preferences[HideVideoKey] ?: false)
                    }
                    .distinctUntilChanged()
                    .collect {
                        fetchArtistsFromYTM()
                    }
            }
        }

        private fun isSpotifyArtistId(id: String): Boolean {
            val clean = id.removePrefix("spotify:artist:")
            return id.startsWith("spotify:artist:") ||
                (clean.length == 22 && clean.all { it.isLetterOrDigit() } && !id.startsWith("UC") && !id.startsWith("FE"))
        }

        fun retry() {
            fetchArtistsFromYTM()
        }

        fun fetchArtistsFromYTM() {
            viewModelScope.launch {
                _fetchState.value = ArtistFetchState.Pending
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                val blockedArtistIds = database.getBlockedArtistIds().toSet()

                // 1. Direct Spotify artist resolution
                if (isSpotifyArtistId(artistId)) {
                    val cleanId = artistId.removePrefix("spotify:artist:")
                    val spotifyPage =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val overview =
                                    Spotify.artistOverview(cleanId).getOrNull()
                                        ?: run {
                                            // Fallback to basic artist info if overview query fails
                                            val basicArtist = Spotify.artist(cleanId).getOrThrow()
                                            val topTracks =
                                                runCatching { Spotify.artistTopTracks(cleanId).getOrNull()?.tracks }
                                                    .getOrNull().orEmpty()
                                            val related =
                                                runCatching { Spotify.artistRelatedArtists(cleanId).getOrNull() }
                                                    .getOrNull().orEmpty()
                                            val albums =
                                                runCatching { Spotify.artistAlbums(cleanId).getOrNull()?.items }
                                                    .getOrNull().orEmpty()
                                            moe.rukamori.archivetune.spotify.models.SpotifyArtistOverview(
                                                id = cleanId,
                                                name = basicArtist.name,
                                                avatarImageUrl = basicArtist.images.firstOrNull()?.url,
                                                topTracks = topTracks,
                                                albums = albums.filter { it.albumType == "album" },
                                                singles = albums.filter { it.albumType == "single" || it.albumType == "ep" },
                                                relatedArtists = related,
                                            )
                                        }

                                val monthlyListenerText =
                                    overview.monthlyListeners?.let { formatCompactCount(it) }
                                val artistThumbnail =
                                    overview.avatarImageUrl ?: overview.headerImageUrl

                                val artistItem =
                                    ArtistItem(
                                        id = "spotify:artist:$cleanId",
                                        title = overview.name,
                                        thumbnail = artistThumbnail,
                                        monthlyListenerCountText = monthlyListenerText,
                                        shuffleEndpoint = null,
                                        radioEndpoint = null,
                                    )

                                val sections = mutableListOf<ArtistSection>()

                                // 1. Popular / Top Tracks
                                val songItems =
                                    overview.topTracks.map { track ->
                                        SongItem(
                                            id = track.id,
                                            title = track.name,
                                            artists =
                                                track.artists.map {
                                                    Artist(name = it.name, id = "spotify:artist:${it.id}")
                                                },
                                            album =
                                                track.album?.let {
                                                    Album(name = it.name, id = "spotify:album:${it.id}")
                                                },
                                            duration = if (track.durationMs > 0) track.durationMs / 1000 else null,
                                            thumbnail = track.album?.images?.firstOrNull()?.url.orEmpty(),
                                            explicit = track.explicit,
                                        )
                                    }
                                if (songItems.isNotEmpty()) {
                                    sections +=
                                        ArtistSection(
                                            title = context.getString(R.string.popular),
                                            items = songItems,
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.LIST,
                                        )
                                }

                                fun mapToAlbumItems(
                                    albums: List<moe.rukamori.archivetune.spotify.models.SpotifyAlbum>,
                                    releaseType: AlbumReleaseType,
                                ): List<AlbumItem> =
                                    albums.map { alb ->
                                        val year = alb.releaseDate?.take(4)?.toIntOrNull()
                                        AlbumItem(
                                            browseId = "spotify:album:${alb.id}",
                                            playlistId = "spotify:album:${alb.id}",
                                            title = alb.name,
                                            artists =
                                                alb.artists.map {
                                                    Artist(name = it.name, id = "spotify:artist:${it.id}")
                                                },
                                            year = year,
                                            thumbnail = alb.images.firstOrNull()?.url.orEmpty(),
                                            releaseType = releaseType,
                                        )
                                    }

                                // 2. Albums
                                if (overview.albums.isNotEmpty()) {
                                    sections +=
                                        ArtistSection(
                                            title = context.getString(R.string.albums),
                                            items = mapToAlbumItems(overview.albums, AlbumReleaseType.ALBUM),
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.GRID,
                                        )
                                }

                                // 3. Singles & EPs
                                if (overview.singles.isNotEmpty()) {
                                    sections +=
                                        ArtistSection(
                                            title = context.getString(R.string.singles_and_eps),
                                            items = mapToAlbumItems(overview.singles, AlbumReleaseType.SINGLE),
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.GRID,
                                        )
                                }

                                // 4. Appears On
                                if (overview.appearsOn.isNotEmpty()) {
                                    sections +=
                                        ArtistSection(
                                            title = context.getString(R.string.appears_on),
                                            items = mapToAlbumItems(overview.appearsOn, AlbumReleaseType.ALBUM),
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.GRID,
                                        )
                                }

                                // 5. Compilations
                                if (overview.compilations.isNotEmpty()) {
                                    sections +=
                                        ArtistSection(
                                            title = context.getString(R.string.compilations),
                                            items = mapToAlbumItems(overview.compilations, AlbumReleaseType.ALBUM),
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.GRID,
                                        )
                                }

                                // 6. Fans Also Like / Similar Artists
                                if (overview.relatedArtists.isNotEmpty()) {
                                    sections +=
                                        ArtistSection(
                                            title = context.getString(R.string.fans_also_like),
                                            items =
                                                overview.relatedArtists.map { rel ->
                                                    ArtistItem(
                                                        id = "spotify:artist:${rel.id}",
                                                        title = rel.name,
                                                        thumbnail = rel.images.firstOrNull()?.url,
                                                        shuffleEndpoint = null,
                                                        radioEndpoint = null,
                                                    )
                                                },
                                            moreEndpoint = null,
                                            layout = ArtistSectionLayout.GRID,
                                        )
                                }

                                ArtistPage(
                                    artist = artistItem,
                                    sections = sections,
                                    description = overview.biography,
                                )
                            }.getOrNull()
                        }

                    if (spotifyPage != null) {
                        val filteredSections =
                            spotifyPage.sections.map { section ->
                                section.copy(
                                    items =
                                        section.items
                                            .filterExplicit(hideExplicit)
                                            .filterVideo(hideVideo)
                                            .filterBlockedArtists(blockedArtistIds),
                                )
                            }
                        artistPage = spotifyPage.copy(sections = filteredSections)
                        _fetchState.value = ArtistFetchState.Success
                        return@launch
                    }
                }

                // 2. Direct YouTube channel fetch
                var ytResult: Result<ArtistPage>? = null
                if (artistId.startsWith("UC") || artistId.startsWith("FE")) {
                    ytResult = withContext(Dispatchers.IO) { YouTube.artist(artistId) }
                }

                if (ytResult?.isSuccess == true) {
                    val page = ytResult.getOrThrow()
                    val filteredSections =
                        page.sections.map { section ->
                            section.copy(
                                items =
                                    section.items
                                        .filterExplicit(hideExplicit)
                                        .filterVideo(hideVideo)
                                        .filterBlockedArtists(blockedArtistIds),
                            )
                        }

                    artistPage = page.copy(sections = filteredSections)
                    _fetchState.value = ArtistFetchState.Success

                    withContext(Dispatchers.IO) {
                        database.artist(artistId).firstOrNull()?.artist?.let { artistEntity ->
                            database.update(artistEntity, page)
                        }
                    }
                    return@launch
                }

                // 3. Fallback: Search YouTube Music by artist name if direct browse failed or ID is a raw name
                val lookupName =
                    withContext(Dispatchers.IO) {
                        database.artist(artistId).firstOrNull()?.artist?.name
                            ?: libraryArtist.value?.artist?.name
                            ?: artistId.takeIf {
                                !it.startsWith("UC") && !it.startsWith("FE") && it.isNotBlank() && !isSpotifyArtistId(it)
                            }
                    }

                if (!lookupName.isNullOrBlank()) {
                    val searchPage =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val search = YouTube.search(lookupName, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                                val candidate = search?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()
                                if (candidate != null && candidate.id.startsWith("UC") && candidate.id != artistId) {
                                    YouTube.artist(candidate.id).getOrNull()
                                } else {
                                    null
                                }
                            }.getOrNull()
                        }

                    if (searchPage != null) {
                        val filteredSections =
                            searchPage.sections.map { section ->
                                section.copy(
                                    items =
                                        section.items
                                            .filterExplicit(hideExplicit)
                                            .filterVideo(hideVideo)
                                            .filterBlockedArtists(blockedArtistIds),
                                )
                            }

                        artistPage = searchPage.copy(sections = filteredSections)
                        _fetchState.value = ArtistFetchState.Success
                        return@launch
                    }
                }

                // 4. Local Library Fallback: If local library has songs/albums for this artist, mark Success
                val hasLocalContent =
                    withContext(Dispatchers.IO) {
                        database.artistSongsByCreateDateAsc(artistId).firstOrNull()?.isNotEmpty() == true ||
                            database.artistAlbumsPreview(artistId).firstOrNull()?.isNotEmpty() == true
                    }

                if (hasLocalContent) {
                    _fetchState.value = ArtistFetchState.Success
                } else {
                    _fetchState.value = ArtistFetchState.Failed(isNotFound = true)
                }
            }
        }

        fun onAction(action: ArtistAction) {
            when (action) {
                ArtistAction.Share -> eventChannel.trySend(ArtistEvent.Share(artistShareLink()))
                ArtistAction.CopyLink -> eventChannel.trySend(ArtistEvent.CopyLink(artistShareLink()))
                ArtistAction.ToggleBlock -> toggleBlocked()
            }
        }

        private fun toggleBlocked() {
            if (blockJob?.isActive == true) return

            val pageArtist = artistPage?.artist
            val localArtist = libraryArtist.value?.artist
            val artistName = pageArtist?.title ?: localArtist?.name ?: return
            val currentlyBlocked = (blockState.value as? ArtistBlockState.Success)?.isBlocked == true

            blockJob =
                viewModelScope.launch {
                    try {
                        setArtistBlocked(
                            ArtistBlockRequest(
                                id = artistId,
                                name = artistName,
                                channelId = pageArtist?.channelId ?: localArtist?.channelId,
                                thumbnailUrl = pageArtist?.thumbnail ?: localArtist?.thumbnailUrl,
                                blocked = !currentlyBlocked,
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        eventChannel.send(ArtistEvent.ShowMessage(R.string.error_unknown))
                    }
                }
        }

        private fun artistShareLink(): String = artistPage?.artist?.shareLink ?: "https://music.youtube.com/channel/$artistId"
    }
