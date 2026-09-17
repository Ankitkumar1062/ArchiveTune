/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.podcast.PodcastLibraryItemUiModel
import moe.rukamori.archivetune.podcast.PodcastLibraryScreenState
import moe.rukamori.archivetune.ui.component.MediaDetailStatePanel
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.viewmodels.LibraryRefreshState
import moe.rukamori.archivetune.viewmodels.PodcastLibraryViewModel

@Composable
fun LibraryPodcastsScreen(
    navController: NavController,
    viewModel: PodcastLibraryViewModel = hiltViewModel(),
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()

    LaunchedEffect(refreshState) {
        if (refreshState is LibraryRefreshState.Success ||
            refreshState is LibraryRefreshState.Error
        ) {
            viewModel.acknowledgeRefreshResult()
        }
    }

    val lazyListState = rememberLazyListState()

    // Pull-to-refresh: detect swipe up from top
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset < -60 && refreshState !is LibraryRefreshState.Refreshing) {
                    viewModel.onRefresh()
                }
            }
    }

    val bottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()

    when (val state = screenState) {
        PodcastLibraryScreenState.Loading -> {
            ShimmerHost(modifier = Modifier.fillMaxSize()) {
                repeat(5) {
                    PodcastLibraryItemShimmer()
                }
            }
        }

        PodcastLibraryScreenState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MediaDetailStatePanel(
                    title = stringResource(R.string.no_podcasts),
                    description = stringResource(R.string.no_podcasts_description),
                    iconRes = R.drawable.podcast,
                )
            }
        }

        is PodcastLibraryScreenState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MediaDetailStatePanel(
                    title = stringResource(R.string.error),
                    description = stringResource(state.messageResId),
                    actionLabel = stringResource(R.string.refresh),
                    onAction = viewModel::onRefresh,
                )
            }
        }

        is PodcastLibraryScreenState.Success -> {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = LibraryHeaderContentPadding,
                        bottom = bottomPadding.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = state.uiState.podcasts,
                    key = { podcast -> podcast.browseId },
                ) { podcast ->
                    PodcastLibraryItem(
                        podcast = podcast,
                        onClick = {
                            navController.navigate("podcast/${podcast.browseId}")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastLibraryItem(
    podcast: PodcastLibraryItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = podcast.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
            placeholder = painterResource(R.drawable.podcast),
            error = painterResource(R.drawable.podcast),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (podcast.author != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = podcast.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (podcast.isSavedLocally || podcast.isSavedRemotely) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PodcastLibraryItemShimmer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.75f).height(16.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
    }
}
