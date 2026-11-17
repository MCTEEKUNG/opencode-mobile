package dev.opencode.mobile

import org.json.JSONObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class PairingLink(val url: HttpUrl, val origin: String) {
    val usesVpnHttp: Boolean get() = usesHttp && isTailnetIpv4(url.host)
    val usesHttp: Boolean get() = !url.isHttps
    companion object {
        fun decodeQr(raw: String, preferVpn: Boolean = false): String = checkedUrl(raw, preferVpn).toString()
        private fun checkedUrl(raw: String, preferVpn: Boolean = false): HttpUrl {
            val input = raw.trim { it.isWhitespace() || it == '\uFEFF' || it == '\u200B' }
            val url = if (input.startsWith("{")) {
                val payload = try { JSONObject(input) } catch (_: Exception) {
                    throw IllegalArgumentException("The QR contains invalid pairing data. Scan a new OpenCode QR.")
                }
                val code = payload.opt("code") as? String
                require(code != null && Regex("^[A-Za-z0-9_-]+$").matches(code)) { "The QR has no valid pairing code." }
                val urls = payload.optJSONArray("urls")
                require(urls != null && urls.length() > 0) { "The QR has no server address." }
                val candidates = (0 until urls.length()).map { index ->
                    val origin = (urls.opt(index) as? String)?.toHttpUrlOrNull()
                        ?: throw IllegalArgumentException("The QR contains an invalid server address.")
                    require(origin.username.isEmpty() && origin.password.isEmpty() && origin.query == null && origin.fragment == null && origin.encodedPath == "/") { "The QR must contain server origins only." }
                    origin
                }
                val vpnOrigin = candidates.firstOrNull { it.isHttps && it.host.endsWith(".ts.net") }
                    ?: candidates.firstOrNull { isTailnetIpv4(it.host) }
                val origin = vpnOrigin.takeIf { preferVpn }
                    ?: candidates.firstOrNull { isLanIpv4(it.host) }
                    ?: candidates.firstOrNull { it.isHttps && !isLoopback(it.host) }
                    ?: candidates.first()
                origin.newBuilder().encodedPath("/auth/connect/$code").build()
            } else input.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid pairing URL")
            require(url.username.isEmpty() && url.password.isEmpty()) { "URL credentials are not allowed" }
            require(url.query == null && url.fragment == null) { "Unexpected query or fragment" }
            require(Regex("^/auth/connect/[A-Za-z0-9_-]+$").matches(url.encodedPath)) { "Not an OpenCode V2 pairing link" }
            return url
        }
        private fun isLoopback(host: String) = host in setOf("localhost", "127.0.0.1", "::1", "0.0.0.0", "::") || host.startsWith("127.")
        fun isTailnetIpv4(host: String): Boolean {
            val parts = host.split('.').map { it.toIntOrNull() ?: return false }
            return parts.size == 4 && parts.all { it in 0..255 } && parts[0] == 100 && parts[1] in 64..127
        }
        private fun isLanIpv4(host: String): Boolean {
            val parts = host.split('.').map { it.toIntOrNull() ?: return false }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            return parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) || (parts[0] == 192 && parts[1] == 168)
        }
        fun needsLanAddress(raw: String): Boolean = isLoopback(checkedUrl(raw).host)
        fun forLan(raw: String, computerIp: String): PairingLink {
            val original = checkedUrl(raw)
            val host = computerIp.trim()
            require(isLanIpv4(host) || isTailnetIpv4(host)) { "Enter the computer’s Wi-Fi or Tailscale IPv4 address without http:// or a port." }
            // Replace only the host. Keep the QR's port, protocol and single-use code intact.
            return parse(original.newBuilder().host(host).build().toString())
        }
        fun parse(raw: String): PairingLink {
            val url = checkedUrl(raw)
            require(!isLoopback(url.host)) { "This QR uses a local-only or wildcard address. Choose Same Wi-Fi and enter the computer's Wi-Fi IP address." }
            require(url.isHttps || isLanIpv4(url.host) || isTailnetIpv4(url.host)) { "HTTP requires a private Wi-Fi IP or a Tailscale IP over a VPN. Other connections require HTTPS." }
            val origin = url.newBuilder().encodedPath("/").build().toString().removeSuffix("/")
            return PairingLink(url, origin)
        }
    }
}
