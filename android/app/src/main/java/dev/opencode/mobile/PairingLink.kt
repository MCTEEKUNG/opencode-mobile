package dev.opencode.mobile

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class PairingLink(val url: HttpUrl, val origin: String) {
    companion object {
        fun parse(raw: String): PairingLink {
            val url = raw.trim().toHttpUrlOrNull() ?: error("Invalid pairing URL")
            require(url.isHttps) { "Use an HTTPS link from opencode pair --remote" }
            require(url.username.isEmpty() && url.password.isEmpty()) { "URL credentials are not allowed" }
            require(url.query == null && url.fragment == null) { "Unexpected query or fragment" }
            require(Regex("^/auth/connect/[A-Za-z0-9_-]+$").matches(url.encodedPath)) { "Not an OpenCode pairing link" }
            require(url.host !in setOf("localhost", "127.0.0.1", "::1")) { "PC localhost cannot be reached from this phone" }
            val origin = url.newBuilder().encodedPath("/").build().toString().removeSuffix("/")
            return PairingLink(url, origin)
        }
    }
}
