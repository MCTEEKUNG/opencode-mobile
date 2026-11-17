package dev.opencode.mobile

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.URI

fun attachmentRequest(file: ChatAttachment, computer: Computer?, directory: String): Request {
        val base = computer?.origin?.toHttpUrlOrNull()
        val url = if (file.uri.startsWith("file:") || !file.uri.contains("://")) {
            require(base != null && directory.isNotBlank()) { "The computer directory is unavailable for this file." }
            val path = if (file.uri.startsWith("file:")) URI(file.uri).path else file.uri
            val root = directory.replace('\\', '/').trimEnd('/')
            val normalized = path.replace('\\', '/')
            val relative = if (normalized.startsWith("/")) {
                require(normalized.startsWith("$root/")) { "This file is outside the conversation folder." }
                normalized.removePrefix("$root/")
            } else normalized
            require(relative.split('/').none { it == ".." || it.isBlank() }) { "Invalid attachment path." }
            base.newBuilder().addPathSegments("api/fs/read").apply { relative.split('/').forEach { addPathSegment(it) } }
                .addQueryParameter("location[directory]", directory).build()
        } else file.uri.toHttpUrlOrNull() ?: error("Unsupported file address.")
        val sameServer = base != null && url.scheme == base.scheme && url.host == base.host && url.port == base.port
        require(url.isHttps || sameServer) { "External attachments require HTTPS." }
        return Request.Builder().url(url).apply {
            if (sameServer) header("Authorization", Credentials.basic("opencode", computer.token))
        }.build()
}
