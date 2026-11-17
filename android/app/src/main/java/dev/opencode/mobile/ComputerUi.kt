package dev.opencode.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ComputerSelector(state: MobileState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onOpen, modifier = modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(AppIcons.Computer, contentDescription = "Switch computer")
            Column(Modifier.weight(1f)) {
                Text(state.computer?.name ?: "Choose a computer", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ConnectionLabel(state)
            }
            Icon(AppIcons.ChevronDown, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComputerSwitcher(state: MobileState, onClose: () -> Unit, onSelect: (Computer) -> Unit, onSettings: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val computers = state.computers.filter { "${it.name} ${it.origin}".contains(query, ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Your computers", style = MaterialTheme.typography.headlineSmall)
            if (state.computers.size > 4) OutlinedTextField(query, { query = it }, placeholder = { Text("Find a computer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(computers, key = { it.id }) { computer ->
                    val current = state.computer?.id == computer.id
                    Surface(onClick = { onSelect(computer) }, shape = RoundedCornerShape(20.dp), color = if (current) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(AppIcons.Computer, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(computer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(computer.origin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (current) ConnectionLabel(state) else Text("Saved computer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (current) Icon(AppIcons.Check, contentDescription = "Current computer")

                        }
                    }
                }
            }
            if (computers.isEmpty()) Text(if (query.isBlank()) "Connect your first computer to get started." else "No matching computers.")
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(AppIcons.Settings, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Manage computers in Settings")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
