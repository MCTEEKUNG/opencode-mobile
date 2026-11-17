package dev.opencode.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun assertContrast(text: Color, background: Color, minimum: Double = 4.5) {
        val a = text.compositeOver(background).luminance().toDouble()
        val b = background.luminance().toDouble()
        val ratio = (maxOf(a, b) + .05) / (minOf(a, b) + .05)
        assertTrue("Contrast $ratio is below $minimum", ratio >= minimum)
    }

    @Test fun textAndButtonsMeetContrastInBothPalettes() {
        for (dark in listOf(false, true)) {
            val c = openCodeColorScheme(dark)
            listOf(c.onBackground to c.background, c.onSurface to c.surface,
                c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer,
                c.onSecondary to c.secondary, c.onSecondaryContainer to c.secondaryContainer,
                c.onTertiaryContainer to c.tertiaryContainer,
                c.onError to c.error, c.onErrorContainer to c.errorContainer,
                c.inverseOnSurface to c.inverseSurface).forEach { (text, bg) -> assertContrast(text, bg) }
        }
    }

    @Test fun hintsAndTimestampsRemainReadableOverGlassAndMenus() {
        for (dark in listOf(false, true)) {
            val c = openCodeColorScheme(dark)
            val backdrops = if (dark) listOf(c.background, Color(0xFF333333)) else listOf(Color.White, Color(0xFFD5D7DD))
            for (backdrop in backdrops) {
                assertContrast(c.onSurfaceVariant, c.surface.copy(alpha = .76f).compositeOver(backdrop))
                assertContrast(c.onSurfaceVariant, backdrop)
            }
            for (surface in listOf(c.surfaceVariant, c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)) {
                assertContrast(c.onSurface, surface)
                assertContrast(c.onSurfaceVariant, surface)
            }
            assertContrast(c.outline, c.surface, minimum = 3.0)
        }
    }

    @Test fun connectedStatusTextHasReadableContrastOnBothThemes() {
        for (dark in listOf(false, true)) {
            val c = openCodeColorScheme(dark)
            for (surface in listOf(c.surface, c.surfaceVariant, c.background, c.surfaceBright)) {
                assertContrast(connectedColor(c), surface)
            }
        }
    }

    @Test fun messageCodeAndSelectionKeepContrastOnBothBubbleStyles() {
        for (dark in listOf(false, true)) {
            val c = openCodeColorScheme(dark)
            for ((text, bubble) in listOf(c.onPrimary to c.primary, c.onSurface to c.surface)) {
                for (alpha in listOf(.08f, .12f)) assertContrast(text, text.copy(alpha = alpha).compositeOver(bubble))
                assertContrast(text, c.primary.copy(alpha = .25f).compositeOver(bubble))
            }
        }
    }

    @Test fun sharedComponentRolesStayNeutralInsteadOfFallingBackToPurple() {
        for (dark in listOf(false, true)) {
            val c = openCodeColorScheme(dark)
            for (color in listOf(c.primaryContainer, c.secondaryContainer, c.tertiaryContainer,
                c.surfaceContainerLowest, c.surfaceContainerLow, c.surfaceContainer,
                c.surfaceContainerHigh, c.surfaceContainerHighest, c.inverseSurface, c.surfaceTint)) {
                assertTrue(kotlin.math.abs(color.red - color.green) < .04f)
                assertTrue(kotlin.math.abs(color.red - color.blue) < .04f)
            }
        }
    }
}
