package com.nova.browser.core.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/** Motion tokens (spec 04_UI_SYSTEM → ANIMATIONS). */
object NovaMotion {
    const val DurationFast = 150
    const val DurationMedium = 200
    const val DurationSlow = 300
    const val DurationSheet = 250
    const val DurationShimmer = 1600
    const val DurationPulse = 1800

    val EaseOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val EaseInOut = FastOutSlowInEasing
    val Linear = LinearEasing

    fun <T> fast() = tween<T>(DurationFast, easing = EaseOut)
    fun <T> medium() = tween<T>(DurationMedium, easing = EaseOut)
    fun <T> slow() = tween<T>(DurationSlow, easing = EaseOut)

    fun <T> springy() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/** Infinite 0f..1f progress used by shimmer / glow effects. */
@Composable
fun rememberInfiniteProgress(
    durationMillis: Int = NovaMotion.DurationShimmer,
    reverse: Boolean = false,
    label: String = "infiniteProgress"
): State<Float> {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = NovaMotion.Linear),
            repeatMode = if (reverse) RepeatMode.Reverse else RepeatMode.Restart
        ),
        label = label
    )
}

/** Breathing pulse between [min] and [max]. */
@Composable
fun rememberPulse(
    min: Float = 0.85f,
    max: Float = 1.05f,
    durationMillis: Int = NovaMotion.DurationPulse,
    label: String = "pulse"
): State<Float> {
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = NovaMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = label
    )
}
