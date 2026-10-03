package com.pocketcli.core.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * PocketMotion defines centralized physics-based motion specs and durations
 * as specified in Section 23 of the PocketCLI UI/UX design spec.
 */
object PocketMotion {
    // Standard durations (ms)
    const val DURATION_INSTANT = 100
    const val DURATION_QUICK = 180
    const val DURATION_STANDARD = 300
    const val DURATION_EMPHASIZED = 450
    const val DURATION_SLOW_AMBIENT = 2400

    // Animation specs
    fun <T> instant(): AnimationSpec<T> = tween(durationMillis = DURATION_INSTANT)
    fun <T> quick(): AnimationSpec<T> = tween(durationMillis = DURATION_QUICK)
    fun <T> standard(): AnimationSpec<T> = tween(durationMillis = DURATION_STANDARD)
    fun <T> emphasized(): AnimationSpec<T> = tween(durationMillis = DURATION_EMPHASIZED)

    // Spring specs
    fun <T> springSpatial(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> springExpressive(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> springGesture(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )
}
