package com.pocketcli.core.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Brand Palette (Cold Violet / Deep Indigo)
val BrandIndigo = Color(0xFF4F378B)
val BrandVioletDark = Color(0xFF21182F)
val BrandVioletLight = Color(0xFFDCC2FF)

// Dark Color Scheme Tokens
val DarkPrimary = Color(0xFFDCC2FF)
val DarkOnPrimary = Color(0xFF381E72)
val DarkPrimaryContainer = Color(0xFF4F378B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)

val DarkSecondary = Color(0xFFCCC2DC)
val DarkOnSecondary = Color(0xFF332D41)
val DarkSecondaryContainer = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)

val DarkTertiary = Color(0xFFEFB8C8)
val DarkOnTertiary = Color(0xFF492532)
val DarkTertiaryContainer = Color(0xFF633B48)
val DarkOnTertiaryContainer = Color(0xFFFFD8E4)

val DarkBackground = Color(0xFF141218)
val DarkOnBackground = Color(0xFFE6E0E9)
val DarkSurface = Color(0xFF141218)
val DarkOnSurface = Color(0xFFE6E0E9)
val DarkSurfaceVariant = Color(0xFF49454F)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)

val DarkSurfaceContainerLowest = Color(0xFF0F0D13)
val DarkSurfaceContainerLow = Color(0xFF1D1B20)
val DarkSurfaceContainer = Color(0xFF211F26)
val DarkSurfaceContainerHigh = Color(0xFF2B2930)
val DarkSurfaceContainerHighest = Color(0xFF36343B)

val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)

// Light Color Scheme Tokens
val LightPrimary = Color(0xFF6750A4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEADDFF)
val LightOnPrimaryContainer = Color(0xFF21005D)

val LightSecondary = Color(0xFF625B71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1D192B)

val LightTertiary = Color(0xFF7D5260)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFD8E4)
val LightOnTertiaryContainer = Color(0xFF31111D)

val LightBackground = Color(0xFFFEF7FF)
val LightOnBackground = Color(0xFF1D1B20)
val LightSurface = Color(0xFFFEF7FF)
val LightOnSurface = Color(0xFF1D1B20)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightOnSurfaceVariant = Color(0xFF49454F)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF7F2FA)
val LightSurfaceContainer = Color(0xFFF3EDF7)
val LightSurfaceContainerHigh = Color(0xFFECE6F0)
val LightSurfaceContainerHighest = Color(0xFFE6E0E9)

val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)

// Semantic Coding & Agent Status Colors
val ToolRunningColor = Color(0xFF0288D1)
val ToolSuccessColor = Color(0xFF388E3C)
val ToolErrorColor = Color(0xFFD32F2F)
val ToolWarningColor = Color(0xFFF57C00)

val SemanticSuccess = Color(0xFF2E7D32)
val SemanticSuccessContainerDark = Color(0xFF1B5E20)
val SemanticSuccessContainerLight = Color(0xFFE8F5E9)

val SemanticWarning = Color(0xFFE65100)
val SemanticWarningContainerDark = Color(0xFFBF360C)
val SemanticWarningContainerLight = Color(0xFFFFF3E0)

val CodeBackgroundDark = Color(0xFF1E1E1E)
val CodeBackgroundLight = Color(0xFFF5F5F5)
val DiffAddedBackgroundDark = Color(0xFF1A3826)
val DiffRemovedBackgroundDark = Color(0xFF3D1B1B)
val DiffAddedBackgroundLight = Color(0xFFE6F4EA)
val DiffRemovedBackgroundLight = Color(0xFFFCE8E6)

@androidx.compose.runtime.Immutable
data class PocketCodeScheme(
    val background: Color,
    val onBackground: Color,
    val addedBackground: Color,
    val addedText: Color,
    val removedBackground: Color,
    val removedText: Color,
    val lineNumber: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color
)

val DarkCodeScheme = PocketCodeScheme(
    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFD4D4D4),
    addedBackground = Color(0xFF1A3826),
    addedText = Color(0xFF81C784),
    removedBackground = Color(0xFF3D1B1B),
    removedText = Color(0xFFE57373),
    lineNumber = Color(0xFF858585),
    keyword = Color(0xFF569CD6),
    string = Color(0xFFCE9178),
    comment = Color(0xFF6A9955)
)

val LightCodeScheme = PocketCodeScheme(
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF24292E),
    addedBackground = Color(0xFFE6F4EA),
    addedText = Color(0xFF1B5E20),
    removedBackground = Color(0xFFFCE8E6),
    removedText = Color(0xFFB71C1C),
    lineNumber = Color(0xFF6E7781),
    keyword = Color(0xFFD73A49),
    string = Color(0xFF032F62),
    comment = Color(0xFF6A737D)
)

val LocalPocketCodeScheme = androidx.compose.runtime.staticCompositionLocalOf { DarkCodeScheme }
