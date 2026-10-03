package com.pocketcli.core.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * PocketMotionScheme defines centralized physics-based motion specs and durations
 * as specified in Section 2 of POCKETCLI_UI_IMPLEMENTATION_PACK.md.
 */
@Immutable
data class PocketMotionScheme(
    val instant: FiniteAnimationSpec<Float> = tween(100),
    val quick: FiniteAnimationSpec<Float> = tween(180, easing = FastOutSlowInEasing),
    val standard: FiniteAnimationSpec<Float> = tween(280, easing = FastOutSlowInEasing),
    val emphasized: FiniteAnimationSpec<Float> = tween(420, easing = LinearOutSlowInEasing),
    val spatial: SpringSpec<Float> = spring(
        dampingRatio = 0.86f,
        stiffness = 500f,
    ),
    val expressive: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 420f,
    ),
    val gesture: SpringSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = 800f,
    ),
)

val LocalPocketMotionScheme = staticCompositionLocalOf { PocketMotionScheme() }

object PocketMotion {
    const val DURATION_INSTANT = 100
    const val DURATION_QUICK = 180
    const val DURATION_STANDARD = 280
    const val DURATION_EMPHASIZED = 420
    const val DURATION_SLOW_AMBIENT = 2400

    fun <T> instant(): FiniteAnimationSpec<T> = tween(durationMillis = DURATION_INSTANT)
    fun <T> quick(): FiniteAnimationSpec<T> = tween(durationMillis = DURATION_QUICK, easing = FastOutSlowInEasing)
    fun <T> standard(): FiniteAnimationSpec<T> = tween(durationMillis = DURATION_STANDARD, easing = FastOutSlowInEasing)
    fun <T> emphasized(): FiniteAnimationSpec<T> = tween(durationMillis = DURATION_EMPHASIZED, easing = LinearOutSlowInEasing)

    fun <T> springSpatial(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 500f)
    fun <T> springExpressive(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 420f)
    fun <T> springGesture(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 800f)
}
