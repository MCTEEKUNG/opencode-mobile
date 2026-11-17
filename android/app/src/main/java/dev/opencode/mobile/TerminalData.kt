package dev.opencode.mobile

import org.json.JSONObject
import java.net.URLEncoder

private const val OUTPUT_LIMIT = 24_000
private const val ACTIVITY_LIMIT = 150
private val remoteId = Regex("^[A-Za-z0-9_-]{1,180}$")
private val terminalEscapes = Regex("\\u001b\\][^\\u0007\\u001b]*(?:\\u0007|\\u001b\\\\)|\\u001b\\[[0-?]*[ -/]*[@-~]|\\u001b[@-_]")

internal fun boundedTerminalOutput(value: String): String = buildString {
    for (char in terminalEscapes.replace(value.takeLast(OUTPUT_LIMIT), "").replace("\r\n", "\n")) {
        when {
            char == '\r' -> append('\n')
            char == '\n' || char == '\t' || !char.isISOControl() -> append(char)
        }
    }
}

enum class TerminalPhase(val label: String, val active: Boolean = false) {
    QUEUED("Preparing", true), RUNNING("Running", true), COMPLETE("Finished"), FAILED("Failed"), STOPPED("Stopped"), UNKNOWN("Last seen"),
}

enum class TerminalKind(val label: String) {
    TOOL("Tool"), SHELL("Shell"), PTY("Terminal"), JOB("Background job"), STEP("Model step"), EXECUTION("Session"),
}

data class TerminalRecord(
    val key: String,
    val kind: TerminalKind,
    val title: String = "",
    val phase: TerminalPhase = TerminalPhase.UNKNOWN,
    val created: Long = 0L,
    val command: String = "",
    val directory: String = "",
    val output: String = "",
    val note: String = "",
    val shellId: String? = null,
    val ptyId: String? = null,
    val files: List<ChatAttachment> = emptyList(),
) {
    val isProcess: Boolean get() = kind != TerminalKind.STEP && kind != TerminalKind.EXECUTION
}

data class TerminalEvent(val sessionId: String, val record: TerminalRecord)

private fun JSONObject.string(key: String): String = opt(key) as? String ?: ""
private fun JSONObject.id(key: String): String? = string(key).takeIf(remoteId::matches)
private fun JSONObject.eventKey(key: String): String? = string(key).takeIf {
    it.isNotBlank() && it.length <= 240 && it.none(Char::isISOControl)
}
private fun JSONObject.command() = string("command").take(4_000)
private fun JSONObject.errorText(): String =
    optJSONObject("error")?.let { it.string("message").ifBlank { it.string("type") } }.orEmpty()
        .ifBlank { string("error") }.take(4_000)

private fun JSONObject.toolOutput(): String = optJSONArray("content")?.objects().orEmpty()
    .asSequence().filter { it.string("type") == "text" }.map { it.string("text").takeLast(OUTPUT_LIMIT) }
    .takeLastOutputs().ifBlank { boundedTerminalOutput(string("output")) }

private fun JSONObject.toolFiles(): List<ChatAttachment> = outputFileParts().mapNotNull { it.asChatAttachment() }

private fun Sequence<String>.takeLastOutputs(): String {
    var result = ""
    for (chunk in this) result = (if (result.isEmpty()) chunk else "$result\n$chunk").takeLast(OUTPUT_LIMIT)
    return boundedTerminalOutput(result)
}

private fun phase(raw: String): TerminalPhase = when (raw) {
    "streaming", "pending", "queued" -> TerminalPhase.QUEUED
    "started", "running", "in_progress" -> TerminalPhase.RUNNING
    "completed", "exited", "succeeded", "success" -> TerminalPhase.COMPLETE
    "killed", "cancelled", "interrupted" -> TerminalPhase.STOPPED
    "error", "failed", "timeout" -> TerminalPhase.FAILED
    else -> TerminalPhase.UNKNOWN
}

private fun shellPhase(shell: JSONObject) = if (shell.string("status") == "exited" &&
    shell.has("exit") && shell.optInt("exit") != 0) TerminalPhase.FAILED else phase(shell.string("status"))

/** Decode only server-defined process events. Prompt/response text and raw event metadata are never copied. */
fun terminalEvent(payload: String): TerminalEvent? {
    val raw = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    val type = raw.string("type")
    if (!(type.startsWith("session.tool.") || type.startsWith("session.shell.") ||
            type.startsWith("session.step.") || type.startsWith("session.execution."))) return null
    val data = raw.optJSONObject("data") ?: return null
    val sid = data.id("sessionID") ?: return null
    val created = raw.optLong("created")
    val record = when (type) {
        "session.execution.started", "session.execution.succeeded", "session.execution.failed",
        "session.execution.interrupted" -> {
            val id = raw.eventKey("id") ?: return null
            TerminalRecord("execution:$id", TerminalKind.EXECUTION, "Session execution",
                phase(type.substringAfterLast('.')), created, note = data.errorText())
        }
        "session.shell.started", "session.shell.ended" -> {
            val shell = data.optJSONObject("shell") ?: return null
            val id = shell.id("id") ?: return null
            val output = data.optJSONObject("output")
            TerminalRecord("shell:$id", TerminalKind.SHELL, "Shell command", shellPhase(shell),
                shell.optJSONObject("time")?.optLong("started")?.takeIf { it > 0 } ?: created,
                shell.command(), shell.string("cwd"), boundedTerminalOutput(output?.string("output").orEmpty()),
                if (output?.optBoolean("truncated") == true) "OpenCode retained a partial output." else "",
                shellId = id)
        }
        "session.step.started", "session.step.ended", "session.step.failed" -> {
            val id = data.id("assistantMessageID") ?: return null
            val state = when (type) {
                "session.step.started" -> TerminalPhase.RUNNING
                "session.step.failed" -> TerminalPhase.FAILED
                else -> TerminalPhase.COMPLETE
            }
            TerminalRecord("step:$id", TerminalKind.STEP, data.string("agent").ifBlank { "Model step" },
                state, created, note = data.errorText().ifBlank {
                    data.optJSONObject("model")?.string("id").orEmpty().take(200)
                })
        }
        "session.tool.input.started", "session.tool.called", "session.tool.progress", "session.tool.success", "session.tool.failed" -> {
            val messageId = data.id("assistantMessageID") ?: return null
            val toolId = data.eventKey("id") ?: return null
            val metadata = data.optJSONObject("metadata")
            val state = when (type) {
                "session.tool.input.started" -> TerminalPhase.QUEUED
                "session.tool.success" -> TerminalPhase.COMPLETE
                "session.tool.failed" -> TerminalPhase.FAILED
                "session.tool.progress" -> phase(metadata?.string("status").orEmpty()).let {
                    if (it == TerminalPhase.UNKNOWN) TerminalPhase.RUNNING else it
                }
                else -> TerminalPhase.RUNNING
            }
            TerminalRecord("tool:$messageId:$toolId", TerminalKind.TOOL, data.string("name").take(200),
                state, created, data.optJSONObject("input")?.command().orEmpty(),
                output = data.toolOutput().ifBlank { boundedTerminalOutput(metadata?.string("output").orEmpty()) },
                note = data.errorText(), shellId = metadata?.id("shellID"), files = data.toolFiles())
        }
        else -> return null
    }
    return TerminalEvent(sid, record)
}

fun terminalRecords(
    sessionId: String,
    messages: List<JSONObject>,
    events: List<TerminalEvent>,
    terminals: List<TerminalRecord> = emptyList(),
    jobs: List<TerminalRecord> = emptyList(),
): List<TerminalRecord> {
    val records = linkedMapOf<String, TerminalRecord>()
    fun add(next: TerminalRecord) {
        val previous = records[next.key]
        records[next.key] = if (previous == null) next else next.copy(
            title = next.title.ifBlank { previous.title },
            phase = when {
                next.phase == TerminalPhase.UNKNOWN -> previous.phase
                next.phase.active && !previous.phase.active && previous.phase != TerminalPhase.UNKNOWN -> previous.phase
                else -> next.phase
            },
            created = listOf(previous.created, next.created).filter { it > 0 }.minOrNull() ?: 0,
            command = next.command.ifBlank { previous.command },
            directory = next.directory.ifBlank { previous.directory },
            output = next.output.ifBlank { previous.output },
            note = next.note.ifBlank { previous.note },
            shellId = next.shellId ?: previous.shellId,
            ptyId = next.ptyId ?: previous.ptyId,
            files = (previous.files + next.files).distinctBy { it.uri },
        )
    }
    for (message in messages) {
        val id = message.id("id") ?: continue
        val time = message.optJSONObject("time")?.optLong("created") ?: 0L
        if (message.string("type") == "shell") {
            val shell = message.optJSONObject("shell")
            val shellId = message.id("shellID") ?: shell?.id("id")
            val output = message.optJSONObject("output")
            add(TerminalRecord(shellId?.let { "shell:$it" } ?: "shell-message:$id", TerminalKind.SHELL,
                "Shell command", shellPhase(shell ?: message), time, message.command().ifBlank { shell?.command().orEmpty() },
                shell?.string("cwd").orEmpty(), boundedTerminalOutput(output?.string("output") ?: message.string("output")),
                note = if (output?.optBoolean("truncated") == true) "OpenCode retained a partial output." else "",
                shellId = shellId, files = message.chatAttachments()))
        }
        for (tool in message.optJSONArray("content")?.objects().orEmpty()) {
            if (tool.string("type") != "tool") continue
            val toolId = tool.eventKey("id") ?: continue
            val state = tool.optJSONObject("state") ?: continue
            val metadata = state.optJSONObject("metadata")
            add(TerminalRecord("tool:$id:$toolId", TerminalKind.TOOL, tool.string("name").take(200),
                phase(state.string("status")), tool.optJSONObject("time")?.optLong("created")?.takeIf { it > 0 } ?: time,
                state.optJSONObject("input")?.command().orEmpty(),
                output = state.toolOutput().ifBlank { boundedTerminalOutput(metadata?.string("output").orEmpty()) },
                note = state.errorText(), shellId = metadata?.id("shellID"), files = state.toolFiles()))
        }
    }
    events.forEach { event ->
        if (event.sessionId != sessionId) return@forEach
        val next = event.record
        if (next.kind == TerminalKind.EXECUTION) {
            records.entries.removeAll { it.value.kind == TerminalKind.EXECUTION && it.value.phase.active }
            if (!next.phase.active) {
                // A cancelled or failed turn may never get a separate result for its in-flight tool.
                records.replaceAll { _, row ->
                    if (row.kind in setOf(TerminalKind.STEP, TerminalKind.TOOL) && row.phase.active && row.created <= next.created)
                        row.copy(phase = if (next.phase == TerminalPhase.COMPLETE) TerminalPhase.UNKNOWN else next.phase)
                    else row
                }
            }
        }
        add(next)
    }
    // Background shell jobs and direct shell events can describe the same process.
    for (job in jobs) {
        val existing = job.shellId?.let { shellId -> records.values.firstOrNull { it.shellId == shellId } }
        add(if (existing != null) job.copy(key = existing.key, kind = existing.kind, title = existing.title) else job)
    }
    terminals.forEach(::add)
    return records.values.sortedWith(compareByDescending<TerminalRecord> { it.phase.active }.thenByDescending { it.created })
        .take(ACTIVITY_LIMIT)
}

internal fun JSONObject.persistentTerminals(sessionId: String): List<TerminalRecord> = rows().mapNotNull { item ->
    if (item.string("sessionID") != sessionId) return@mapNotNull null
    val id = item.id("id") ?: return@mapNotNull null
    TerminalRecord("pty:$id", TerminalKind.PTY, item.string("userTitle").ifBlank { item.string("title") }.take(200),
        phase(item.string("status")), command = item.command(), directory = item.string("cwd"),
        note = item.string("foregroundProcess").take(200), ptyId = id)
}

internal fun JSONObject.backgroundJobs(): List<TerminalRecord> = rows().mapNotNull { item ->
    val id = item.eventKey("id") ?: return@mapNotNull null
    val kind = item.string("kind")
    if (kind !in setOf("shell", "subagent", "image")) return@mapNotNull null
    TerminalRecord("job:$id", TerminalKind.JOB, item.string("title").ifBlank {
        when (kind) { "shell" -> "Background shell"; "subagent" -> "Subagent"; else -> "Image generation" }
    }.take(200), phase(item.string("status")), item.optJSONObject("time")?.optLong("started") ?: 0L,
        item.command(), note = item.string("description").take(4_000), shellId = item.id("shellID"))
}

internal fun terminalListPath(sessionId: String): String? =
    sessionId.takeIf(remoteId::matches)?.let { "/api/experimental/session/$it/terminal" }

internal fun terminalJobsPath(sessionId: String): String? =
    sessionId.takeIf(remoteId::matches)?.let { "/api/experimental/job?sessionID=$it&descendants=true" }

internal fun terminalSnapshotPath(record: TerminalRecord): String? =
    record.ptyId?.takeIf(remoteId::matches)?.let { "/api/experimental/persistent-pty/$it/snapshot" }

internal fun shellOutputPath(record: TerminalRecord, directory: String?, cursor: Long): String? =
    record.shellId?.takeIf(remoteId::matches)?.let { id ->
        val location = record.directory.ifBlank { directory.orEmpty() }
        // Child jobs can belong to a different directory; never guess the location of a background job.
        if (location.isBlank() || record.kind == TerminalKind.JOB && record.directory.isBlank()) return null
        "/api/shell/$id/output?cursor=$cursor&limit=8192&location%5Bdirectory%5D=" + URLEncoder.encode(location, "UTF-8")
    }
