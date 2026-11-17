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
        transport = Api.http.newBuilder()
            .dispatcher(okhttp3.Dispatcher()).connectionPool(okhttp3.ConnectionPool())
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
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
    @Test fun projectCreationWritesInThePcDefaultDirectoryAndVerifiesLocation() = runBlocking {
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        server.enqueue(MockResponse().setBody("""{"directory":"/home/dev"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"_tag":"FileNotFoundError"}"""))
        server.enqueue(MockResponse().setBody("""{"data":{"path":"/home/dev/OpenCode Projects/งาน 1/.opencode-mobile-project"}}"""))
        server.enqueue(MockResponse().setBody("""{"directory":"/home/dev/OpenCode Projects/งาน 1"}"""))
        assertEquals("/home/dev/OpenCode Projects/งาน 1", api.createProject("งาน 1"))
        assertEquals("/api/location", server.takeRequest().path)
        assertEquals("/home/dev/OpenCode Projects", server.takeRequest().requestUrl?.queryParameter("path"))
        val write = server.takeRequest()
        assertEquals("POST", write.method)
        assertEquals("application/octet-stream", write.getHeader("Content-Type"))
        assertEquals("/home/dev/OpenCode Projects/งาน 1/.opencode-mobile-project", write.requestUrl?.queryParameter("path"))
        assertEquals(Credentials.basic("opencode", "test-token"), write.getHeader("Authorization"))
        assertEquals("/home/dev/OpenCode Projects/งาน 1", server.takeRequest().requestUrl?.queryParameter("location[directory]"))
    }
    @Test fun projectCountsFollowEverySessionCursorAndDeduplicateRows() = runBlocking {
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"one"}],"cursor":{"next":"cursor+/?="}}"""))
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"one"},{"id":"two"}],"cursor":{}}"""))
        assertEquals(listOf("one", "two"), api.projectSessions().map { it.getString("id") })
        assertEquals("100", server.takeRequest().requestUrl?.queryParameter("limit"))
        assertEquals("cursor+/?=", server.takeRequest().requestUrl?.queryParameter("cursor"))
    }
    @Test fun projectCreationRejectsExistingWindowsFolderBeforeWriting() = runBlocking {
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        server.enqueue(MockResponse().setBody("""{"directory":"C:\\Users\\dev"}"""))
        server.enqueue(MockResponse().setBody("""{"data":[{"path":"OpenCode Projects\\SAMPLE\\","type":"directory"}]}"""))
        val failure = runCatching { api.createProject("Sample") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(2, server.requestCount)
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
    @Test fun searchKeepsUnicodeAndReservedCharactersAcrossPages() = runBlocking {
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        val query = "แชท + & / ? #"
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"found"}],"cursor":{"next":"next+/=&"}}"""))
        val first = api.get(conversationSearchPath("  $query  "))
        assertEquals("found", first.rows().single().getString("id"))
        val firstRequest = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals(query, firstRequest.requestUrl!!.queryParameter("search"))
        assertEquals("desc", firstRequest.requestUrl!!.queryParameter("order"))
        assertEquals("50", firstRequest.requestUrl!!.queryParameter("limit"))
        server.enqueue(MockResponse().setBody("""{"data":[],"cursor":{"next":null}}"""))
        api.get(conversationSearchPath(query, first.getJSONObject("cursor").getString("next")))
        val next = server.takeRequest(2, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals(query, next.queryParameter("search"))
        assertEquals("next+/=&", next.queryParameter("cursor"))
    }
    @Test fun modelListIdentifiesMissingProjectFromServerError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody(
            """{"_tag":"LocationNotFoundError","message":"private directory"}"""))
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        val error = runCatching { api.models("C:/missing") }.exceptionOrNull() as ApiFailure
        assertTrue(error.locationMissing)
        assertFalse(error.message.orEmpty().contains("private directory"))
        assertEquals(1, server.requestCount)
    }

    @Test fun checksLocationBeforeCreatingSoStaleFoldersDoNotCreateDeadChats() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"_tag":"LocationNotFoundError"}"""))
        server.enqueue(MockResponse().setBody("""{"data":{"id":"new"}}"""))
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        assertEquals("new", api.createSessionWithDefaultFallback("/missing").getString("id"))
        val check = server.takeRequest()
        assertEquals("GET", check.method)
        assertEquals("/missing", check.requestUrl!!.queryParameter("location[directory]"))
        val creation = server.takeRequest()
        assertEquals("POST", creation.method)
        assertEquals(0, JSONObject(creation.body.readUtf8()).length())
        assertEquals(2, server.requestCount)
    }

    @Test fun generic404DoesNotRetrySessionCreation() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        val error = runCatching { api.createSessionWithDefaultFallback("/missing") }.exceptionOrNull() as ApiFailure
        assertFalse(error.locationMissing)
        assertEquals(2, server.requestCount)
    }

    @Test fun moveUsesOpenCodeDirectoryPayload() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        api.moveSession("s1", "C:/Projects/new")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/session/s1/move", request.path)
        assertEquals("C:/Projects/new", JSONObject(request.body.readUtf8()).getString("directory"))
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
    @Test fun pairsOverLanHttpAndLoadsSessions() = runBlocking {
        val lanServer = MockWebServer()
        lanServer.start()
        try {
            lanServer.enqueue(MockResponse().setBody("""{"token":"lan-test-token"}"""))
            lanServer.enqueue(MockResponse().setBody("""{"version":"2.test"}"""))
            lanServer.enqueue(MockResponse().setBody("""{"data":[{"id":"s1","title":"LAN chat"}],"cursor":{"next":null}}"""))
            val link = PairingLink.forLan("http://localhost:${lanServer.port}/auth/connect/code", "192.168.1.20")
            // IP literals bypass OkHttp DNS; route only this test through the local HTTP proxy fixture.
            val lanTransport = transport.newBuilder().proxy(java.net.Proxy(java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress("127.0.0.1", lanServer.port))).build()
            val token = Api.redeem(link, lanTransport)
            val api = Api(Computer("lan", "Wi-Fi PC", link.origin, token), lanTransport)
            assertEquals("2.test", api.get("/api/info").getString("version"))
            assertEquals("s1", api.get("/api/session?order=desc&limit=50").rows().single().getString("id"))
            val redemption = lanServer.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("GET ${link.url} HTTP/1.1", redemption.requestLine)
            assertNull(redemption.getHeader("Authorization"))
            val info = lanServer.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals(Credentials.basic("opencode", "lan-test-token"), info.getHeader("Authorization"))
        } finally { lanServer.shutdown() }
    }
    @Test fun retriesInterruptedReadOnceOnFreshConnection() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        val result = Api(Computer("pc", "PC", origin, "test-token"), transport).get("/api/session")
        assertEquals(0, result.rows().size)
        assertEquals(2, server.requestCount)
    }
    @Test fun doesNotRetryInterruptedPost() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try {
            Api(Computer("pc", "PC", origin, "test-token"), transport).post("/api/session/s1/prompt", JSONObject().put("text", "test"))
            fail("Interrupted POST unexpectedly succeeded")
        } catch (_: java.io.IOException) { }
        assertEquals(1, server.requestCount)
    }
    @Test fun doesNotRetryAuthenticationFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            Api(Computer("pc", "PC", origin, "test-token"), transport).get("/api/info")
            fail("Authentication failure accepted")
        } catch (failure: ApiFailure) { assertEquals(401, failure.status) }
        assertEquals(1, server.requestCount)
    }
    @Test fun loadsEnabledModelsAndSwitchesSessionUsingV2Reference() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"model-ref","modelID":"upstream-id","providerID":"provider-a","name":"Example model","enabled":true,"variants":[{"id":"high"}]}]}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        val models = api.models("/workspace/my project")
        assertEquals("Example model", models.single().name)
        api.switchModel("s1", models.single(), "high")
        val listing = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/model", listing.requestUrl!!.encodedPath)
        assertEquals("/workspace/my project", listing.requestUrl!!.queryParameter("location[directory]"))
        assertNull(listing.requestUrl!!.queryParameter("location"))
        val switched = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", switched.method)
        assertEquals("/api/session/s1/model", switched.path)
        val body = JSONObject(switched.body.readUtf8()).getJSONObject("model")
        assertEquals("model-ref", body.getString("id"))
        assertEquals("high", body.getString("variant"))
        assertEquals("provider-a", body.getString("providerID"))
        assertEquals(Credentials.basic("opencode", "test-token"), switched.getHeader("Authorization"))
    }
    @Test fun createsDefaultChatAndDeletesOnlyItsSession() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"id":"new-chat"}}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        val created = api.createSession()
        assertEquals("new-chat", created.getString("id"))
        api.deleteSession(created.getString("id"))
        val create = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", create.method)
        assertEquals("/api/session", create.path)
        assertEquals(0, JSONObject(create.body.readUtf8()).length())
        val delete = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("DELETE", delete.method)
        assertEquals("/api/session/new-chat", delete.path)
        assertEquals(Credentials.basic("opencode", "test-token"), delete.getHeader("Authorization"))
    }
    @Test fun createsChatInSavedFolderWithoutRequiringUserInput() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"data":{"id":"new-chat"}}"""))
        Api(Computer("pc", "PC", origin, "test-token"), transport).createSession("/home/user/project")
        val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        assertEquals("/home/user/project", body.getJSONObject("location").getString("directory"))
    }
    @Test fun postsFilesAsDataUriInV2PromptBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val file = ChatAttachment.inline("note.txt", "text/plain", "hello".toByteArray())
        Api(Computer("pc", "PC", origin, "test-token"), transport).post("/api/session/s1/prompt", promptBody("Read this", listOf(file)))
        val sent = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body = JSONObject(sent.body.readUtf8())
        assertEquals("Read this", body.getString("text"))
        assertEquals("data:text/plain;base64,aGVsbG8=", body.getJSONArray("files").getJSONObject(0).getString("uri"))
        assertEquals("/api/session/s1/prompt", sent.path)
        assertEquals(1, server.requestCount)
    }
    @Test fun heartbeatAuthenticatesAndRejectsNonServerResponses() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"version":"2.test"}"""))
        server.enqueue(MockResponse().setBody("{}"))
        val api = Api(Computer("pc", "PC", origin, "test-token"), transport)
        api.probe()
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/info", request.path)
        assertEquals(Credentials.basic("opencode", "test-token"), request.getHeader("Authorization"))
        try { api.probe(); fail("Invalid heartbeat accepted") } catch (_: IllegalStateException) { }
    }
    @Test fun heartbeatHasBoundedDeadlineWhenServerStopsResponding() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val started = System.nanoTime()
        try {
            Api(Computer("pc", "PC", origin, "test-token"), transport).probe(200)
            fail("Unresponsive heartbeat accepted")
        } catch (_: java.io.IOException) { }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000)
    }
    @Test fun rendersV2AssistantContent() {
        val message = JSONObject("""{"id":"m1","type":"assistant","content":[{"type":"text","text":"hello"},{"type":"tool","tool":"shell"}]}""")
        assertTrue(message.displayText().contains("hello"))
        assertTrue(message.displayText().contains("shell"))
    }
}
