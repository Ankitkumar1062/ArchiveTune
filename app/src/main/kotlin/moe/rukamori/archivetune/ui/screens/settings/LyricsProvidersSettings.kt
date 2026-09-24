/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableBetterLyricsPortatoKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnableMusixmatchExperimentalKey
import moe.rukamori.archivetune.constants.EnableTidalLyricsKey
import moe.rukamori.archivetune.constants.EnableDeezerLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.PrioritizeWordSyncedLyricsKey
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ContentSettingsViewModel
import androidx.compose.foundation.layout.asPaddingValues

/**
 * Lyrics providers sub-page (Task 2): houses every lyrics-provider toggle plus the
 * Musixmatch experimental section that used to live inline on the Lyrics settings page.
 *
 * Behaviour preserved verbatim from the original inline groups:
 *   • All provider switches default to on (except Musixmatch experimental).
 *   • Paxsenix sub-toggles (Apple Music / NetEase / Spotify / Musixmatch / YouTube) only
 *     render when the parent Paxsenix toggle is on, and include the Paxsenix stats entry.
 *   • "Set first lyrics provider" opens the reorderable dialog. The dialog itself lives
 *     in LyricsSettings.kt and is `internal` so this screen can reuse it.
 */
@Composable
fun LyricsProvidersSettings(
    navController: NavController,
    viewModel: ContentSettingsViewModel = hiltViewModel(),
    scrollTo: String? = null,
) {
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) =
        rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enableBetterLyricsPortato, onEnableBetterLyricsPortatoChange) =
        rememberPreference(key = EnableBetterLyricsPortatoKey, defaultValue = true)
    val (enableYouLyPlusLyrics, onEnableYouLyPlusLyricsChange) =
        rememberPreference(key = EnableYouLyPlusLyricsKey, defaultValue = true)
    val (enableUnisonLyrics, onEnableUnisonLyricsChange) =
        rememberPreference(key = EnableUnisonLyricsKey, defaultValue = true)
    val (enableTidalLyrics, onEnableTidalLyricsChange) =
        rememberPreference(key = EnableTidalLyricsKey, defaultValue = true)
    val (enableDeezerLyrics, onEnableDeezerLyricsChange) =
        rememberPreference(key = EnableDeezerLyricsKey, defaultValue = true)
    val (prioritizeWordSynced, onPrioritizeWordSyncedChange) =
        rememberPreference(key = PrioritizeWordSyncedLyricsKey, defaultValue = false)
    val (enableMusixmatchExperimental, onEnableMusixmatchExperimentalChange) =
        rememberPreference(key = EnableMusixmatchExperimentalKey, defaultValue = false)
    val (providerOrderStr, onProviderOrderStrChange) =
        rememberPreference(key = LyricsProviderOrderKey, defaultValue = "")
    val providerOrder =
        remember(providerOrderStr) {
            deserializeLyricsProviderOrder(providerOrderStr)
        }
    var showProviderOrderDialog by remember { mutableStateOf(false) }

    if (showProviderOrderDialog) {
        LyricsProviderOrderDialog(
            initialOrder = providerOrder,
            onDismiss = { showProviderOrderDialog = false },
            onConfirm = { newOrder ->
                onProviderOrderStrChange(newOrder.joinToString(",") { it.name })
                showProviderOrderDialog = false
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.providers)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val playerAwareBottomPadding =
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()
        val scrollState = rememberScrollState()
        val positions = rememberPreferencePositions()
        androidx.compose.runtime.LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }
        Column(
            Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal,
                    ),
                )
                // Chained before verticalScroll so it measures the viewport, not the scrolling content.
                .then(positions.containerModifier())
                .verticalScroll(scrollState)
                .padding(bottom = playerAwareBottomPadding + SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.providers)) {
                // "Prioritize Word Synced Lyrics" sits at the TOP of the providers
                // group because when it's ON it overrides every other toggle and the
                // Lyrics Priority order below — the app queries only BetterLyrics,
                // BetterLyrics Portato, YouLyPlus, and Unison directly. Putting it
                // first makes the override relationship visually obvious.
                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("prioritize_word_synced_lyrics"),
                        title = { Text(stringResource(R.string.prioritize_word_synced_lyrics)) },
                        description = stringResource(R.string.prioritize_word_synced_lyrics_desc),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = prioritizeWordSynced,
                        onCheckedChange = onPrioritizeWordSyncedChange,
                    )
                }

                // When "Prioritize Word Synced Lyrics" is ON, the per-provider
                // toggles and Lyrics Priority order are ignored by LyricsHelper
                // (the four word-sync-capable providers are queried directly).
                // We grey them out here to signal that they have no effect while
                // the override is active. They remain visible (not hidden) so the
                // user can still see their state and understand what will resume
                // when the override is turned back off.
                val providerTogglesEnabled = !prioritizeWordSynced

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_betterlyrics", "betterlyrics"),
                        title = { Text(stringResource(R.string.enable_betterlyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableBetterLyrics,
                        onCheckedChange = onEnableBetterLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_betterlyrics_portato", "betterlyrics_portato"),
                        title = { Text(stringResource(R.string.enable_betterlyrics_portato)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableBetterLyricsPortato,
                        onCheckedChange = onEnableBetterLyricsPortatoChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_youlyplus_lyrics", "youlyplus_lyrics"),
                        title = { Text(stringResource(R.string.enable_youlyplus_lyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableYouLyPlusLyrics,
                        onCheckedChange = onEnableYouLyPlusLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_lrclib", "lrclib"),
                        title = { Text(stringResource(R.string.enable_lrclib)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableLrclib,
                        onCheckedChange = onEnableLrclibChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_kugou", "kugou"),
                        title = { Text(stringResource(R.string.enable_kugou)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableKugou,
                        onCheckedChange = onEnableKugouChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_unison_lyrics", "unison_lyrics"),
                        title = { Text(stringResource(R.string.enable_unison_lyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableUnisonLyrics,
                        onCheckedChange = onEnableUnisonLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }



                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_tidal_lyrics"),
                        title = { Text(stringResource(R.string.enable_tidal_lyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableTidalLyrics,
                        onCheckedChange = onEnableTidalLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_deezer_lyrics"),
                        title = { Text(stringResource(R.string.enable_deezer_lyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableDeezerLyrics,
                        onCheckedChange = onEnableDeezerLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    PreferenceEntry(
                        modifier = positions.modifierFor("set_first_lyrics_provider", "first_lyrics_provider"),
                        title = { Text(stringResource(R.string.set_first_lyrics_provider)) },
                        description = providerOrder.firstOrNull()?.displayName(),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        onClick = { showProviderOrderDialog = true },
                        isEnabled = providerTogglesEnabled,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.musixmatch_experimental_section)) {
                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_musixmatch_experimental"),
                        title = { Text(stringResource(R.string.enable_musixmatch_experimental)) },
                        description = stringResource(R.string.enable_musixmatch_experimental_desc),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableMusixmatchExperimental,
                        onCheckedChange = onEnableMusixmatchExperimentalChange,
                        isEnabled = !prioritizeWordSynced,
                    )
                }
                item(visible = enableMusixmatchExperimental) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    ) {
                        Text(
                            text = stringResource(R.string.musixmatch_experimental_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
