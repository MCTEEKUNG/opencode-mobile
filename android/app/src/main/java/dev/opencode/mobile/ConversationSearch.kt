package dev.opencode.mobile

import java.net.URLEncoder
import org.json.JSONObject

data class ConversationSearch(
    val query: String = "",
    val results: List<JSONObject> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Search the PC's entire history, retaining the query on every cursor page. */
fun conversationSearchPath(query: String, cursor: String? = null, limit: Int = 50, directory: String? = null): String {
    require(query.isNotBlank())
    require(limit > 0)
    fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    return "/api/session?order=desc&limit=$limit&search=${encode(query.trim())}" +
        (directory?.let { "&directory=${encode(it)}" } ?: "") +
        (cursor?.let { "&cursor=${encode(it)}" } ?: "")
}
