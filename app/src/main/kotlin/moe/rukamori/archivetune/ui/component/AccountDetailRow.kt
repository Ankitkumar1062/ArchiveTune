/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * One label/value row for the provider account detail dialogs (Tidal, Qobuz).
 *
 * Replaces the hand-built Text + Spacer(height) stacks those dialogs used, which hardcoded their
 * own label typography and 2dp/10dp gaps in every copy. Material 3's ListItem already defines the
 * overline/headline pairing and vertical rhythm for exactly this, so alignment stays consistent
 * with the rest of the settings UI and with the platform's density and font-scale handling.
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * @param value shown as the headline; blank values are the caller's responsibility to filter.
 * @param valueColor overrides the headline colour, used to tint status lines.
 * @param monospace set for opaque credentials (tokens, IDs) so digits align and glyphs stay legible.
 */
@Composable
fun AccountDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false,
) {
    ListItem(
        modifier = modifier.fillMaxWidth(),
        // The dialog already paints its own container; an opaque row would draw a seam over it.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
        },
    )
}
