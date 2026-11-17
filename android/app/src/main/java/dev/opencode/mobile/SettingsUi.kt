package dev.opencode.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: MobileState, themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit, onSelect: (Computer) -> Unit, onAdd: () -> Unit,
    onRename: (Computer) -> Unit, onRemove: (Computer) -> Unit,
    onReconnect: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(AppIcons.Back, "Back from settings", onBack)
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.selectableGroup()) {
                        ThemeOption("Follow system", AppIcons.SystemTheme, themeMode == ThemeMode.SYSTEM) { onThemeChange(ThemeMode.SYSTEM) }
                        Text("Match your phone's appearance automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 36.dp, bottom = 8.dp))
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ThemeOption("Light mode", AppIcons.Sun, themeMode == ThemeMode.LIGHT) { onThemeChange(ThemeMode.LIGHT) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ThemeOption("Dark mode", AppIcons.Moon, themeMode == ThemeMode.DARK) { onThemeChange(ThemeMode.DARK) }
                    }
                }
            }
            item {
                Text("Computers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 16.dp))
            }
            item { ConnectionWarning(state, onReconnect) }
            if (state.computers.isEmpty()) item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("No saved computers", style = MaterialTheme.typography.titleMedium)
                    Text("Connect a computer to bring your conversations here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.computers, key = { it.id }) { computer ->
                val current = state.computer?.id == computer.id
                GlassPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(AppIcons.Computer, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(computer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(computer.origin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (current) ConnectionLabel(state) else Text("Saved computer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current) Icon(AppIcons.Check, contentDescription = "Current computer")
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (current) Text("Current computer", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        else TextButton(onClick = { onSelect(computer) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Use this computer") }
                        ActionIcon(AppIcons.Edit, "Rename ${computer.name}", { onRename(computer) }, enabled = !state.busy)
                        ActionIcon(AppIcons.Trash, "Remove ${computer.name}", { onRemove(computer) }, enabled = !state.busy)
                    }
                }
            }
            item {
                OutlinedButton(onClick = onAdd, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(AppIcons.Computer, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Connect another computer")
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, icon: ImageVector, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}
