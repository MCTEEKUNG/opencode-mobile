package dev.opencode.mobile

import org.json.JSONArray
import org.json.JSONObject

data class ComposerEntry(val name: String, val description: String = "")

internal enum class ComposerCatalogKind(val endpoint: String, val field: String) {
    COMMAND("command", "name"), SKILL("skill", "id"),
}

internal fun JSONObject.composerEntries(kind: ComposerCatalogKind): List<ComposerEntry> = rows()
    .mapNotNull { entry ->
        val id = entry.optString(kind.field)
        val pattern = if (kind == ComposerCatalogKind.COMMAND) commandId else skillId
        id.takeIf { pattern.matches(it) && it != "pair-mobile" }?.let {
            ComposerEntry(it, entry.optString("description"))
        }
    }.distinctBy(ComposerEntry::name).sortedBy(ComposerEntry::name)

private val commandId = Regex("[A-Za-z0-9_.:-]+")
private val skillId = Regex("[A-Za-z0-9_.-]+")
private val commandPattern = Regex("""^\s*/([A-Za-z0-9_.:-]+)(?:\s+|$)""")
private val skillPattern = Regex("""(?<!\S)@([A-Za-z0-9_.-]+)(?![A-Za-z0-9_./-])""")

internal fun commandName(text: String): String? = commandPattern.find(text)?.groupValues?.get(1)
internal fun hasSkillMention(text: String): Boolean = skillPattern.containsMatchIn(text)

internal data class ComposerSubmission(val endpoint: String, val body: JSONObject)

internal fun composerSubmission(
    text: String,
    attachments: List<ChatAttachment>,
    commands: List<ComposerEntry>,
    skills: List<ComposerEntry>,
): ComposerSubmission {
    val command = commandPattern.find(text)
    val name = command?.groupValues?.get(1)
    require(name == null || commands.any { it.name == name }) {
        if (name == "pair-mobile") "Use /pair-mobile in the computer's OpenCode terminal."
        else "This command is not registered on the PC. Choose an available command from the / menu."
    }
    val input = command?.let { text.substring(it.range.last + 1) } ?: text
    val body = promptBody(input, attachments)
    val knownSkills = skills.mapTo(hashSetOf(), ComposerEntry::name)
    val mentions = skillPattern.findAll(input).filter { it.groupValues[1] in knownSkills }
        .distinctBy { it.groupValues[1] }.toList()
    if (mentions.isNotEmpty()) body.put("skills", JSONArray().apply {
        for (match in mentions) put(JSONObject().put("id", match.groupValues[1]).put("mention", JSONObject()
            .put("start", match.range.first).put("end", match.range.last + 1).put("text", match.value)))
    })
    if (name != null) body.put("name", name)
    return ComposerSubmission(if (name == null) "prompt" else "command", body)
}
