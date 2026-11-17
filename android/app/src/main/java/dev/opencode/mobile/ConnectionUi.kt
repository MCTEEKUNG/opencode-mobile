package dev.opencode.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionLabel(state: MobileState, showComputer: Boolean = false) {
    val color = when (state.status) {
        PeerStatus.CONNECTED.label -> connectedColor(MaterialTheme.colorScheme)
        PeerStatus.LOST.label, PeerStatus.AUTH_REQUIRED.label -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text((if (showComputer && state.computer != null) "${state.computer.name} · " else "") + state.status,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun ConnectionWarning(state: MobileState, onReconnect: () -> Unit) {
    if (state.computer == null || state.status !in listOf(PeerStatus.LOST.label, PeerStatus.AUTH_REQUIRED.label)) return
    Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${state.computer.name}: ${state.status}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (state.status == PeerStatus.AUTH_REQUIRED.label) "Access expired or was revoked. Pair this computer again." else "Reconnecting automatically. Your draft is kept.", style = MaterialTheme.typography.bodySmall)
            }
            ActionIcon(AppIcons.Refresh, "Check connection now", onReconnect)
        }
    }
}
