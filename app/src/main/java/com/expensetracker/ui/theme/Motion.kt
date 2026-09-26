package com.expensetracker.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object MotionTokens {
    const val DurationFast = 160
    const val DurationMedium = 260
    const val DurationSlow = 420

    /**
     * Page-level navigation. Enter and exit deliberately share one duration so
     * the outgoing and incoming pages move in lockstep — mismatched durations are
     * what makes slide transitions feel like they "slip".
     */
    const val DurationPage = 340

    /**
     * How far the outgoing page travels relative to the incoming one. Anything
     * under 1.0f creates a parallax effect (a small depth cue) instead of both
     * pages sliding a full screen width past each other.
     */
    const val PageParallax = 0.3f

    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Material 3 emphasized-decelerate — fast start, long soft landing. */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Material 3 emphasized-accelerate — gentle start, quick exit. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> fastTween(delayMillis: Int = 0): TweenSpec<T> = tween(
        durationMillis = DurationFast,
        delayMillis = delayMillis,
        easing = StandardEasing
    )

    fun <T> enterTween(
        durationMillis: Int = DurationMedium,
        delayMillis: Int = 0
    ): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = EnterEasing
    )

    fun <T> exitTween(
        durationMillis: Int = DurationFast,
        delayMillis: Int = 0
    ): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = ExitEasing
    )

    fun <T> progressTween(durationMillis: Int = DurationSlow): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        easing = StandardEasing
    )

    fun <T> spring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // ── Page transition specs ────────────────────────────────────────────
    // Incoming page: decelerates into place.
    fun <T> pageEnter(): TweenSpec<T> = tween(
        durationMillis = DurationPage,
        easing = EmphasizedEasing
    )

    // Outgoing page: same duration so both pages stay in sync.
    fun <T> pageExit(): TweenSpec<T> = tween(
        durationMillis = DurationPage,
        easing = EmphasizedEasing
    )

    // Opacity rides slightly ahead of the slide and finishes with it, so the
    // page never looks like it is still fading after it has stopped moving.
    fun <T> pageFade(): TweenSpec<T> = tween(
        durationMillis = DurationPage / 2,
        easing = StandardEasing
    )

    // ── Floating navbar show/hide specs ──────────────────────────────────
    // Slide and fade share a duration here too, for the same lockstep reason.
    fun <T> navbarEnter(): TweenSpec<T> = tween(
        durationMillis = DurationMedium,
        easing = EmphasizedEasing
    )

    fun <T> navbarExit(): TweenSpec<T> = tween(
        durationMillis = DurationFast,
        easing = StandardEasing
    )

    fun <T> navbarFade(): TweenSpec<T> = tween(
        durationMillis = DurationMedium,
        easing = StandardEasing
    )

    // ── Nav item selection ───────────────────────────────────────────────
    // Colour, icon and label all cross-fade on the same curve so the selected
    // tab resolves as one gesture rather than three separate ones.
    fun <T> navSelection(): TweenSpec<T> = tween(
        durationMillis = 200,
        easing = StandardEasing
    )

    // ── In-place content swaps (loading -> loaded, empty -> list) ─────────
    fun <T> contentSwap(): TweenSpec<T> = tween(
        durationMillis = DurationMedium,
        easing = StandardEasing
    )
}
