package com.pocketcli.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * PocketShapes defines standardized shape tokens according to Section 1.2
 * of POCKETCLI_UI_IMPLEMENTATION_PACK.md.
 */
object PocketShapes {
    val action = RoundedCornerShape(28.dp)
    val container = RoundedCornerShape(20.dp)
    val compact = RoundedCornerShape(14.dp)
    val technical = RoundedCornerShape(10.dp)

    // Material 3 Shapes compatibility
    val extraSmall = RoundedCornerShape(4.dp)
    val small = technical
    val medium = compact
    val large = container
    val extraLarge = action
}

val MaterialShapes = Shapes(
    extraSmall = PocketShapes.extraSmall,
    small = PocketShapes.technical,
    medium = PocketShapes.compact,
    large = PocketShapes.container,
    extraLarge = PocketShapes.action
)

object PocketCustomShapes {
    val Composer = PocketShapes.action
    val StatusPill = RoundedCornerShape(100.dp)
    val CodeBlock = PocketShapes.technical
    val Card = PocketShapes.container
    val HeroCard = RoundedCornerShape(24.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
