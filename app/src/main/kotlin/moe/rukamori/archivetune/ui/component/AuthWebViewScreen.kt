/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Shared chrome for the provider sign-in WebViews (YouTube, Tidal, Qobuz, Deezer).
 *
 * Visually this mirrors the Spotify login sheet in BackupAndRestore.kt — a short explanatory
 * subtitle above a WebView clipped into a rounded surface — while staying a normal navigation
 * route, because each of these flows persists its session and then calls navigateUp().
 *
 * It also removes three copy-pasted bugs those four screens shared:
 *  - the TopAppBar was a *sibling* of a fillMaxSize() AndroidView, so it painted on top of the page
 *    instead of above it, covering the first ~64dp of every login form;
 *  - the captured WebView lived in a plain `var`, which resets to null on recomposition (the
 *    AndroidView factory only runs once) and never triggers one, leaving BackHandler permanently
 *    disabled. DeezerLoginScreen documented this bug rather than fixing it;
 *  - BackHandler keyed `enabled` off canGoBack(), which flips during in-page navigation without
 *    recomposing, so the flag went stale either way.
 */

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.backToMain

/**
 * Renders [factory]'s WebView inside the shared sign-in chrome.
 *
 * @param title shown in the top app bar.
 * @param subtitle one-line hint under the bar explaining what the user should do.
 * @param onRelease optional teardown, invoked when the AndroidView leaves composition.
 * @param factory builds the WebView. The returned instance is tracked automatically for in-page
 *   back navigation, so callers no longer need their own reference for that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWebViewScreen(
    navController: NavController,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onRelease: ((WebView) -> Unit)? = null,
    factory: (Context) -> WebView,
) {
    // Real state, so assigning from the factory recomposes and enables the BackHandler below.
    var webView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // The bar already consumed the top inset; keep the sides and the mini-player gap.
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large),
                factory = { ctx -> factory(ctx).also { webView = it } },
                onRelease = { released ->
                    onRelease?.invoke(released)
                    if (webView === released) webView = null
                },
            )
        }
    }

    // Enabled purely on presence: canGoBack() is polled at press time because it changes during
    // in-page navigation without recomposing.
    BackHandler(enabled = webView != null) {
        val view = webView
        if (view != null && view.canGoBack()) {
            view.goBack()
        } else {
            navController.navigateUp()
        }
    }
}
