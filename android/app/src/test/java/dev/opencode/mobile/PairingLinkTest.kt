package dev.opencode.mobile

import org.junit.Assert.*
import org.junit.Test

class PairingLinkTest {
    @Test fun choosesTailnetOverLanWhenVpnIsActive() {
        val raw = """{"code":"test-code","urls":["http://192.168.1.20:28894","http://100.64.1.10:28894"]}"""
        assertEquals("http://192.168.1.20:28894", PairingLink.parse(raw).origin)
        val paired = PairingLink.parse(PairingLink.decodeQr(raw, preferVpn = true))
        assertEquals("http://100.64.1.10:28894", paired.origin)
        assertTrue(paired.usesVpnHttp)
    }
    @Test fun replacesLanWithTailnetAndPreservesPortAndPairingCode() {
        val paired = PairingLink.forLan("http://192.168.1.20:28894/auth/connect/test-code", "100.64.1.10")
        assertEquals("http://100.64.1.10:28894/auth/connect/test-code", paired.url.toString())
        assertThrows(IllegalArgumentException::class.java) { PairingLink.parse("http://100.128.0.1/auth/connect/test-code") }
    }
    @Test fun acceptsStructuredOpenCodeQr() {
        val raw = """{"code":"test-code_123","urls":["http://192.168.1.20:49374"]}"""
        assertEquals("http://192.168.1.20:49374/auth/connect/test-code_123", PairingLink.parse(raw).url.toString())
        assertFalse(PairingLink.needsLanAddress(raw))
    }
    @Test fun replacesLocalhostInStructuredQr() {
        val raw = """{"code":"test-code","urls":["http://localhost:49374"]}"""
        assertTrue(PairingLink.needsLanAddress(raw))
        assertEquals("http://192.168.1.20:49374/auth/connect/test-code", PairingLink.forLan(raw, "192.168.1.20").url.toString())
    }
    @Test fun prefersReachableLanOriginFromStructuredQr() {
        val raw = """{"code":"test-code","urls":["http://0.0.0.0:49374","http://192.168.1.20:49374"]}"""
        assertEquals("http://192.168.1.20:49374", PairingLink.parse(raw).origin)
    }
    @Test fun rejectsMalformedStructuredQr() {
        for (raw in listOf("{", "{}", """{"code":"x/y","urls":["http://192.168.1.20"]}""", """{"code":"x","urls":[]}""", """{"code":"x","urls":["http://user:pass@192.168.1.20"]}""", """{"code":"x","urls":["http://192.168.1.20/other"]}""")) {
            assertThrows(IllegalArgumentException::class.java) { PairingLink.parse(raw) }
        }
    }
    @Test fun acceptsHttpsPairingLink() {
        val link = PairingLink.parse(" https://pc.example:8443/auth/connect/abc_123-XYZ ")
        assertEquals("https://pc.example:8443", link.origin)
    }
    @Test fun replacesOnlyLocalhostAndPreservesPortAndCode() {
        for (host in listOf("localhost", "127.0.0.1", "[::1]", "0.0.0.0", "[::]")) {
            val raw = "http://$host:45278/auth/connect/abc_123-XYZ"
            assertTrue(PairingLink.needsLanAddress(raw))
            val link = PairingLink.forLan(raw, " 192.168.1.20 ")
            assertEquals("http://192.168.1.20:45278/auth/connect/abc_123-XYZ", link.url.toString())
            assertEquals("http://192.168.1.20:45278", link.origin)
            assertTrue(link.usesHttp)
        }
    }
    @Test fun acceptsQrWithSurroundingInvisibleFormatting() {
        val raw = "\uFEFF\u200B http://0.0.0.0:49374/auth/connect/code \n"
        assertTrue(PairingLink.needsLanAddress(raw))
        assertEquals("http://192.168.1.20:49374/auth/connect/code", PairingLink.forLan(raw, "192.168.1.20").url.toString())
    }
    @Test fun malformedQrStillCannotBeRewritten() {
        for (raw in listOf("", "not a URL", "http://:49374/auth/connect/code")) {
            assertThrows(IllegalArgumentException::class.java) { PairingLink.forLan(raw, "192.168.1.20") }
        }
    }
    @Test fun acceptsPrivateWifiRanges() {
        for (host in listOf("10.0.0.2", "172.16.0.2", "172.31.255.2", "192.168.1.20")) {
            assertTrue(PairingLink.parse("http://$host:45278/auth/connect/code").usesHttp)
        }
        assertFalse(PairingLink.needsLanAddress("http://192.168.1.20/auth/connect/code"))
    }
    @Test fun rejectsInvalidReplacementAddresses() {
        for (host in listOf("", "localhost", "127.0.0.1", "0.0.0.0", "8.8.8.8", "192.168.1.256", "192.168.1", "192.168.1.2:1234", "http://192.168.1.2", "192.168.1.2/evil", "user@192.168.1.2")) {
            assertThrows(IllegalArgumentException::class.java) { PairingLink.forLan("http://localhost:45278/auth/connect/code", host) }
        }
    }
    @Test fun preservesHttpsAndValidatesQrBeforeReplacement() {
        assertEquals("https://192.168.1.20:8443/auth/connect/code", PairingLink.forLan("https://localhost:8443/auth/connect/code", "192.168.1.20").url.toString())
        assertThrows(IllegalArgumentException::class.java) { PairingLink.forLan("http://localhost/not-pairing", "192.168.1.20") }
    }
    @Test fun rejectsUnsafeLinks() {
        listOf("http://pc.example/auth/connect/x", "http://8.8.8.8/auth/connect/x", "http://172.32.0.1/auth/connect/x",
            "https://user:pass@pc.example/auth/connect/x", "https://pc.example/auth/connect/x?token=oops", "https://pc.example/auth/connect/x#fragment",
            "https://localhost/auth/connect/x", "https://127.0.0.1/auth/connect/x", "http://127.0.0.2/auth/connect/x",
            "https://pc.example/auth/connect/a/b", "https://pc.example/not-pairing", "https://pc.example/auth/connect/%2F").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { PairingLink.parse(raw) }
        }
    }
}
