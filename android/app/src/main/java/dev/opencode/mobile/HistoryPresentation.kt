package dev.opencode.mobile

import org.json.JSONObject

data class HistoryPresentation(val hidden: Boolean = false, val clearedThrough: Long = 0L) {
    fun visible(rows: List<JSONObject>): List<JSONObject> = if (hidden) emptyList() else rows
        .filter { clearedThrough == 0L || it.sessionTime("created") > clearedThrough }
        .sortedByDescending { it.sessionTime("updated").takeIf { time -> time > 0 } ?: it.sessionTime("created") }

    // OpenCode paginates by last update. Unseen rows with older updates cannot be newer creations.
    fun hasPotentialOlderPage(rows: List<JSONObject>): Boolean =
        clearedThrough == 0L || rows.lastOrNull()?.sessionTime("updated")?.let { it == 0L || it > clearedThrough } != false
}

fun JSONObject.sessionTime(key: String): Long = optJSONObject("time")?.optLong(key) ?: 0L
