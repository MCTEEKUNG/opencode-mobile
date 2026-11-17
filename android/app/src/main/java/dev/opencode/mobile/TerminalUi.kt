package dev.opencode.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONObject

internal data class TerminalActivity(
    val records: List<TerminalRecord>,
    val unavailable: List<String>,
    val refresh: () -> Unit,
)

// Keep polling while the user reads the chat so its Terminal indicator stays current.
@Composable
internal fun rememberTerminalActivity(
    computerId: String?,
    sessionId: String,
    messages: List<JSONObject>,
    connected: Boolean,
    fetchSnapshot: suspend (String) -> JSONObject,
    liveEvents: List<TerminalEvent>,
): TerminalActivity {
    val fetch by rememberUpdatedState(fetchSnapshot)
    var terminals by remember(computerId, sessionId) { mutableStateOf(emptyList<TerminalRecord>()) }
    var jobs by remember(computerId, sessionId) { mutableStateOf(emptyList<TerminalRecord>()) }
    var refresh by remember(computerId, sessionId) { mutableIntStateOf(0) }
    var offlineSources by remember(computerId, sessionId) { mutableStateOf(emptyList<String>()) }
    val records = remember(computerId, sessionId, messages, liveEvents, terminals, jobs) {
        terminalRecords(sessionId, messages, liveEvents, terminals, jobs).filter { it.isProcess }
    }
    LaunchedEffect(computerId, sessionId, connected, refresh) {
        if (!connected) return@LaunchedEffect
        val terminalPath = terminalListPath(sessionId)
        val jobsPath = terminalJobsPath(sessionId)
        var readTerminals = terminalPath != null
        var readJobs = jobsPath != null
        var terminalError = false
        var jobError = false
        while (readTerminals || readJobs) {
            val (terminalResult, jobResult) = coroutineScope {
                val terminalRequest = async { if (readTerminals && terminalPath != null) terminalRead { fetch(terminalPath) } else null }
                val jobRequest = async { if (readJobs && jobsPath != null) terminalRead { fetch(jobsPath) } else null }
                terminalRequest.await() to jobRequest.await()
            }
            terminalResult?.getOrNull()?.let { terminals = it.persistentTerminals(sessionId) }
            jobResult?.getOrNull()?.let { jobs = it.backgroundJobs() }
            if (terminalResult != null) terminalError = terminalResult.isFailure
            if (jobResult != null) jobError = jobResult.isFailure
            if (terminalResult?.exceptionOrNull().isUnsupportedTerminalRead()) readTerminals = false
            if (jobResult?.exceptionOrNull().isUnsupportedTerminalRead()) readJobs = false
            offlineSources = listOfNotNull(
                "terminal list".takeIf { terminalError },
                "background jobs".takeIf { jobError },
            )
            delay(4_000)
        }
    }
    return TerminalActivity(records, offlineSources) { refresh++ }
}

@Composable
internal fun LiveTerminalPanel(
    activity: TerminalActivity,
    computer: Computer?,
    connected: Boolean,
    canInterrupt: Boolean,
    onInterrupt: () -> Unit,
    canLoadEarlier: Boolean,
    onLoadEarlier: () -> Unit,
    modifier: Modifier = Modifier,
    fetchSnapshot: suspend (String) -> JSONObject,
    directory: String? = null,
) {
    val records = activity.records
    var selected by remember { mutableStateOf<TerminalRecord?>(null) }
    val running = records.count { it.phase.active }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live terminal", style = MaterialTheme.typography.titleMedium)
                Text(when {
                    !connected -> "Offline · showing last received activity"
                    running == 1 -> "1 active process"
                    running > 1 -> "$running active processes"
                    else -> "Commands, tools and background jobs"
                },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ActionIcon(AppIcons.Refresh, "Refresh terminal activity", activity.refresh, enabled = connected)
        }
        if (canInterrupt) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onInterrupt, enabled = connected) {
                Icon(AppIcons.Stop, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Interrupt session")
            }
            Text("Stops the current AI response", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (connected && activity.unavailable.isNotEmpty()) Text(
            "Unavailable: ${activity.unavailable.joinToString()}. Session activity is still shown below.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (records.isEmpty()) item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("No terminal activity here", style = MaterialTheme.typography.titleMedium)
                    Text("Open a command or tool here to see its output.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(records, key = TerminalRecord::key) { record ->
                TerminalActivityRow(record, connected) { selected = record }
            }
            if (canLoadEarlier) item {
                TextButton(onClick = onLoadEarlier, enabled = connected) { Text("Load earlier activity") }
            }
        }
    }
    selected?.let { initial ->
        val current = records.find { it.key == initial.key } ?: initial.copy(phase = TerminalPhase.UNKNOWN)
        TerminalOutputDialog(current, computer, connected, directory, fetchSnapshot, onClose = { selected = null })
    }
}

private fun Throwable?.isUnsupportedTerminalRead(): Boolean =
    this is ApiFailure && status in setOf(401, 403, 404, 405, 501)

private suspend fun terminalRead(block: suspend () -> JSONObject): Result<JSONObject> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

@Composable
private fun TerminalActivityRow(record: TerminalRecord, connected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (record.phase.active && connected) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(AppIcons.Code, null, Modifier.size(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.title.ifBlank { record.kind.label }, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val annotation = if (!connected && record.phase.active) "Last seen ${record.phase.label.lowercase()}"
                    else record.phase.label
                Text("${record.kind.label} · $annotation", style = MaterialTheme.typography.labelMedium,
                    color = if (record.phase == TerminalPhase.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                if (record.command.isNotBlank()) Text(record.command, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(AppIcons.ChevronDown, "Inspect output", Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TerminalOutputDialog(
    record: TerminalRecord, computer: Computer?, connected: Boolean, directory: String?,
    fetchSnapshot: suspend (String) -> JSONObject, onClose: () -> Unit,
) {
    val fetch by rememberUpdatedState(fetchSnapshot)
    var text by remember(record.key) { mutableStateOf("") }
    var cursor by remember(record.key) { mutableLongStateOf(0L) }
    var error by remember(record.key) { mutableStateOf(false) }
    var loading by remember(record.key) { mutableStateOf(false) }
    LaunchedEffect(record.key, connected, record.phase, record.shellId, record.ptyId, directory) {
        if (!connected) {
            loading = false
            return@LaunchedEffect
        }
        val ptyPath = terminalSnapshotPath(record)
        do {
            val path = ptyPath ?: shellOutputPath(record, directory, cursor) ?: break
            if (ptyPath == null && !record.phase.active && record.output.isNotBlank()) break
            loading = true
            val result = terminalRead { fetch(path) }
            loading = false
            result.getOrNull()?.optJSONObject("data")?.let { data ->
                if (ptyPath != null) text = boundedTerminalOutput(data.optString("text"))
                else {
                    val chunk = data.opt("output") as? String ?: ""
                    val next = data.optLong("cursor", cursor)
                    if (next > cursor) {
                        text = boundedTerminalOutput(text + chunk)
                        cursor = next
                    }
                }
            }
            error = result.isFailure
            if (!record.phase.active || result.exceptionOrNull().isUnsupportedTerminalRead()) break
            delay(1_500)
        } while (true)
    }
    val output = if (record.ptyId == null && !record.phase.active && record.output.isNotBlank()) record.output
        else text.ifBlank { record.output }
    val vertical = rememberScrollState()
    var follow by remember(record.key) { mutableStateOf(true) }
    LaunchedEffect(output, follow) {
        if (follow) vertical.scrollTo(vertical.maxValue)
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(record.title.ifBlank { record.kind.label }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.command.isNotBlank()) SelectionContainer {
                    Text(record.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (record.note.isNotBlank()) Text(record.note, style = MaterialTheme.typography.bodySmall,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (loading && output.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error) Text("Live output unavailable. Showing the last retained output.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (record.files.isNotEmpty()) {
                    Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        record.files.forEach { AttachmentPreview(it, computer, record.directory.ifBlank { directory.orEmpty() }, Modifier.fillMaxWidth()) }
                    }
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.heightIn(min = 80.dp, max = 260.dp).verticalScroll(vertical)
                        .horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        SelectionContainer {
                            Text(output.ifBlank { "No output yet." }, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall, softWrap = false)
                        }
                    }
                }
                if (record.phase.active && connected) FilterChip(follow, { follow = !follow }, { Text("Follow latest output") })
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
