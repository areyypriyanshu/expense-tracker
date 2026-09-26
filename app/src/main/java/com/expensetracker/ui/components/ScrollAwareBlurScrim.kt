package com.expensetracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.ui.navigation.LocalScrollBlurProvider
import com.expensetracker.ui.theme.MotionTokens

/**
 * How many pixels of scroll offset in the first visible item before the scrim
 * considers the list "scrolled". Keeps it from flipping on tiny over-scroll
 * bounces at the very top.
 */
private const val ScrollThreshold = 24

/**
 * Alpha of the gradient scrim painted in the nav-bar strip when the list is
 * at the top (i.e. content is flush with the bar — subtle dark frosted hint).
 */
private const val ScrimAlphaAtTop = 0.65f

/**
 * Alpha once the user has scrolled away from the top. The scrim persists but
 * steps back so content reads more clearly through the glass bar.
 */
private const val ScrimAlphaScrolled = 0.22f

/**
 * Height of the gradient band painted behind and above the nav bar.
 */
private val ScrimHeight = 88.dp

// ─────────────────────────────────────────────────────────────────────────────
// Public API — two overloads so callers pass whichever state they already hold
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps [content] in a [Box] and paints an animated gradient scrim at the
 * bottom of that box — in the strip of content that sits directly behind the
 * floating glass nav bar.
 *
 * **Scroll-aware behaviour**
 * - At the top (not scrolled): strong scrim ([ScrimAlphaAtTop]) so items that
 *   happen to land in the nav-bar zone don't look abruptly clipped.
 * - After scrolling: light scrim ([ScrimAlphaScrolled]) — content is more
 *   visible through the glass but the frosted softness never fully disappears.
 * - Animates between the two states with [MotionTokens.scrollTween], short
 *   enough to stay locked to the scroll.
 *
 * This overload accepts a [LazyListState] (screens backed by `LazyColumn`).
 *
 * The composable also pushes the current "is scrolled" value into
 * [LocalScrollBlurProvider] so the floating glass nav bar can simultaneously
 * adjust its own blur radius / tint — keeping both effects in sync with zero
 * extra plumbing in the screen.
 *
 * @param listState  The `LazyListState` belonging to the screen's `LazyColumn`.
 * @param scrimColor Base colour for the gradient. Defaults to the window
 *                   background; callers can override for tinted screens.
 * @param scrimHeight Height of the gradient band (defaults to [ScrimHeight]).
 * @param modifier   Applied to the outer [Box].
 * @param content    The scrollable content. Receives [BoxScope].
 */
@Composable
fun ScrollAwareBlurScrim(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Unspecified,
    scrimHeight: Dp = ScrimHeight,
    content: @Composable BoxScope.() -> Unit
) {
    // Derive "isScrolled" inside a snapshot without triggering recomposition on
    // every pixel — only when the boolean flips.
    val isScrolled by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > ScrollThreshold
        }
    }

    ScrollAwareBlurScrimImpl(
        isScrolled = isScrolled,
        modifier = modifier,
        scrimColor = scrimColor,
        scrimHeight = scrimHeight,
        content = content
    )
}

/**
 * [ScrollAwareBlurScrim] overload for screens backed by a plain [ScrollState]
 * (e.g. `Column + verticalScroll`).
 *
 * @param scrollState The [ScrollState] from `rememberScrollState()`.
 */
@Composable
fun ScrollAwareBlurScrim(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Unspecified,
    scrimHeight: Dp = ScrimHeight,
    content: @Composable BoxScope.() -> Unit
) {
    val isScrolled by remember(scrollState) {
        derivedStateOf { scrollState.value > ScrollThreshold }
    }

    ScrollAwareBlurScrimImpl(
        isScrolled = isScrolled,
        modifier = modifier,
        scrimColor = scrimColor,
        scrimHeight = scrimHeight,
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal implementation shared by both public overloads
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScrollAwareBlurScrimImpl(
    isScrolled: Boolean,
    modifier: Modifier,
    scrimColor: Color,
    scrimHeight: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    // Push the scroll state into the composition local so the nav bar can
    // independently adjust its blur radius without any prop-drilling.
    val scrollBlurState = LocalScrollBlurProvider.current
    LaunchedEffect(isScrolled) {
        scrollBlurState.isScrolled = isScrolled
    }

    // Ease the scrim alpha between its two target values. Short enough to keep
    // up with the scroll — a slow fade here leaves the edge visibly lagging
    // behind the list it is supposed to soften.
    val targetAlpha = if (isScrolled) ScrimAlphaScrolled else ScrimAlphaAtTop
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = MotionTokens.scrollTween(),
        label = "scrimAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Screen content
        content()

        // Gradient scrim — rendered as a simple Box with a vertical brush so
        // it's pure Canvas with no extra RenderEffect / Haze overhead. The
        // glass blur of the nav bar itself is handled by Haze in Navigation.kt;
        // this scrim is purely a visual soft-edge that bridges the last visible
        // list item and the frosted bar above it.
        val resolvedColor = if (scrimColor == Color.Unspecified) {
            androidx.compose.material3.MaterialTheme.colorScheme.background
        } else {
            scrimColor
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(scrimHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            resolvedColor.copy(alpha = 0f),
                            resolvedColor.copy(alpha = animatedAlpha * 0.5f),
                            resolvedColor.copy(alpha = animatedAlpha * 0.9f)
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = animatedAlpha * 0.12f),
                            Color.Black.copy(alpha = animatedAlpha * 0.32f)
                        )
                    )
                )
        )
    }
}
