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
import moe.rukamori.archivetune.paxsenix.models.PaxsenixStats
import moe.rukamori.archivetune.paxsenix.models.ProviderStats
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableBetterLyricsKey
import moe.rukamori.archivetune.constants.EnableBetterLyricsPortatoKey
import moe.rukamori.archivetune.constants.EnableKugouKey
import moe.rukamori.archivetune.constants.EnableLrcLibKey
import moe.rukamori.archivetune.constants.EnableMegalobizLyricsKey
import moe.rukamori.archivetune.constants.EnableMusixmatchExperimentalKey
import moe.rukamori.archivetune.constants.EnablePaxsenixAppleMusicLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixMusixmatchLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixNeteaseLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixSpotifyLyricsKey
import moe.rukamori.archivetune.constants.EnablePaxsenixYouTubeLyricsKey
import moe.rukamori.archivetune.constants.PaxsenixApiKeyKey
import moe.rukamori.archivetune.constants.PaxsenixEndpointKey
import moe.rukamori.archivetune.constants.EnableSimpMusicLyricsKey
import moe.rukamori.archivetune.constants.EnableTidalLyricsKey
import moe.rukamori.archivetune.constants.EnableDeezerLyricsKey
import moe.rukamori.archivetune.constants.EnableUnisonLyricsKey
import moe.rukamori.archivetune.constants.EnableYouLyPlusLyricsKey
import moe.rukamori.archivetune.constants.LyricsProviderOrderKey
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.PrioritizeWordSyncedLyricsKey
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.ContentSettingsViewModel
import moe.rukamori.archivetune.viewmodels.PaxsenixEndpointCheckState
import moe.rukamori.archivetune.viewmodels.PaxsenixStatsState
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
    val (enableSimpMusicLyrics, onEnableSimpMusicLyricsChange) =
        rememberPreference(key = EnableSimpMusicLyricsKey, defaultValue = true)
    val (enableMegalobizLyrics, onEnableMegalobizLyricsChange) =
        rememberPreference(key = EnableMegalobizLyricsKey, defaultValue = true)
    val (enablePaxsenixLyrics, onEnablePaxsenixLyricsChange) =
        rememberPreference(key = EnablePaxsenixLyricsKey, defaultValue = true)
    val (enablePaxsenixAppleMusicLyrics, onEnablePaxsenixAppleMusicLyricsChange) =
        rememberPreference(key = EnablePaxsenixAppleMusicLyricsKey, defaultValue = true)
    val (enablePaxsenixNeteaseLyrics, onEnablePaxsenixNeteaseLyricsChange) =
        rememberPreference(key = EnablePaxsenixNeteaseLyricsKey, defaultValue = false)
    val (enablePaxsenixSpotifyLyrics, onEnablePaxsenixSpotifyLyricsChange) =
        rememberPreference(key = EnablePaxsenixSpotifyLyricsKey, defaultValue = false)
    val (enablePaxsenixMusixmatchLyrics, onEnablePaxsenixMusixmatchLyricsChange) =
        rememberPreference(key = EnablePaxsenixMusixmatchLyricsKey, defaultValue = false)
    val (enablePaxsenixYouTubeLyrics, onEnablePaxsenixYouTubeLyricsChange) =
        rememberPreference(key = EnablePaxsenixYouTubeLyricsKey, defaultValue = false)
    val (paxsenixApiKey, onPaxsenixApiKeyChange) =
        rememberPreference(key = PaxsenixApiKeyKey, defaultValue = "")
    val (paxsenixEndpoint, onPaxsenixEndpointChange) =
        rememberPreference(key = PaxsenixEndpointKey, defaultValue = "")
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

    var showPaxsenixStatsDialog by remember { mutableStateOf(false) }
    var showProviderOrderDialog by remember { mutableStateOf(false) }
    var showPaxsenixEndpointCheckDialog by remember { mutableStateOf(false) }

    if (showPaxsenixStatsDialog) {
        val statsState by viewModel.paxsenixStatsState.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.fetchPaxsenixStats()
        }
        PaxsenixStatsDialog(
            state = statsState,
            onDismiss = { showPaxsenixStatsDialog = false },
            onRetry = { viewModel.fetchPaxsenixStats() },
        )
    }

    if (showPaxsenixEndpointCheckDialog) {
        val checkState by viewModel.paxsenixEndpointCheckState.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.checkPaxsenixEndpoints()
        }
        PaxsenixEndpointCheckDialog(
            state = checkState,
            onDismiss = { showPaxsenixEndpointCheckDialog = false },
            onRetry = { viewModel.checkPaxsenixEndpoints() },
        )
    }

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
                        modifier = positions.modifierFor("enable_simpmusic_lyrics", "simpmusic_lyrics"),
                        title = { Text(stringResource(R.string.enable_simpmusic_lyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableSimpMusicLyrics,
                        onCheckedChange = onEnableSimpMusicLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_megalobiz_lyrics", "megalobiz_lyrics"),
                        title = { Text(stringResource(R.string.enable_megalobiz_lyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enableMegalobizLyrics,
                        onCheckedChange = onEnableMegalobizLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item {
                    SwitchPreference(
                        modifier = positions.modifierFor("enable_paxsenix_lyrics", "paxsenix_lyrics"),
                        title = { Text(stringResource(R.string.enable_paxsenix_lyrics)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enablePaxsenixLyrics,
                        onCheckedChange = onEnablePaxsenixLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item(visible = enablePaxsenixLyrics) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("paxsenix_stats"),
                        title = { Text(stringResource(R.string.paxsenix_stats)) },
                        icon = { Icon(painterResource(R.drawable.stats), null) },
                        onClick = { showPaxsenixStatsDialog = true },
                        isEnabled = providerTogglesEnabled,
                    )
                }

                // PaxSenix API key — user-configurable. When set, sent as
                // "Authorization: Bearer <key>" on every Paxsenix API request.
                // When blank, the built-in default key is used.
                item(visible = enablePaxsenixLyrics) {
                    var showApiKeyDialog by remember { mutableStateOf(false) }
                    PreferenceEntry(
                        modifier = positions.modifierFor("paxsenix_api_key"),
                        title = { Text(stringResource(R.string.paxsenix_api_key)) },
                        description = if (paxsenixApiKey.isNotBlank()) {
                            stringResource(R.string.paxsenix_api_key_set)
                        } else {
                            stringResource(R.string.paxsenix_api_key_not_set)
                        },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        onClick = { showApiKeyDialog = true },
                        isEnabled = providerTogglesEnabled,
                    )
                    if (showApiKeyDialog) {
                        TextFieldDialog(
                            onDismiss = { showApiKeyDialog = false },
                            title = { Text(stringResource(R.string.paxsenix_api_key)) },
                            textFieldValue = paxsenixApiKey,
                            onTextFieldValueChange = onPaxsenixApiKeyChange,
                            singleLine = true,
                            // Bound to the stored key, so reopening the dialog rendered a live
                            // credential in cleartext.
                            masked = true,
                            isInputValid = { it.isBlank() || it.length >= 8 },
                        )
                    }
                }

                // PaxSenix endpoint override — user-configurable. When blank,
                // the default (https://lyrics.paxsenix.org/) is used.
                item(visible = enablePaxsenixLyrics) {
                    var showEndpointDialog by remember { mutableStateOf(false) }
                    PreferenceEntry(
                        modifier = positions.modifierFor("paxsenix_endpoint"),
                        title = { Text(stringResource(R.string.paxsenix_endpoint)) },
                        description = paxsenixEndpoint.ifBlank {
                            stringResource(R.string.paxsenix_endpoint_default)
                        },
                        icon = { Icon(painterResource(R.drawable.solar_server_linear), null) },
                        onClick = { showEndpointDialog = true },
                        isEnabled = providerTogglesEnabled,
                    )
                    if (showEndpointDialog) {
                        TextFieldDialog(
                            onDismiss = { showEndpointDialog = false },
                            title = { Text(stringResource(R.string.paxsenix_endpoint)) },
                            placeholder = { Text(stringResource(R.string.paxsenix_endpoint_hint)) },
                            textFieldValue = paxsenixEndpoint,
                            onTextFieldValueChange = onPaxsenixEndpointChange,
                            singleLine = true,
                            isInputValid = {
                                it.isBlank() ||
                                    it.startsWith("http://") ||
                                    it.startsWith("https://")
                            },
                        )
                    }
                }

                // Which per-provider Paxsenix paths the configured endpoint actually serves.
                // Upstream has retired most of them (unconditional 403), which from inside the
                // app is indistinguishable from a wrong endpoint or a bad key — every affected
                // provider simply returns nothing. This makes the difference visible, and it
                // probes whatever endpoint is configured, so a self-hosted instance reports its
                // own coverage rather than the public service's.
                item(visible = enablePaxsenixLyrics) {
                    PreferenceEntry(
                        modifier = positions.modifierFor("paxsenix_check_endpoints"),
                        title = { Text(stringResource(R.string.paxsenix_check_endpoints)) },
                        description = stringResource(R.string.paxsenix_check_endpoints_description),
                        icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                        onClick = { showPaxsenixEndpointCheckDialog = true },
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item(visible = enablePaxsenixLyrics) {
                    SwitchPreference(
                        title = { Text("Paxsenix: Apple Music") },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enablePaxsenixAppleMusicLyrics,
                        onCheckedChange = onEnablePaxsenixAppleMusicLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item(visible = enablePaxsenixLyrics) {
                    SwitchPreference(
                        title = { Text("Paxsenix: NetEase") },
                        description = stringResource(R.string.paxsenix_endpoint_retired),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enablePaxsenixNeteaseLyrics,
                        onCheckedChange = onEnablePaxsenixNeteaseLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item(visible = enablePaxsenixLyrics) {
                    SwitchPreference(
                        title = { Text("Paxsenix: Spotify") },
                        description = stringResource(R.string.paxsenix_endpoint_retired),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enablePaxsenixSpotifyLyrics,
                        onCheckedChange = onEnablePaxsenixSpotifyLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item(visible = enablePaxsenixLyrics) {
                    SwitchPreference(
                        title = { Text("Paxsenix: Musixmatch") },
                        description = stringResource(R.string.paxsenix_endpoint_retired),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enablePaxsenixMusixmatchLyrics,
                        onCheckedChange = onEnablePaxsenixMusixmatchLyricsChange,
                        isEnabled = providerTogglesEnabled,
                    )
                }

                item(visible = enablePaxsenixLyrics) {
                    SwitchPreference(
                        title = { Text("Paxsenix: YouTube") },
                        description = stringResource(R.string.paxsenix_endpoint_retired),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = enablePaxsenixYouTubeLyrics,
                        onCheckedChange = onEnablePaxsenixYouTubeLyricsChange,
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

/**
 * Shows, per Paxsenix provider, whether the configured endpoint still serves its route.
 *
 * The three outcomes are deliberately distinct: a provider can be *available*, *retired
 * upstream* (the service answers 403 for that route no matter what key is sent — turning its
 * toggle on cannot help), or *unreachable* (network/DNS/5xx, which may be temporary). Only the
 * middle case means "stop expecting this provider to ever work on this endpoint".
 */
@Composable
private fun PaxsenixEndpointCheckDialog(
    state: PaxsenixEndpointCheckState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.paxsenix_check_endpoints)) },
        icon = { Icon(painterResource(R.drawable.wifi_proxy), contentDescription = null) },
        buttons = {
            if (state is PaxsenixEndpointCheckState.Success) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        when (state) {
            PaxsenixEndpointCheckState.Idle,
            PaxsenixEndpointCheckState.Running,
            -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                    Text(
                        text = stringResource(R.string.paxsenix_check_running),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is PaxsenixEndpointCheckState.Success -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.results.forEach { result ->
                        val label =
                            when (result.status) {
                                PaxsenixLyrics.PathStatus.AVAILABLE ->
                                    stringResource(R.string.paxsenix_check_result_ok, result.provider)

                                PaxsenixLyrics.PathStatus.RETIRED ->
                                    stringResource(R.string.paxsenix_check_result_retired, result.provider)

                                PaxsenixLyrics.PathStatus.UNREACHABLE ->
                                    stringResource(R.string.paxsenix_check_result_failed, result.provider)
                            }
                        val tint =
                            when (result.status) {
                                PaxsenixLyrics.PathStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
                                PaxsenixLyrics.PathStatus.RETIRED -> MaterialTheme.colorScheme.error
                                PaxsenixLyrics.PathStatus.UNREACHABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        val iconRes =
                            when (result.status) {
                                PaxsenixLyrics.PathStatus.AVAILABLE -> R.drawable.check
                                PaxsenixLyrics.PathStatus.RETIRED -> R.drawable.error
                                PaxsenixLyrics.PathStatus.UNREACHABLE -> R.drawable.info
                            }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                painterResource(iconRes),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tint,
                                )
                                Text(
                                    text = result.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PaxsenixServerStatus { Operational, Degraded, Down }

private fun successRateToStatus(rate: Float): PaxsenixServerStatus =
    when {
        rate >= 90f -> PaxsenixServerStatus.Operational
        rate >= 70f -> PaxsenixServerStatus.Degraded
        else -> PaxsenixServerStatus.Down
    }

private fun formatUptimeSeconds(seconds: Double): String {
    val total = seconds.toLong()
    val days = total / 86400L
    val hours = (total % 86400L) / 3600L
    val minutes = (total % 3600L) / 60L
    return when {
        days > 0L -> "${days}d ${hours}h ${minutes}m"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

@Composable
private fun PaxsenixStatsDialog(
    state: PaxsenixStatsState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.paxsenix_stats)) },
        icon = { Icon(painterResource(R.drawable.stats), contentDescription = null) },
        buttons = {
            if (state is PaxsenixStatsState.Error) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
            } else {
                TextButton(onClick = { uriHandler.openUri("https://lyrics.paxsenix.org/") }) {
                    Text(stringResource(R.string.visit_website))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        when (state) {
            PaxsenixStatsState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            PaxsenixStatsState.Error -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = stringResource(R.string.paxsenix_stats_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is PaxsenixStatsState.Success -> {
                PaxsenixStatsContent(stats = state.stats)
            }
        }
    }
}

@Composable
private fun PaxsenixStatsContent(stats: PaxsenixStats) {
    val overallRate =
        remember(stats.overallSuccessRate) {
            stats.overallSuccessRate.trimEnd('%').toFloatOrNull() ?: 0f
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaxsenixStatusBar(successRate = overallRate)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.uptime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatUptimeSeconds(stats.uptimeSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.total_requests),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stats.totalRequests.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.success_rate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stats.overallSuccessRate,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (stats.providers.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.providers),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.providers.forEach { (name, providerStats) ->
                    key(name) {
                        PaxsenixProviderRow(name = name, providerStats = providerStats)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaxsenixStatusBar(successRate: Float) {
    val status = remember(successRate) { successRateToStatus(successRate) }
    val statusColor =
        when (status) {
            PaxsenixServerStatus.Operational -> Color(0xFF4CAF50)
            PaxsenixServerStatus.Degraded -> Color(0xFFFF9800)
            PaxsenixServerStatus.Down -> MaterialTheme.colorScheme.error
        }
    val statusLabel =
        when (status) {
            PaxsenixServerStatus.Operational -> stringResource(R.string.paxsenix_status_operational)
            PaxsenixServerStatus.Degraded -> stringResource(R.string.paxsenix_status_degraded)
            PaxsenixServerStatus.Down -> stringResource(R.string.paxsenix_status_down)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = "${successRate.toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PaxsenixProviderRow(
    name: String,
    providerStats: ProviderStats,
) {
    val rate =
        remember(providerStats.successRate) {
            providerStats.successRate.trimEnd('%').toFloatOrNull() ?: 0f
        }
    val status = remember(rate) { successRateToStatus(rate) }
    val dotColor =
        when (status) {
            PaxsenixServerStatus.Operational -> Color(0xFF4CAF50)
            PaxsenixServerStatus.Degraded -> Color(0xFFFF9800)
            PaxsenixServerStatus.Down -> MaterialTheme.colorScheme.error
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${providerStats.hits} hits",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = providerStats.successRate,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

