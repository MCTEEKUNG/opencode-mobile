package dev.opencode.mobile

import org.json.JSONObject
import java.util.Locale

internal const val PROJECTS_FOLDER = "OpenCode Projects"
internal const val PROJECT_MARKER = ".opencode-mobile-project"

data class DirectoryProject(
    val directory: String,
    val sessions: Int,
    val running: Int,
) {
    val name: String get() = directoryName(directory)
}

data class Projects(
    val directories: List<String> = emptyList(),
    val sessions: List<JSONObject> = emptyList(),
    val root: String? = null,
    val selected: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val failed: Boolean = false,
) {
    fun choices(working: Set<String>): List<DirectoryProject> {
        val groups = sessions.mapNotNull { session ->
            session.workDirectory()?.let { it to session.optString("id") }
        }
            .groupBy { directoryKey(it.first) }
        return (directories + groups.values.map { it.first().first }).distinctBy(::directoryKey)
            .map { path ->
                val rows = groups[directoryKey(path)].orEmpty()
                DirectoryProject(path, rows.size, rows.count { it.second in working })
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun forDirectory(directory: String): List<JSONObject> {
        val key = directoryKey(directory)
        return sessions.filter { it.workDirectory()?.let(::directoryKey) == key }
    }
}

internal fun JSONObject.workDirectory(): String? =
    optJSONObject("location")?.let { if (it.isNull("directory")) null else it.optString("directory").takeIf(String::isNotBlank) }

private val windowsDrivePath = Regex("^[A-Za-z]:[/\\\\]")

internal fun isWindowsPath(path: String): Boolean =
    path.startsWith("\\\\") || windowsDrivePath.containsMatchIn(path)

internal fun directoryKey(path: String): String = when {
    isWindowsPath(path) ->
        path.replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)
    else -> path.trimEnd('/').ifEmpty { "/" }
}

internal fun childDirectory(parent: String, name: String): String {
    val path = if (isWindowsPath(parent)) parent.replace('\\', '/') else parent
    val child = path.trimEnd('/') + '/' + name
    return if (isWindowsPath(parent) && '\\' in parent) child.replace('/', '\\') else child
}

internal fun directoryName(path: String, windows: Boolean = isWindowsPath(path)): String {
    val normalized = if (windows) path.replace('\\', '/') else path
    return normalized.trimEnd('/').substringAfterLast('/').ifBlank { path }
}

private val projectNamePattern = Regex("[\\p{L}\\p{M}\\p{N}_-][\\p{L}\\p{M}\\p{N} _.\\-]{0,63}")
private val windowsDeviceName = Regex("(?i)(CON|PRN|AUX|NUL|COM[0-9¹²³]|LPT[0-9¹²³])")

internal fun checkedProjectName(value: String): String {
    val name = value.trim()
    require(projectNamePattern.matches(name) && !name.endsWith('.') &&
        !windowsDeviceName.matches(name.substringBefore('.').trimEnd())) {
        "Use 1–64 letters, numbers, spaces, dots, dashes or underscores. Choose a folder name that works on your PC."
    }
    return name
}

internal fun projectInventoryChanged(eventType: String): Boolean = when (eventType) {
    "session.created", "session.forked", "session.deleted", "session.moved",
    "session.archived", "session.unarchived", "session.renamed" -> true
    else -> false
}
