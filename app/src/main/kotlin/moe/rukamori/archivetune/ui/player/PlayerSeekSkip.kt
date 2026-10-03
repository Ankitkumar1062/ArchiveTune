/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.SeekExtraSeconds
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.rememberPreference

/** The step of the rewind and fast-forward buttons: the same 5 seconds the artwork double-tap skips. */
internal const val SeekSkipStepMs = 5_000L

private const val SeekSkipRepeatWindowMs = 1_000L

internal val SeekSkipRewindIcon: ImageVector get() = Icons.Rounded.Replay5
internal val SeekSkipForwardIcon: ImageVector get() = Icons.Rounded.Forward5

/**
 * Skips the current track back or forward by [SeekSkipStepMs], growing the step on rapid repeats
 * when "Progressive seek" is on — the same rule as the artwork double-tap.
 */
@Stable
internal class SeekSkip(
    private val playerConnection: PlayerConnection,
    private val progressive: () -> Boolean,
    val rewindDescription: String,
    val forwardDescription: String,
) {
    private var lastSkipAt = 0L
    private var multiplier = 1

    fun rewind() = skip(forward = false)

    fun forward() = skip(forward = true)

    private fun skip(forward: Boolean) {
        val now = SystemClock.uptimeMillis()
        multiplier = if (progressive() && now - lastSkipAt < SeekSkipRepeatWindowMs) multiplier + 1 else 1
        lastSkipAt = now

        val player = playerConnection.player
        val step = SeekSkipStepMs * multiplier
        val target = player.currentPosition + if (forward) step else -step
        val duration = player.duration
        player.seekTo(
            if (duration == C.TIME_UNSET) target.coerceAtLeast(0L) else target.coerceIn(0L, duration),
        )
        playerConnection.service.forceDiscordSync("seek_button_skip")
    }
}

@Composable
internal fun rememberSeekSkip(playerConnection: PlayerConnection): SeekSkip {
    val (progressive) = rememberPreference(SeekExtraSeconds, defaultValue = false)
    val progressiveState = rememberUpdatedState(progressive)
    val seconds = (SeekSkipStepMs / 1000).toInt()
    val rewindDescription = stringResource(R.string.seek_backward_dynamic, seconds)
    val forwardDescription = stringResource(R.string.seek_forward_dynamic, seconds)
    return remember(playerConnection, rewindDescription, forwardDescription) {
        SeekSkip(
            playerConnection = playerConnection,
            progressive = { progressiveState.value },
            rewindDescription = rewindDescription,
            forwardDescription = forwardDescription,
        )
    }
}

@Composable
internal fun rememberSeekSkip(): SeekSkip? = LocalPlayerConnection.current?.let { rememberSeekSkip(it) }

/** A bare tinted rewind or fast-forward glyph; each style supplies its own tint and size. */
@Composable
internal fun SeekSkipButton(
    seekSkip: SeekSkip,
    forward: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 20.dp,
) {
    val haptics = LocalHapticFeedback.current
    val (hapticsEnabled) = rememberPreference(EnableHapticFeedbackKey, true)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(buttonSize)
                .clip(CircleShape)
                .clickable {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (forward) seekSkip.forward() else seekSkip.rewind()
                },
    ) {
        Icon(
            imageVector = if (forward) SeekSkipForwardIcon else SeekSkipRewindIcon,
            contentDescription = if (forward) seekSkip.forwardDescription else seekSkip.rewindDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
