package dev.opencode.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class ComposerToken(val symbol: Char, val start: Int, val end: Int, val query: String) {
    fun replace(text: String, name: String): TextFieldValue {
        val replacement = "$symbol$name"
        val hasTrailingSpace = text.getOrNull(end)?.isWhitespace() == true
        val inserted = if (hasTrailingSpace) replacement else "$replacement "
        return TextFieldValue(text.replaceRange(start, end, inserted),
            selection = TextRange(start + replacement.length + 1))
    }
}

private fun Char.isCatalogNameCharacter() = this in 'a'..'z' || this in 'A'..'Z' ||
    this in '0'..'9' || this in "_.-"

internal fun composerToken(text: String, caret: Int): ComposerToken? {
    if (caret !in 1..text.length) return null
    var start = caret
    while (start > 0 && (text[start - 1].isCatalogNameCharacter() || text[start - 1] == ':')) start--
    val symbol = text.getOrNull(start - 1) ?: return null
    if (symbol != '/' && symbol != '@') return null
    start--
    if (start > 0 && !text[start - 1].isWhitespace()) return null
    if (symbol == '/' && text.take(start).any { !it.isWhitespace() }) return null
    if (symbol == '@' && text.substring(start + 1, caret).contains(':')) return null
    var end = caret
    while (end < text.length && (text[end].isCatalogNameCharacter() || (symbol == '/' && text[end] == ':'))) end++
    // Don't suggest names inside file paths, emails, or marked-up text.
    if (end < text.length && !text[end].isWhitespace() && text[end] !in ",;:!?)]}") return null
    return ComposerToken(symbol, start, end, text.substring(start + 1, caret))
}

@Composable
internal fun ComposerSuggestions(
    token: ComposerToken,
    entries: List<ComposerEntry>,
    loading: Boolean,
    error: String?,
    onSelect: (ComposerEntry) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    canRetry: Boolean,
) {
    val commands = token.symbol == '/'
    val matches = remember(token.query, entries) {
        entries.filter { it.name.contains(token.query, ignoreCase = true) ||
            it.description.contains(token.query, ignoreCase = true) }
            .sortedWith(compareByDescending<ComposerEntry> { it.name.startsWith(token.query, ignoreCase = true) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (commands) "Commands" else "Skills on your computer",
                    Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.labelLarge)
                ActionIcon(AppIcons.Close, "Dismiss suggestions", onClose)
            }
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                error != null -> {
                    Text(error, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (canRetry) TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 4.dp)) { Text("Try again") }
                }
                matches.isEmpty() -> Text(
                    if (token.query.isEmpty()) {
                        if (commands) "No commands available for this project." else "No skills available for this project."
                    } else {
                        if (commands) "No matching commands." else "No matching skills."
                    },
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 136.dp)) {
                    items(matches, key = { it.name }) { entry ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("${token.symbol}${entry.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (entry.description.isNotBlank()) Text(entry.description, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { onSelect(entry) },
                        )
                    }
                }
            }
        }
    }
}
