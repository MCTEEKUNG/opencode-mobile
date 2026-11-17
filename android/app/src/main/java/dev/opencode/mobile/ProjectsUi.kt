package dev.opencode.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ProjectSelector(
    state: MobileState,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val projects = state.projects
    val connected = state.status == PeerStatus.CONNECTED.label
    val working = if (connected) state.workingSessions.keys else emptySet()
    val choices = remember(projects.directories, projects.sessions, working) { projects.choices(working) }
    val selected = projects.selected
    val current = selected?.let { path -> choices.find { directoryKey(it.directory) == directoryKey(path) } }
    var expanded by remember(state.computer?.id) { mutableStateOf(false) }
    var creating by remember(state.computer?.id) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 34.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcons.Folder, null, Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(current?.name ?: selected?.let { DirectoryProject(it, 0, 0).name } ?: "All projects",
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            selected == null && projects.loaded -> "${choices.size} " + if (choices.size == 1) "project" else "projects"
                            current != null && projects.loaded -> projectSummary(current)
                            projects.failed -> "Could not load projects"
                            projects.loading -> "Loading projects…"
                            selected != null -> selected
                            else -> "Work in directory"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(AppIcons.ChevronDown, "Choose project", Modifier.size(18.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 240.dp, max = 320.dp).heightIn(max = 360.dp)) {
                DropdownMenuItem(
                    text = { Text("All projects") },
                    leadingIcon = { Icon(AppIcons.Folder, null, Modifier.size(20.dp)) },
                    trailingIcon = { if (selected == null) Icon(AppIcons.Check, "Selected", Modifier.size(18.dp)) },
                    onClick = { expanded = false; onSelect(null) },
                )
                if (choices.isNotEmpty()) HorizontalDivider()
                choices.forEach { project ->
                    DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(project.directory, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                if (projects.loaded) Text(projectSummary(project), style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        trailingIcon = { if (selected != null && directoryKey(selected) == directoryKey(project.directory))
                            Icon(AppIcons.Check, "Selected", Modifier.size(18.dp)) },
                        onClick = { expanded = false; onSelect(project.directory) },
                    )
                }
                if (projects.failed) DropdownMenuItem(
                    text = { Text("Refresh projects") }, enabled = connected && !state.busy,
                    onClick = { expanded = false; onRefresh() },
                )
            }
        }
        ActionIcon(AppIcons.FolderPlus, "Create a new project", { creating = true },
            modifier = Modifier.padding(start = 4.dp), enabled = connected && !state.busy)
    }
    if (creating) CreateProjectDialog(projects.root, onDismiss = { creating = false }, onCreate = {
        creating = false
        onCreate(it)
    })
}

private fun projectSummary(project: DirectoryProject): String =
    "${project.sessions} " + (if (project.sessions == 1) "session" else "sessions") +
        if (project.running > 0) ", ${project.running} running" else ""

@Composable
private fun CreateProjectDialog(root: String?, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val validation = remember(name) { runCatching { checkedProjectName(name) } }
    val submit = { validation.getOrNull()?.let(onCreate); Unit }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a new project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Project name") },
                    isError = name.isNotBlank() && validation.isFailure,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }))
                val path = root ?: PROJECTS_FOLDER
                Text(when {
                    name.isNotBlank() && validation.isFailure -> validation.exceptionOrNull()?.message.orEmpty()
                    validation.isSuccess -> childDirectory(path, validation.getOrThrow())
                    else -> "The folder will be created on the PC in $path."
                }, style = MaterialTheme.typography.bodySmall, color = if (name.isNotBlank() && validation.isFailure)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = submit, enabled = validation.isSuccess) { Text("Create project") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
