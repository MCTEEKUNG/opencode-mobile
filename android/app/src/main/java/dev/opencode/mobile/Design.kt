package dev.opencode.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OpenCodeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = openCodeColorScheme(dark)
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1.5).sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = {
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                androidx.compose.foundation.text.selection.LocalTextSelectionColors provides androidx.compose.foundation.text.selection.TextSelectionColors(
                    handleColor = colors.primary, backgroundColor = colors.primary.copy(alpha = .25f)),
            ) { content() }
        },
    )
}

@Composable
fun GlassBackdrop(dark: Boolean) {
    val background = MaterialTheme.colorScheme.background
    val glow = if (dark) Color(0xFF333333) else Color.White
    val shade = if (dark) Color(0xFF242424) else Color(0xFFD5D7DD)
    Canvas(Modifier.fillMaxSize().background(background).blur(64.dp)) {
        drawCircle(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), Offset(size.width * .1f, size.height * .2f), size.width * .9f), size.width * .9f, Offset(size.width * .1f, size.height * .2f))
        drawCircle(shade.copy(alpha = .75f), size.width * .48f, Offset(size.width * 1.05f, size.height * .42f))
        drawCircle(glow, size.width * .6f, Offset(size.width * .3f, size.height * .88f))
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .76f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)), shadowElevation = 2.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun WelcomePanel(savedComputers: Boolean, busy: Boolean, onScan: () -> Unit, onPaste: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your workspace.\nWithin reach.", style = MaterialTheme.typography.headlineLarge)
            Text(if (savedComputers) "Choose a saved computer above, or connect another one." else "Pick up your OpenCode conversations from your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("Connect your computer", style = MaterialTheme.typography.titleLarge)
            Text("On the computer running OpenCode V2, create a pairing QR.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text("opencode pair", Modifier.fillMaxWidth().padding(16.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Scan QR code") }
            OutlinedButton(onClick = onPaste, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Paste pairing link") }
        }
        Text("Pair once to save your computer. Next time, open the app to reconnect automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
