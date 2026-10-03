package com.pocketcli.core.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),      // Code blocks, logs, terminal, diff hunks
    medium = RoundedCornerShape(16.dp),    // Cards, dialogs, sheets
    large = RoundedCornerShape(24.dp),     // Hero cards, surface containers
    extraLarge = RoundedCornerShape(28.dp) // Composer, floating toolbars, pills
)

object PocketCustomShapes {
    val Composer = RoundedCornerShape(28.dp)
    val StatusPill = RoundedCornerShape(100.dp)
    val CodeBlock = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(16.dp)
    val HeroCard = RoundedCornerShape(24.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
