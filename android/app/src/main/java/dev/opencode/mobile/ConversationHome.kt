package dev.opencode.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun ConversationHome(
    state: MobileState, dark: Boolean, onNew: () -> Unit, onRefresh: () -> Unit,
    onSearch: (String) -> Unit, onRetrySearch: () -> Unit, onMore: () -> Unit,
    onOpen: (JSONObject) -> Unit, onDelete: (JSONObject) -> Unit, onToggleHistory: () -> Unit,
    onClearHistory: () -> Unit, onRestoreHistory: () -> Unit,
    onSelectProject: (String?) -> Unit, onCreateProject: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val history = state.history
    val connected = state.status == PeerStatus.CONNECTED.label
    val searching = state.search.query.isNotBlank()
    val directory = state.projects.selected
    val source = when {
        searching -> state.search.results
        directory != null -> remember(state.projects.sessions, directory) { state.projects.forDirectory(directory) }
        else -> state.sessions
    }
    val rows = remember(history, source) { history.visible(source) }
    var menu by remember(state.computer?.id) { mutableStateOf(false) }
    var confirmClear by remember(state.computer?.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        ProjectSelector(state, onSelectProject, onCreateProject, onRefresh)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Conversations", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            ActionIcon(if (history.hidden) AppIcons.ChevronDown else AppIcons.ChevronUp,
                if (history.hidden) "Show conversation history" else "Hide conversation history", onToggleHistory)
            Box {
                ActionIcon(AppIcons.More, "Conversation options", { menu = true })
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; onRefresh() }, enabled = !state.busy)
                    DropdownMenuItem(text = { Text("Clear history from home") },
                        onClick = { menu = false; confirmClear = true },
                        enabled = !state.busy && connected && state.sessions.isNotEmpty())
                    if (history.clearedThrough != 0L) DropdownMenuItem(text = { Text("Restore full history") },
                        onClick = { menu = false; onRestoreHistory() })
                }
            }
            ActionIcon(AppIcons.Plus, "New chat", onNew, enabled = !state.busy && connected, filled = true)
        }
        if (history.hidden) {
            GlassPanel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("History hidden", style = MaterialTheme.typography.titleMedium)
                Text("Start a new chat, or show your history when you need it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onToggleHistory) { Text("Show history") }
            }
        } else {
            ConversationSearchField(state.search.query, onSearch)
            if (searching && state.search.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            if (searching && state.search.error != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.search.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                ActionIcon(AppIcons.Refresh, "Retry search", onRetrySearch, enabled = !state.search.loading)
            }
            if (rows.isNotEmpty()) Text("Newest activity first", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            val listState = key(state.computer?.id, directory, state.search.query, history.clearedThrough) { rememberLazyListState() }
            LazyColumn(Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.isEmpty() && !(searching && (state.search.loading || state.search.error != null))) item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(when {
                            searching -> "No matching conversations"
                            directory != null && state.projects.failed && !state.projects.loaded -> "Could not load project sessions"
                            directory != null && !state.projects.loaded && connected -> "Loading project sessions…"
                            history.clearedThrough != 0L -> "A fresh start"
                            connected -> "No conversations yet"
                            else -> "Connecting to your computer..."
                        }, style = MaterialTheme.typography.titleMedium)
                        val message = when {
                            searching -> "Try a different title or clear your search."
                            directory != null && !state.projects.loaded -> null
                            history.clearedThrough != 0L -> "New chats will appear here. Your old chats are still on your computer."
                            directory != null -> "Start a new chat in this project."
                            else -> "Start a new chat to pick up where you left off."
                        }
                        if (message != null) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (history.clearedThrough != 0L) TextButton(onClick = onRestoreHistory) { Text("Restore full history") }
                    }
                }
                items(rows, key = { it.getString("id") }) { session ->
                    val id = session.getString("id")
                    SwipeConversation(session, state.busy || !connected, { onOpen(session) }, { onDelete(session) },
                        working = connected && id in state.workingSessions, dark = dark)
                }
                val nextPage = if (searching) state.search.nextCursor else if (directory == null) state.nextSessions else null
                if (nextPage != null && history.hasPotentialOlderPage(source)) item {
                    TextButton(onClick = onMore, enabled = !state.busy && !state.search.loading) { Text("Load more sessions") }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("Clear history from home?") },
        text = { Text("Existing chats will be hidden on this phone. New chats will still appear, and you can restore the full list anytime.") },
        confirmButton = { Button(onClick = { confirmClear = false; onClearHistory() }) { Text("Clear from home") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}
