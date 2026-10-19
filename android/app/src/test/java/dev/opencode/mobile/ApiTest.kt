package dev.opencode.mobile

import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class ApiTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: okhttp3.OkHttpClient
    private lateinit var origin: String
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("pc.example").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        transport = Api.http.newBuilder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .dns(object : Dns { override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1")) }).build()
        origin = server.url("/").newBuilder().host("pc.example").build().toString().removeSuffix("/")
    }
    @After fun teardown() { server.shutdown(); transport.connectionPool.evictAll(); transport.dispatcher.executorService.shutdown() }
    @Test fun pairingIsAnonymousAndAcceptsJson() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"token\":\"test-token\"}"))
        assertEquals("test-token", Api.redeem(PairingLink.parse("$origin/auth/connect/code"), transport))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/auth/connect/code", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Authorization"))
    }
    @Test fun promptUsesV2BodyAndBasicAuthentication() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"data\":{}}"))
        Api(Computer("pc", "PC", origin, "test-token"), transport)
            .post("/api/session/s1/prompt", JSONObject().put("text", "hello"))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/api/session/s1/prompt", request.path)
        assertEquals(Credentials.basic("opencode", "test-token"), request.getHeader("Authorization"))
        assertEquals("hello", JSONObject(request.body.readUtf8()).getString("text"))
        assertEquals(1, server.requestCount)
    }
    @Test fun refusesRedirects() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "$origin/other"))
        try {
            Api(Computer("pc", "PC", origin, "test-token"), transport).get("/api/info")
            fail("Redirect accepted")
        } catch (failure: ApiFailure) { assertEquals(302, failure.status) }
        assertEquals(1, server.requestCount)
    }
    @Test fun permission204HasNoJsonBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val result = Api(Computer("pc", "PC", origin, "test-token"), transport)
            .post("/api/session/s1/permission/r1/reply", JSONObject().put("decision", "once"))
        assertEquals(0, result.length())
    }
    @Test fun rendersV2AssistantContent() {
        val message = JSONObject("""{"id":"m1","type":"assistant","content":[{"type":"text","text":"hello"},{"type":"tool","tool":"shell"}]}""")
        assertTrue(message.displayText().contains("hello"))
        assertTrue(message.displayText().contains("shell"))
    }
}
