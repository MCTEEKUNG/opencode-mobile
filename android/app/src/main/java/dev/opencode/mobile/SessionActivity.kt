package dev.opencode.mobile

import org.json.JSONObject

enum class WorkKind { RESPONSE, SHELL }

data class SessionActivity(val sessionId: String, val kind: WorkKind, val working: Boolean) {
    fun reduce(active: Map<String, Set<WorkKind>>): Map<String, Set<WorkKind>> {
        val current = active[sessionId].orEmpty()
        val next = if (working) current + kind else current - kind
        return if (next.isEmpty()) active - sessionId else active + (sessionId to next)
    }
}

sealed interface ServerSignal {
    data object Open : ServerSignal
    data object Refresh : ServerSignal
    data object ProjectInventory : ServerSignal
    data class Activity(val update: SessionActivity) : ServerSignal
    data class Terminal(val event: TerminalEvent, val activity: SessionActivity?) : ServerSignal
}

fun sessionActivity(payload: String): SessionActivity? = runCatching {
    val event = JSONObject(payload)
    val id = event.optJSONObject("data")?.optString("sessionID").orEmpty()
    if (id.isBlank()) return null
    when (event.optString("type")) {
        "session.execution.started", "session.step.started", "session.text.delta",
        "session.reasoning.delta", "session.retry.scheduled" -> SessionActivity(id, WorkKind.RESPONSE, true)
        "session.execution.succeeded", "session.execution.failed", "session.execution.interrupted",
        "session.idle" -> SessionActivity(id, WorkKind.RESPONSE, false)
        "session.shell.started" -> SessionActivity(id, WorkKind.SHELL, true)
        "session.shell.ended" -> SessionActivity(id, WorkKind.SHELL, false)
        else -> null
    }
}.getOrNull()
