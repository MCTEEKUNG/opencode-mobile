package dev.opencode.mobile

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Explicit neutral roles keep dialogs, menus, fields and selected chips on the same palette. */
fun openCodeColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFF5F5F5), onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF333333), onPrimaryContainer = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF111111),
    secondary = Color(0xFFBBBBBB), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF333333), onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFBBBBBB), onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF333333), onTertiaryContainer = Color(0xFFF5F5F5),
    background = Color(0xFF090909), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF191919), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF292929), onSurfaceVariant = Color(0xFFBBBBBB),
    surfaceTint = Color(0xFFF5F5F5), inverseSurface = Color(0xFFE6E6E6), inverseOnSurface = Color(0xFF303030),
    outline = Color(0xFF858585), outlineVariant = Color(0xFF575757),
    surfaceDim = Color(0xFF090909), surfaceBright = Color(0xFF3B3B3B),
    surfaceContainerLowest = Color(0xFF090909), surfaceContainerLow = Color(0xFF191919),
    surfaceContainer = Color(0xFF202020), surfaceContainerHigh = Color(0xFF292929), surfaceContainerHighest = Color(0xFF333333),
) else lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E2E5), onPrimaryContainer = Color(0xFF111111),
    inversePrimary = Color(0xFFF5F5F5),
    secondary = Color(0xFF606066), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E2E5), onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF606066), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E2E5), onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFF2F3F5), onBackground = Color(0xFF111111),
    surface = Color(0xFFFAFAFA), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE8E9EC), onSurfaceVariant = Color(0xFF505055),
    surfaceTint = Color(0xFF111111), inverseSurface = Color(0xFF303030), inverseOnSurface = Color(0xFFF5F5F5),
    outline = Color(0xFF77777F), outlineVariant = Color(0xFFB8B9BF),
    surfaceDim = Color(0xFFD5D7DD), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F4F6),
    surfaceContainer = Color(0xFFF4F4F6), surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFE8E9EC),
)

fun connectedColor(colors: ColorScheme): Color =
    if (colors.background.luminance() < .5f) Color(0xFF79D5A3) else Color(0xFF216E45)
