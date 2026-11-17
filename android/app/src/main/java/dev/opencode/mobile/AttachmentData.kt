package dev.opencode.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class ChatAttachment(val name: String, val mime: String, val uri: String) {
    val image: Boolean get() = mime.startsWith("image/")
    fun input(): JSONObject = JSONObject().put("uri", uri).put("name", name)
    companion object {
        const val MAX_BYTES = 15 * 1024 * 1024
        fun inline(name: String, mime: String, bytes: ByteArray): ChatAttachment {
            require(bytes.size <= MAX_BYTES) { "Each attachment must be 15 MB or smaller." }
            val safeMime = mime.takeIf { Regex("^[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+$").matches(it) } ?: "application/octet-stream"
            return ChatAttachment(name, safeMime, "data:$safeMime;base64,${Base64.getEncoder().encodeToString(bytes)}")
        }
    }
}
internal fun JSONObject.asChatAttachment(): ChatAttachment? {
    val mime = optString("mime").ifBlank { "application/octet-stream" }
    val uri = (opt("data") as? String)?.takeIf(String::isNotBlank)?.let { "data:$mime;base64,$it" }
        ?: (opt("uri") as? String)?.takeIf(String::isNotBlank) ?: return null
    return ChatAttachment(optString("name").ifBlank { if (mime.startsWith("image/")) "Image" else "Attachment" }, mime, uri)
}

fun JSONObject.chatAttachments(): List<ChatAttachment> =
    (optJSONArray("files")?.objects().orEmpty() + outputFileParts()).mapNotNull { it.asChatAttachment() }.distinctBy { it.uri }

internal fun JSONObject.outputFileParts(): List<JSONObject> =
    optJSONArray("content")?.objects().orEmpty().filter { it.optString("type") == "file" }
fun promptBody(text: String, attachments: List<ChatAttachment>): JSONObject = JSONObject().put("text", text).apply {
    if (attachments.isNotEmpty()) put("files", JSONArray().apply { attachments.forEach { put(it.input()) } })
}

fun readAttachmentBytes(input: java.io.InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= ChatAttachment.MAX_BYTES) { "Each attachment must be 15 MB or smaller." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
