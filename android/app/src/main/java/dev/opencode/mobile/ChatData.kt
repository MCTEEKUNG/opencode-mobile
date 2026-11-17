package dev.opencode.mobile

import org.json.JSONObject
import java.util.Locale

data class ChatModel(
    val id: String, val provider: String, val name: String,
    val variants: List<String> = emptyList(), val upstreamId: String? = null,
) {
    val key: String get() = "$provider/$id"
    fun reference(variant: String? = null): JSONObject {
        require(variant == null || variant in variants) { "This effort is not supported by the selected model." }
        return JSONObject().put("id", id).put("providerID", provider).apply { if (variant != null) put("variant", variant) }
    }
}
fun JSONObject.availableChatModels(): List<ChatModel> = rows()
    .filter { it.optBoolean("enabled", false) && it.optString("id").isNotBlank() && it.optString("providerID").isNotBlank() }
    .map { ChatModel(it.getString("id"), it.getString("providerID"), it.optString("name").ifBlank { it.getString("id") },
        it.optJSONArray("variants")?.objects().orEmpty().map { variant -> variant.optString("id") }.filter(String::isNotBlank).distinct(),
        (it.opt("modelID") as? String)?.takeIf(String::isNotBlank)) }
    .distinctBy { it.key }
fun JSONObject.chatProse(): String = when (optString("type")) {
    "user", "system", "synthetic" -> optString("text")
    "assistant" -> optJSONArray("content")?.objects().orEmpty().filter { it.optString("type") == "text" }.joinToString("\n\n") { it.optString("text") }
    else -> ""
}
internal fun JSONObject.chatReasoning(): String = optJSONArray("content")?.objects().orEmpty()
    .filter { it.optString("type") == "reasoning" }.map { it.optString("text") }
    .filter(String::isNotBlank).joinToString("\n\n")

fun JSONObject.chatVisible(): Boolean = when (optString("type")) {
    "assistant" -> chatProse().isNotBlank() || chatAttachments().isNotEmpty() || !isNull("error")
    "user", "system", "synthetic" -> chatProse().isNotBlank() || chatAttachments().isNotEmpty()
    "idle" -> optString("outcome") in setOf("interrupted", "failed")
    else -> false
}

/** Tokens spent by a finished assistant message and its generation speed. */
data class ChatUsage(val tokens: Int, val tokensPerSecond: Double)

fun JSONObject.chatUsage(): ChatUsage? {
    if (optString("type") != "assistant") return null
    val time = optJSONObject("time") ?: return null
    if (time.optLong("completed", 0) <= 0) return null
    val tokens = optJSONObject("tokens") ?: return null
    val cache = tokens.optJSONObject("cache")
    val generated = tokens.optInt("output", 0) + tokens.optInt("reasoning", 0)
    val total = tokens.optInt("input", 0) + generated + cache.optInt("read", 0) + cache.optInt("write", 0)
    if (total <= 0) return null
    val first = time.optLong("firstToken", 0)
    val end = time.optLong("streamed", 0).takeIf { it > first } ?: time.optLong("completed", 0)
    val start = if (first > 0) first else time.optLong("created", 0)
    val seconds = (end - start) / 1000.0
    return ChatUsage(total, if (generated > 0 && seconds > 0) generated / seconds else 0.0)
}

internal fun formatTokenCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> "%.1f".format(Locale.ROOT, value / 1000.0).removeSuffix(".0") + "k"
    else -> "%.1f".format(Locale.ROOT, value / 1_000_000.0).removeSuffix(".0") + "M"
}

internal fun formatTokensPerSecond(rate: Double): String =
    (if (rate >= 10) "%.0f".format(Locale.ROOT, rate) else "%.1f".format(Locale.ROOT, rate)) + " tok/s"

fun newSessionBody(directory: String?): JSONObject = JSONObject().apply {
    directory?.trim()?.takeIf { it.isNotEmpty() }?.let {
        require(it.startsWith("/") || isWindowsPath(it)) { "The saved computer directory is not an absolute path." }
        put("location", JSONObject().put("directory", it))
    }
}
