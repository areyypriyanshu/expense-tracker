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

    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

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
}
