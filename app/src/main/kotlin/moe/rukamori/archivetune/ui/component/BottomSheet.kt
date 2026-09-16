/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.constants.BottomSheetAnimationSpec
import moe.rukamori.archivetune.constants.BottomSheetSoftAnimationSpec
import moe.rukamori.archivetune.constants.NavigationBarAnimationSpec

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 *
 * @param keepContentAlive When true, the [content] composable is kept in the
 *   composition tree even when the sheet is collapsed (it's hidden via
 *   alpha=0 instead of being unmounted). This is used by the player sheet
 *   to keep the [InlineVideoPlayer]'s ExoPlayer alive across collapse/expand
 *   cycles — without this, collapsing the player to the mini player would
 *   release the ExoPlayer, and expanding it again would require re-resolving
 *   the stream URL and re-buffering (causing the "video pauses, audio keeps
 *   playing" bug). Default is false to preserve the original behavior for
 *   other sheets (queue, etc.) that don't need this.
 * @param morphMode When true, the sheet fades + scales in place (0.94 → 1.0) over
 *   450ms with FastOutSlowInEasing instead of a plain slide on open. The sheet
 *   still slides vertically with the finger while dragging. This is used by the
 *   queue to add an in-place morph open transition on top of the normal
 *   bottom-sheet behavior. Default is false to preserve the original slide
 *   behavior for other sheets.
 * @param opaqueBackground When true, the sheet's outer background is rendered
 *   fully opaque (alpha = [backgroundColor].alpha) as soon as the sheet is
 *   visible, instead of fading in proportionally to [BottomSheetState.progress].
 *   This is needed by the queue sheet: non-Apple-Music player styles render a
 *   zoomed/gradient/blur artwork backdrop behind the player, and the default
 *   progress-based alpha fade let that artwork bleed through the queue sheet
 *   while dragging. With this flag set, the outer background is opaque from
 *   the very first pixel of drag, fully covering the player artwork, while
 *   the inner content (queue rows) still fades in via its own graphicsLayer
 *   alpha. Default is false to preserve the original behavior for the player
 *   sheet and any other callers.
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onDismiss: (() -> Unit)? = null,
    keepContentAlive: Boolean = false,
    morphMode: Boolean = false,
    backHandlerEnabled: Boolean = true,
    opaqueBackground: Boolean = false,
    onCollapsedContentClick: (() -> Unit)? = null,
    navbarHiddenOffset: (() -> Float)? = null,
    sharedLayer: (@Composable BoxScope.() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val morphShape =
        if (morphMode) {
            remember(state) {
                PlayerSheetDynamicShape(
                    progressProvider = { state.progress.coerceIn(0f, 1f) },
                )
            }
        } else {
            null
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .offset {
                    val y =
                        (state.expandedBound - state.value)
                            .roundToPx()
                            .coerceAtLeast(0)
                    // Scroll-to-hide / route-change navbar: while the sheet sits
                    // collapsed, let the mini player drift down into the space
                    // the navigation bar vacated. Fades out with sheet progress
                    // so the expanded player is never double-shifted.
                    val takeOver =
                        (navbarHiddenOffset?.invoke()?.coerceAtLeast(0f) ?: 0f) *
                            (1f - state.progress.coerceIn(0f, 1f))
                    IntOffset(x = 0, y = (y + takeOver).roundToInt())
                }.bottomSheetDraggable(state, onDismiss)
                .clip(
                    morphShape
                        ?: RoundedCornerShape(
                            topStart = if (!state.isExpanded) 16.dp else 0.dp,
                            topEnd = if (!state.isExpanded) 16.dp else 0.dp,
                        ),
                ).background(
                    if (opaqueBackground) {
                        // Render the outer background fully opaque ONLY when
                        // the sheet is actually sliding up or expanded
                        // (progress > 0). When the sheet is fully collapsed
                        // (progress = 0), the background is transparent so it
                        // does NOT cover the system navigation bar area
                        // (gesture hint / 3-button nav) at the bottom of the
                        // screen.
                        //
                        // Previously, this branch returned `backgroundColor`
                        // unconditionally, which meant the opaque background
                        // was always rendered — even when the sheet was
                        // collapsed at the peek height. Because the queue
                        // sheet's `collapsedBound` is
                        // `dynamicQueuePeekHeight + systemBarsBottom`, the
                        // visible portion of the collapsed sheet INCLUDES the
                        // navigation bar inset, and the opaque background
                        // covered it, hiding the gesture hint / 3-button nav.
                        //
                        // By gating on `progress > 0`, the background is:
                        //   - transparent when collapsed (progress = 0): the
                        //     navigation bar shows through normally.
                        //   - opaque as soon as the user starts dragging
                        //     (progress > 0): the player's zoomed artwork
                        //     backdrop is hidden during the slide-up, which
                        //     was the original bug `opaqueBackground = true`
                        //     was introduced to fix.
                        if (state.progress > 0f) {
                            backgroundColor
                        } else {
                            Color.Transparent
                        }
                    } else {
                        backgroundColor.copy(
                            alpha = backgroundColor.alpha * state.progress.coerceIn(0f, 1f),
                        )
                    },
                ),
    ) {
        if (state.isExpandedOrExpanding && backHandlerEnabled) {
            BackHandler(onBack = state::collapseSoft)
        }

        if (sharedLayer != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = sharedLayer,
            )
        }

        val fullContentZIndex by remember(state) {
            derivedStateOf { if (state.progress > 0.5f) 2f else 1f }
        }

        if (keepContentAlive) {
            // Always compose the content, but hide it when collapsed.
            // This keeps stateful composables (e.g. InlineVideoPlayer's
            // ExoPlayer) alive across collapse/expand cycles.
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(fullContentZIndex)
                        .graphicsLayer {
                            if (morphMode) {
                                val p = state.progress.coerceIn(0f, 1f)
                                alpha = ((p - 0.5f) * 2).coerceIn(0f, 1f)
                                if (p <= 0.01f) translationY = 10_000f
                            } else {
                                alpha = if (state.isCollapsed) 0f else ((state.progress - 0.25f) * 4).coerceIn(0f, 1f)
                            }
                        },
                content = content,
            )
        } else if (!state.isCollapsed) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(fullContentZIndex)
                        .graphicsLayer {
                            if (morphMode) {
                                val p = state.progress.coerceIn(0f, 1f)
                                alpha = ((p - 0.5f) * 2).coerceIn(0f, 1f)
                            } else {
                                alpha = ((state.progress - 0.25f) * 4).coerceIn(0f, 1f)
                            }
                        },
                content = content,
            )
        }

        if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            val miniZIndex by remember(state) {
                derivedStateOf { if (state.progress > 0.5f) 1f else 2f }
            }
            Box(
                modifier =
                    Modifier
                        .zIndex(miniZIndex)
                        .graphicsLayer {
                            alpha =
                                if (morphMode) {
                                    (1f - 2f * state.progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                                } else {
                                    1f - (state.progress * 4).coerceAtMost(1f)
                                }
                        }.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapsedContentClick ?: state::expandSoft,
                        ).fillMaxWidth()
                        .height(state.collapsedBound),
                content = collapsedContent,
            )
        }
    }
}

private class PlayerSheetDynamicShape(
    private val progressProvider: () -> Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: Density,
    ): Outline {
        val progress = progressProvider()
        val cornerPx = with(density) { androidx.compose.ui.unit.lerp(28.dp, 0.dp, progress).toPx() }
        return Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
            ),
        )
    }
}

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    private val animationsDisabled: Boolean,
    private val density: Density,
    collapsedBound: Dp,
    initialAnchor: Int = DISMISSED_ANCHOR,
) : DraggableState by draggableState {
    private val collapsedBoundState = mutableStateOf(collapsedBound)

    val collapsedBound: Dp
        get() = collapsedBoundState.value

    internal var targetCollapsedBound: Dp by mutableStateOf(
        collapsedBound.coerceIn(animatable.lowerBound ?: 0.dp, animatable.upperBound ?: collapsedBound)
    )
        private set

    private var lastAnimationSpec: AnimationSpec<Dp> =
        if (animationsDisabled) snap() else BottomSheetAnimationSpec

    internal fun updateTargetCollapsedBound(newTargetBound: Dp) {
        val lower = animatable.lowerBound ?: 0.dp
        val upper = animatable.upperBound ?: newTargetBound
        val clampedTarget = newTargetBound.coerceIn(lower, upper)
        val previousTarget = targetCollapsedBound
        targetCollapsedBound = clampedTarget
        if (previousTarget == clampedTarget) return

        if (targetAnchor == COLLAPSED_ANCHOR) {
            val isRestingAtOldCollapsed = !animatable.isRunning &&
                (animatable.value - collapsedBoundState.value).let { if (it < 0.dp) -it else it } < 0.5.dp
            if (!isRestingAtOldCollapsed) {
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    animatable.animateTo(clampedTarget, lastAnimationSpec)
                }
            }
        }
    }

    internal suspend fun reanchorTo(newCollapsedBound: Dp) {
        val previous = collapsedBoundState.value
        if (newCollapsedBound == previous) return
        val current = animatable.value
        val isRestingAtCollapsed = !animatable.isRunning &&
            (current == previous || (current - previous).let { if (it < 0.dp) -it else it } < 0.5.dp)

        val lower = animatable.lowerBound ?: 0.dp
        val upper = animatable.upperBound ?: newCollapsedBound
        val target =
            if (isRestingAtCollapsed) {
                newCollapsedBound.coerceIn(lower, upper)
            } else {
                current
            }

        withContext(NonCancellable) {
            if (target != current) {
                animatable.snapTo(target)
            }
            collapsedBoundState.value = newCollapsedBound
        }
    }
    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    var targetAnchor by mutableIntStateOf(initialAnchor)
        private set

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    val isExpandedOrExpanding: Boolean
        get() = targetAnchor == EXPANDED_ANCHOR

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    private fun updateAnchor(anchor: Int) {
        targetAnchor = anchor
        onAnchorChanged(anchor)
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        updateAnchor(COLLAPSED_ANCHOR)
        lastAnimationSpec = animationSpec
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(targetCollapsedBound, animationSpec)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        updateAnchor(EXPANDED_ANCHOR)
        lastAnimationSpec = animationSpec
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.upperBound!!, animationSpec)
        }
    }

    fun collapse(animationSpec: AnimationSpec<Dp>, velocityPx: Float = 0f) {
        updateAnchor(COLLAPSED_ANCHOR)
        lastAnimationSpec = animationSpec
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(
                targetCollapsedBound,
                animationSpec,
                initialVelocity = with(density) { velocityPx.toDp() },
            )
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>, velocityPx: Float = 0f) {
        updateAnchor(EXPANDED_ANCHOR)
        lastAnimationSpec = animationSpec
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(
                animatable.upperBound!!,
                animationSpec,
                initialVelocity = with(density) { velocityPx.toDp() },
            )
        }
    }

    private fun collapse(velocityPx: Float = 0f) {
        collapse(if (animationsDisabled) snap() else BottomSheetAnimationSpec, velocityPx)
    }

    private fun expand(velocityPx: Float = 0f) {
        expand(if (animationsDisabled) snap() else BottomSheetAnimationSpec, velocityPx)
    }

    fun collapseSoft() {
        collapse(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun expandSoft() {
        expand(if (animationsDisabled) snap() else BottomSheetSoftAnimationSpec)
    }

    fun dismiss() {
        updateAnchor(DISMISSED_ANCHOR)
        val spec = if (animationsDisabled) snap() else BottomSheetAnimationSpec
        lastAnimationSpec = spec
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.lowerBound!!, spec)
        }
    }

    fun snapTo(value: Dp) {
        updateAnchor(
            when (value) {
                expandedBound -> EXPANDED_ANCHOR
                collapsedBound -> COLLAPSED_ANCHOR
                dismissedBound -> DISMISSED_ANCHOR
                else -> COLLAPSED_ANCHOR
            },
        )
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value)
        }
    }

    fun performFling(
        velocity: Float,
        onDismiss: (() -> Unit)?,
    ) {
        if (velocity > 250) {
            expand(velocity)
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse(velocity)
            }
        } else {
            val l0 = dismissedBound
            val l1 = (collapsedBound - dismissedBound) / 2
            val l2 = (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse(velocity)
                    }
                }

                in l1..l2 -> {
                    collapse(velocity)
                }

                in l2..l3 -> {
                    expand(velocity)
                }

                else -> {
                    Unit
                }
            }
        }
    }

    /**
     * One instance per sheet, deliberately — this used to be a `get()` that minted a fresh
     * connection on every read.
     *
     * `isTopReached` is per-GESTURE state: it latches when the inner scrollable can give no more,
     * and it is what lets the rest of that same drag pull the sheet down. Call sites write
     * `Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)`, which re-reads the
     * property on every recomposition — so a new object arrived mid-drag, `nestedScroll` swapped
     * it in, and the latch reset to false. The drag then finished scrolling nothing and the sheet
     * never collapsed. Only the SimpMusic style showed it, because it is the only player style
     * with a full-page `verticalScroll` inside the sheet; everywhere else the drag reaches the
     * sheet's own draggable without passing through here.
     */
    val preUpPostDownNestedScrollConnection: NestedScrollConnection by lazy {
        object : NestedScrollConnection {
            var isTopReached = false

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (isExpanded && available.y < 0) {
                    isTopReached = false
                }

                return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isTopReached) {
                    isTopReached = consumed.y == 0f && available.y > 0
                }

                return if (isTopReached && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (isTopReached) {
                    val velocity = -available.y
                    performFling(velocity, null)

                    available
                } else {
                    Velocity.Zero
                }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                isTopReached = false
                return Velocity.Zero
            }
        }
    }
}

const val EXPANDED_ANCHOR = 2
const val COLLAPSED_ANCHOR = 1
const val DISMISSED_ANCHOR = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = DISMISSED_ANCHOR,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val animationsDisabled = LocalAnimationsDisabled.current

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable =
        remember {
            Animatable(0.dp, Dp.VectorConverter)
        }

    val state =
        remember(dismissedBound, expandedBound, coroutineScope, animationsDisabled) {
            val initialValue =
                when (previousAnchor) {
                    EXPANDED_ANCHOR -> expandedBound
                    COLLAPSED_ANCHOR -> collapsedBound
                    DISMISSED_ANCHOR -> dismissedBound
                    else -> error("Unknown BottomSheet anchor")
                }

            animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                animatable.animateTo(initialValue, if (animationsDisabled) snap() else BottomSheetAnimationSpec)
            }

            BottomSheetState(
                draggableState =
                    DraggableState { delta ->
                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                        }
                    },
                onAnchorChanged = { previousAnchor = it },
                coroutineScope = coroutineScope,
                animatable = animatable,
                animationsDisabled = animationsDisabled,
                density = density,
                collapsedBound = collapsedBound,
                initialAnchor = previousAnchor,
            )
        }

    LaunchedEffect(state, collapsedBound) {
        state.updateTargetCollapsedBound(collapsedBound)
    }

    val animationSpec = if (animationsDisabled) snap() else NavigationBarAnimationSpec
    val animatedCollapsedBound by
        animateDpAsState(
            targetValue = collapsedBound,
            animationSpec = animationSpec,
            label = "SheetCollapsedBound",
        )
    LaunchedEffect(state) {
        snapshotFlow { animatedCollapsedBound }.collect {
            state.reanchorTo(it)
        }
    }

    return state
}

private class BottomSheetGestureRegion(private val view: View) {
    var coordinates: LayoutCoordinates? = null
    private val windowLocation = IntArray(2)

    fun canStartDrag(position: Offset): Boolean {
        val layoutCoordinates = coordinates?.takeIf { it.isAttached } ?: return false
        val insets = ViewCompat.getRootWindowInsets(view)?.getInsets(
            WindowInsetsCompat.Type.systemGestures() or WindowInsetsCompat.Type.navigationBars(),
        ) ?: return true
        val rootView = view.rootView
        rootView.getLocationInWindow(windowLocation)
        val windowPosition = layoutCoordinates.localToWindow(position)
        val x = windowPosition.x - windowLocation[0]
        val y = windowPosition.y - windowLocation[1]
        return x >= insets.left && x < rootView.width - insets.right &&
            y >= insets.top && y < rootView.height - insets.bottom
    }
}

@Composable
fun Modifier.bottomSheetDraggable(
    state: BottomSheetState,
    onDismiss: (() -> Unit)? = null,
): Modifier {
    val view = LocalView.current
    val gestureRegion = remember(view) { BottomSheetGestureRegion(view) }
    val updateCoordinates: (LayoutCoordinates) -> Unit = remember(gestureRegion) {
        { gestureRegion.coordinates = it }
    }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    return this
        .onGloballyPositioned(updateCoordinates)
        .pointerInput(state, gestureRegion) {
            val velocityTracker = VelocityTracker()

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!gestureRegion.canStartDrag(down.position)) return@awaitEachGesture

                val initialAnchor = state.targetAnchor
                velocityTracker.resetTracking()
                velocityTracker.addPointerInputChange(down)
                var dragStarted = false
                var dragCompleted = false

                try {
                    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        change.consume()
                        dragStarted = true
                        velocityTracker.addPointerInputChange(change)
                        state.dispatchRawDelta(overSlop)
                    } ?: return@awaitEachGesture

                    dragCompleted = verticalDrag(drag.id) { change ->
                        velocityTracker.addPointerInputChange(change)
                        state.dispatchRawDelta(change.positionChange().y)
                        change.consume()
                    }
                    if (dragCompleted) {
                        state.performFling(-velocityTracker.calculateVelocity().y, currentOnDismiss)
                    }
                } finally {
                    velocityTracker.resetTracking()
                    if (dragStarted && !dragCompleted) {
                        when (initialAnchor) {
                            EXPANDED_ANCHOR -> state.expandSoft()
                            COLLAPSED_ANCHOR -> state.collapseSoft()
                            DISMISSED_ANCHOR -> state.dismiss()
                        }
                    }
                }
            }
        }
}
