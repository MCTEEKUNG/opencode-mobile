package dev.opencode.mobile

import org.junit.Assert.*
import org.junit.Test

class PairingLinkTest {
    @Test fun acceptsHttpsPairingLink() {
        val link = PairingLink.parse(" https://pc.example:8443/auth/connect/abc_123-XYZ ")
        assertEquals("https://pc.example:8443", link.origin)
    }
    @Test fun rejectsUnsafeLinks() {
        listOf("http://pc.example/auth/connect/x", "https://user:pass@pc.example/auth/connect/x",
            "https://pc.example/auth/connect/x?token=oops", "https://pc.example/auth/connect/x#fragment",
            "https://localhost/auth/connect/x", "https://127.0.0.1/auth/connect/x",
            "https://pc.example/auth/connect/a/b", "https://pc.example/not-pairing",
            "https://pc.example/auth/connect/%2F").forEach { raw ->
            try { PairingLink.parse(raw); fail("Accepted unsafe URL") } catch (_: IllegalArgumentException) { }
        }
    }
}
