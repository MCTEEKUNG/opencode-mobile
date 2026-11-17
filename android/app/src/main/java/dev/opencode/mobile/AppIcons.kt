package dev.opencode.mobile

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppIcons {
    private fun icon(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()
    val SystemTheme = icon("System theme") { moveTo(7f,2f); lineTo(17f,2f); lineTo(17f,22f); lineTo(7f,22f); close(); moveTo(7f,6f); lineTo(17f,6f); moveTo(10f,19f); lineTo(14f,19f) }
    val Settings = icon("Settings") { moveTo(9f,3f); lineTo(15f,3f); lineTo(16f,6f); lineTo(19f,6f); lineTo(22f,11f); lineTo(20f,14f); lineTo(21f,17f); lineTo(17f,21f); lineTo(14f,20f); lineTo(11f,22f); lineTo(6f,19f); lineTo(6f,16f); lineTo(3f,15f); lineTo(3f,9f); lineTo(6f,8f); lineTo(7f,5f); close(); moveTo(16f,12f); curveTo(16f,17.3f,8f,17.3f,8f,12f); curveTo(8f,6.7f,16f,6.7f,16f,12f); close() }
    val Search = icon("Search") { moveTo(16f,9f); curveTo(16f,18.3f,2f,18.3f,2f,9f); curveTo(2f,-0.3f,16f,-0.3f,16f,9f); close(); moveTo(14f,14f); lineTo(21f,21f) }
    val Trash = icon("Delete") { moveTo(4f,6f); lineTo(20f,6f); moveTo(9f,6f); lineTo(9f,3f); lineTo(15f,3f); lineTo(15f,6f); moveTo(6f,6f); lineTo(7f,21f); lineTo(17f,21f); lineTo(18f,6f); moveTo(10f,10f); lineTo(10f,17f); moveTo(14f,10f); lineTo(14f,17f) }
    val Attach = icon("Attach file") { moveTo(8f,13f); lineTo(15f,6f); curveTo(20f,1f,25f,7f,20f,12f); lineTo(10f,22f); curveTo(3f,27f,-1f,18f,4f,13f); lineTo(14f,3f); moveTo(8f,13f); lineTo(5f,16f); curveTo(3f,18f,6f,21f,8f,19f); lineTo(18f,9f) }
    val Image = icon("Image") { moveTo(3f,3f); lineTo(21f,3f); lineTo(21f,21f); lineTo(3f,21f); close(); moveTo(3f,17f); lineTo(9f,11f); lineTo(14f,16f); lineTo(17f,13f); lineTo(21f,17f); moveTo(15f,7f); lineTo(15.1f,7f) }
    val Computer = icon("Computer") { moveTo(3f,3f); lineTo(21f,3f); lineTo(21f,16f); lineTo(3f,16f); close(); moveTo(12f,16f); lineTo(12f,21f); moveTo(7f,21f); lineTo(17f,21f) }
    val Folder = icon("Projects") { moveTo(3f,6f); lineTo(9f,6f); lineTo(11f,8f); lineTo(21f,8f); lineTo(21f,19f); lineTo(3f,19f); close() }
    val FolderPlus = icon("New project") { moveTo(3f,6f); lineTo(9f,6f); lineTo(11f,8f); lineTo(21f,8f); lineTo(21f,20f); lineTo(3f,20f); close(); moveTo(9f,14f); lineTo(15f,14f); moveTo(12f,11f); lineTo(12f,17f) }
    val More = icon("More") { moveTo(12f,5f); lineTo(12f,5.1f); moveTo(12f,12f); lineTo(12f,12.1f); moveTo(12f,19f); lineTo(12f,19.1f) }
    val Send = icon("Send") { moveTo(21f,3f); lineTo(14f,21f); lineTo(10f,14f); lineTo(3f,10f); close(); moveTo(10f,14f); lineTo(21f,3f) }
    val Stop = icon("Stop") { moveTo(6f,6f); lineTo(18f,6f); lineTo(18f,18f); lineTo(6f,18f); close() }
    val Copy = icon("Copy") { moveTo(9f,9f); lineTo(20f,9f); lineTo(20f,21f); lineTo(9f,21f); close(); moveTo(15f,5f); lineTo(15f,3f); lineTo(3f,3f); lineTo(3f,15f); lineTo(5f,15f) }
    val Back = icon("Back") { moveTo(20f,12f); lineTo(4f,12f); moveTo(11f,5f); lineTo(4f,12f); lineTo(11f,19f) }
    val Down = icon("Latest") { moveTo(12f,4f); lineTo(12f,20f); moveTo(5f,13f); lineTo(12f,20f); lineTo(19f,13f) }
    val Earlier = icon("Earlier") { moveTo(12f,20f); lineTo(12f,4f); moveTo(5f,11f); lineTo(12f,4f); lineTo(19f,11f) }
    val ChevronDown = icon("Expand") { moveTo(6f,9f); lineTo(12f,15f); lineTo(18f,9f) }
    val ChevronUp = icon("Collapse") { moveTo(6f,15f); lineTo(12f,9f); lineTo(18f,15f) }
    val Plus = icon("Add") { moveTo(12f,5f); lineTo(12f,19f); moveTo(5f,12f); lineTo(19f,12f) }
    val Close = icon("Close") { moveTo(6f,6f); lineTo(18f,18f); moveTo(18f,6f); lineTo(6f,18f) }
    val Check = icon("Selected") { moveTo(5f,12f); lineTo(10f,17f); lineTo(20f,7f) }
    val Refresh = icon("Refresh") { moveTo(20f,7f); curveTo(16f,1f,6f,2f,4f,9f); moveTo(4f,17f); curveTo(8f,23f,18f,22f,20f,15f); moveTo(20f,3f); lineTo(20f,8f); lineTo(15f,8f); moveTo(4f,21f); lineTo(4f,16f); lineTo(9f,16f) }
    val Edit = icon("Rename") { moveTo(4f,20f); lineTo(5f,15f); lineTo(16f,4f); lineTo(20f,8f); lineTo(9f,19f); close(); moveTo(13f,7f); lineTo(17f,11f) }
    val Disconnect = icon("Disconnect") { moveTo(9f,4f); lineTo(4f,4f); lineTo(4f,20f); lineTo(9f,20f); moveTo(9f,12f); lineTo(21f,12f); moveTo(16f,7f); lineTo(21f,12f); lineTo(16f,17f) }
    val Sun = icon("Light mode") { moveTo(16f,12f); curveTo(16f,17.3f,8f,17.3f,8f,12f); curveTo(8f,6.7f,16f,6.7f,16f,12f); close(); moveTo(12f,2f); lineTo(12f,4f); moveTo(12f,20f); lineTo(12f,22f); moveTo(2f,12f); lineTo(4f,12f); moveTo(20f,12f); lineTo(22f,12f); moveTo(5f,5f); lineTo(6.5f,6.5f); moveTo(17.5f,17.5f); lineTo(19f,19f); moveTo(5f,19f); lineTo(6.5f,17.5f); moveTo(17.5f,6.5f); lineTo(19f,5f) }
    val Moon = icon("Dark mode") { moveTo(20f,15f); curveTo(12f,18f,6f,10f,10f,3f); curveTo(0f,6f,3f,22f,13f,21f); curveTo(17f,21f,19f,18f,20f,15f); close() }
    val Chat = icon("Chat") { moveTo(4f,4f); lineTo(20f,4f); lineTo(20f,17f); lineTo(9f,17f); lineTo(4f,21f); close(); moveTo(8f,9f); lineTo(16f,9f); moveTo(8f,13f); lineTo(13f,13f) }
    val Shield = icon("Approvals") { moveTo(12f,3f); lineTo(20f,6f); lineTo(20f,12f); curveTo(20f,17f,16f,20f,12f,22f); curveTo(8f,20f,4f,17f,4f,12f); lineTo(4f,6f); close(); moveTo(8f,12f); lineTo(11f,15f); lineTo(16f,9f) }
    val Terminal = icon("Live terminal") { moveTo(3f,5f); lineTo(21f,5f); lineTo(21f,19f); lineTo(3f,19f); close(); moveTo(7f,9f); lineTo(10f,12f); lineTo(7f,15f); moveTo(13f,15f); lineTo(17f,15f) }
    val Code = icon("Changes") { moveTo(8f,6f); lineTo(2f,12f); lineTo(8f,18f); moveTo(16f,6f); lineTo(22f,12f); lineTo(16f,18f); moveTo(14f,4f); lineTo(10f,20f) }
    val Model = icon("Model") { moveTo(12f,2f); lineTo(21f,7f); lineTo(21f,17f); lineTo(12f,22f); lineTo(3f,17f); lineTo(3f,7f); close(); moveTo(3f,7f); lineTo(12f,12f); lineTo(21f,7f); moveTo(12f,12f); lineTo(12f,22f) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = false, iconSize: Dp = 24.dp) {
    val contentColor = LocalContentColor.current
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        if (filled) FilledIconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))) { Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize)) }
        else IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = IconButtonDefaults.iconButtonColors(
            contentColor = contentColor, disabledContentColor = contentColor.copy(alpha = .45f))) { Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize)) }
    }
}
