package com.expensetracker.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * One place for every animation in the app.
 *
 * The house style is "calm": motion eases in gently, eases out softly, and
 * always lands in a predictable amount of time. Three rules keep it that way,
 * and every spec below exists to serve one of them.
 *
 * 1. **No two parts of one gesture use different durations.** A slide paired
 *    with a shorter fade is what makes a transition look like it "slips" — the
 *    eye finishes the motion before the pixels do. Paired specs here always
 *    share a duration, so they finish together.
 * 2. **Tweens, not springs.** Springs have a long elastic tail, and once an
 *    element is large, animating its *size* on a spring reads as rubberiness.
 *    Tweens always land in exactly the same time and never wobble.
 * 3. **Nothing outruns the eye.** Curves stay near the Material 2 standard
 *    instead of the front-loaded M3 "emphasized" curves, which cover most of
 *    their distance in the first third of their time and read as a jolt.
 */
object MotionTokens {
    /** Colour and other small state cross-fades (selection, toggles, hovers). */
    const val DurationFast = 200

    /** Structural motion: content swaps, expand/collapse, the nav bar. */
    const val DurationMedium = 300

    /** Deliberate reveals: counters, charts, progress fills. */
    const val DurationSlow = 520

    /**
     * Page-level navigation. Enter and exit share one duration so the outgoing
     * and incoming pages move in lockstep.
     */
    const val DurationPage = 400

    /**
     * How far the outgoing page travels relative to the incoming one. Anything
     * under 1.0f creates a parallax effect (a small depth cue) instead of both
     * pages sliding a full screen width past each other.
     */
    const val PageParallax = 0.3f

    // ── Reveal staggers ─────────────────────────────────────────────────
    // A screen that reveals three things on the same frame reads as one
    // restless blur. Holding each one back by a beat turns that into a
    // sequence the eye can follow.
    const val RevealStaggerSmall = 80
    const val RevealStaggerMedium = 160
    const val RevealStaggerLarge = 240

    // ── Curves ──────────────────────────────────────────────────────────
    // Three curves, split by how much of the screen is moving. Large motion
    // gets the slow-starting curve so it carries weight; small state changes
    // get the quicker one so they still feel responsive to the tap that
    // caused them.
    //
    // Note this is deliberately *not* Material 3's "emphasized" curve. That
    // one covers most of its distance in the first third of its time, which
    // reads as a snap next to this palette; the two below are its calmer
    // Material 2 counterparts.

    /** Large motion. Starts unhurried, settles without a hard stop. */
    val CalmEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Leaving motion. Same shape, leaning out — smooth all the way to the end. */
    val CalmAccelerate: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Small state changes: responsive on the way out, soft on the way in. */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ── System animation setting ────────────────────────────────────────
    /**
     * False when the platform has animations switched off (Developer options →
     * "Animator duration scale: off", some battery-saver modes). Every spec
     * below collapses to a zero-length tween in that case, so state changes
     * still happen — they just arrive instantly instead of travelling.
     */
    val animationsEnabled: Boolean
        get() = try {
            ValueAnimator.areAnimatorsEnabled()
        } catch (_: Throwable) {
            true
        }

    /**
     * Builds a tween, or a zero-length one when animations are disabled.
     * `tween(0)` is what Compose itself uses for `snap()`, so this is the
     * supported way to express "no animation".
     */
    private fun <T> calm(
        durationMillis: Int,
        easing: Easing,
        delayMillis: Int = 0
    ): TweenSpec<T> = if (animationsEnabled) {
        tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)
    } else {
        tween(durationMillis = 0, easing = LinearEasing)
    }

    // ── Generic specs ───────────────────────────────────────────────────

    /** Small, quick state change: a colour, tint, or slight scale settling. */
    fun <T> fastTween(delayMillis: Int = 0): TweenSpec<T> =
        calm(DurationFast, StandardEasing, delayMillis)

    /**
     * A measured change in a value: a counter ticking up, a chart growing, a
     * progress bar filling. Slow enough to follow with your eye.
     *
     * Pass a [delayMillis] to hold a reveal back and start it after its
     * neighbours — several things animating on the same frame at once is the
     * opposite of calm.
     */
    fun <T> progressTween(
        durationMillis: Int = DurationSlow,
        delayMillis: Int = 0
    ): TweenSpec<T> = calm(durationMillis, CalmEasing, delayMillis)

    /**
     * Size and scale changes. A tween rather than a spring on purpose: when
     * what is animating is a card's height, a spring's long tail reads as the
     * layout wobbling. See rule 2 above.
     */
    fun <T> gentle(
        durationMillis: Int = DurationMedium,
        delayMillis: Int = 0
    ): TweenSpec<T> = calm(durationMillis, CalmEasing, delayMillis)

    // ── Page transition specs ────────────────────────────────────────────
    // Incoming page: eases into place.
    fun <T> pageEnter(): TweenSpec<T> = calm(DurationPage, CalmEasing)

    // Outgoing page: same duration so both pages stay in sync.
    fun <T> pageExit(): TweenSpec<T> = calm(DurationPage, CalmEasing)

    /**
     * Runs for the full page duration, the same as the slide it rides with, so
     * opacity and position land on the same frame. A shorter fade here is what
     * makes a page look like it is still sliding after it has stopped fading.
     */
    fun <T> pageFade(): TweenSpec<T> = calm(DurationPage, CalmEasing)

    // ── Floating navbar show/hide specs ──────────────────────────────────
    // Slide and fade share a duration here too, for the same lockstep reason.
    fun <T> navbarEnter(): TweenSpec<T> = calm(DurationMedium, CalmEasing)

    fun <T> navbarExit(): TweenSpec<T> = calm(DurationMedium, CalmEasing)

    fun <T> navbarFade(): TweenSpec<T> = calm(DurationMedium, CalmEasing)

    // ── Nav item selection ───────────────────────────────────────────────
    // Colour, icon and label all cross-fade on the same curve so the selected
    // tab resolves as one gesture rather than three separate ones.
    fun <T> navSelection(): TweenSpec<T> = calm(DurationFast, StandardEasing)

    // ── In-place content swaps (loading -> loaded, empty -> list) ─────────
    fun <T> contentSwap(): TweenSpec<T> = calm(DurationMedium, CalmEasing)

    // ── Scroll-driven values ────────────────────────────────────────────
    /**
     * For values that follow the finger — the nav bar's blur, the scrim. Kept
     * short on purpose: anything longer than the scroll itself leaves the bar
     * trailing behind the content it is supposed to be tracking.
     */
    fun <T> scrollTween(): TweenSpec<T> = calm(DurationFast, StandardEasing)

    // ── Reusable transition pairs ────────────────────────────────────────
    /**
     * The standard "a section opened" transition. Both halves share one
     * duration so the content and its container arrive together — hand-tuned
     * mismatches here are where most of the app's visual stutter came from.
     */
    fun expandWithFade(durationMillis: Int = DurationMedium): EnterTransition =
        expandVertically(animationSpec = calm(durationMillis, CalmEasing)) +
            fadeIn(animationSpec = calm(durationMillis, CalmEasing))

    /** The matching "a section closed" transition. */
    fun collapseWithFade(durationMillis: Int = DurationMedium): ExitTransition =
        shrinkVertically(animationSpec = calm(durationMillis, CalmAccelerate)) +
            fadeOut(animationSpec = calm(durationMillis, CalmAccelerate))
}
