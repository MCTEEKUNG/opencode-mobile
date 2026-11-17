package dev.opencode.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiFailure(val status: Int, private val tag: String = "") : IOException(when {
    status == 404 && tag == "LocationNotFoundError" -> "The project folder no longer exists on the PC."
    else -> when (status) {
    401, 403 -> "Access denied. Pair this computer again."
    404 -> "Item no longer exists or pairing code has expired."
    in 300..399 -> "Redirect refused. Use the final HTTPS pairing address."
    else -> "Server returned HTTP $status"
    }
}) {
    val locationMissing get() = status == 404 && tag == "LocationNotFoundError"
    val fileMissing get() = status == 404 && tag == "FileNotFoundError"
}

class Api(private val computer: Computer, private val transport: OkHttpClient = http) {
    companion object {
        // No transparent retries: a failed POST may already have reached the PC.
        val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).build()
        suspend fun redeem(link: PairingLink, transport: OkHttpClient = http): String {
            val request = Request.Builder().url(link.url).header("Accept", "application/json").build()
            return execute(request, transport).getString("token").also { require(it.isNotBlank()) }
        }
        private suspend fun execute(request: Request, transport: OkHttpClient): JSONObject = suspendCancellableCoroutine { continuation ->
            val call = transport.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (!it.isSuccessful) {
                                val tag = if (it.code == 404) runCatching {
                                    JSONObject(it.peekBody(8192).string()).optString("_tag")
                                }.getOrDefault("") else ""
                                throw ApiFailure(it.code, tag)
                            }
                            val body = it.body?.string().orEmpty()
                            if (body.isBlank()) JSONObject() else JSONObject(body)
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
    private fun request(path: String): Request.Builder = Request.Builder().url(computer.origin + path)
        .header("Accept", "application/json").header("Authorization", Credentials.basic("opencode", computer.token))
    suspend fun get(path: String): JSONObject {
        val read = request(path).build()
        try { return execute(read, transport) }
        catch (failure: IOException) {
            if (failure is ApiFailure) throw failure
            // A LAN server may close an idle pooled socket without notifying the client.
            // Retry only ordinary reads, once, on a fresh connection. Redemption and POSTs
            // remain single-attempt because their effects may already have happened.
            val pool = ConnectionPool()
            try { return execute(read, transport.newBuilder().connectionPool(pool).build()) }
            finally { pool.evictAll() }
        }
    }
    suspend fun post(path: String, body: JSONObject = JSONObject()): JSONObject = execute(
        request(path).post(body.toString().toRequestBody("application/json".toMediaType())).build(), transport)
    suspend fun createSession(directory: String? = null): JSONObject = post("/api/session", newSessionBody(directory)).getJSONObject("data")
    internal suspend fun projectSessions(): List<JSONObject> {
        val sessions = linkedMapOf<String, JSONObject>()
        val cursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
            val page = get("/api/session?order=desc&limit=100" + (cursor?.let { "&cursor=${queryValue(it)}" } ?: ""))
            page.rows().forEach { sessions.putIfAbsent(it.getString("id"), it) }
            cursor = page.nextCursor()
            check(cursor == null || cursors.add(cursor)) { "The PC returned a repeated session page." }
        } while (cursor != null)
        return sessions.values.toList()
    }
    internal suspend fun location(directory: String? = null): String {
        val query = directory?.let { "?location%5Bdirectory%5D=${queryValue(it)}" }.orEmpty()
        return get("/api/location$query").getString("directory")
    }
    internal suspend fun projectDirectories(root: String): List<String> {
        val page = try { get("/api/fs/list?path=${queryValue(root)}") }
        catch (failure: ApiFailure) {
            if (failure.fileMissing) return emptyList()
            throw failure
        }
        return page.rows().filter { it.optString("type") == "directory" }.mapNotNull { entry ->
            val name = directoryName(entry.getString("path"), windows = isWindowsPath(root))
            name.takeIf { it.isNotBlank() && it != "." && it != ".." }?.let { childDirectory(root, it) }
        }
    }
    internal suspend fun createProject(name: String): String {
        val folderName = checkedProjectName(name)
        val root = childDirectory(location(), PROJECTS_FOLDER)
        val directory = childDirectory(root, folderName)
        require(projectDirectories(root).none { directoryKey(it) == directoryKey(directory) }) {
            "A project folder with that name already exists on the PC."
        }
        // The filesystem API creates missing parent directories when writing a file.
        val marker = childDirectory(directory, PROJECT_MARKER)
        val write = request("/api/experimental/fs/write?path=${queryValue(marker)}")
            .post("OpenCode Mobile project\n".toByteArray(Charsets.UTF_8).toRequestBody("application/octet-stream".toMediaType())).build()
        try { execute(write, transport) }
        catch (failure: ApiFailure) {
            if (failure.status == 404) error("This OpenCode server does not support creating project folders. Update OpenCode on the PC.")
            throw failure
        }
        catch (_: IOException) {
            if (projectDirectories(root).none { directoryKey(it) == directoryKey(directory) }) {
                error("Could not confirm folder creation. Refresh Projects and try again.")
            }
        }
        return location(directory)
    }
    suspend fun createSessionWithDefaultFallback(directory: String?): JSONObject {
        val verified = directory?.takeIf { it.isNotBlank() }?.let {
            try { models(it); it }
            catch (error: ApiFailure) {
                if (!error.locationMissing) throw error
                null
            }
        }
        return try { createSession(verified) }
        catch (error: ApiFailure) {
            if (verified == null || !error.locationMissing) throw error
            // A folder can disappear between validation and creation.
            createSession()
        }
    }
    suspend fun moveSession(id: String, directory: String) {
        val target = newSessionBody(directory).getJSONObject("location")
        post("/api/session/$id/move", target)
    }
    suspend fun deleteSession(id: String) { execute(request("/api/session/$id").delete().build(), transport) }
    suspend fun models(directory: String = ""): List<ChatModel> {
        // OpenAPI declares a deepObject query, not a JSON-encoded location string.
        val query = if (directory.isBlank()) "" else "?location%5Bdirectory%5D=" + java.net.URLEncoder.encode(directory, "UTF-8")
        return get("/api/model$query").availableChatModels()
    }
    internal suspend fun composerCatalog(kind: ComposerCatalogKind, directory: String): List<ComposerEntry> {
        val query = if (directory.isBlank()) "" else "?location%5Bdirectory%5D=" + java.net.URLEncoder.encode(directory, "UTF-8")
        return get("/api/${kind.endpoint}$query").composerEntries(kind)
    }
    suspend fun switchModel(sessionId: String, model: ChatModel, variant: String? = null) {
        post("/api/session/$sessionId/model", JSONObject().put("model", model.reference(variant)))
    }
    suspend fun probe(timeoutMillis: Long = 3000) {
        val client = transport.newBuilder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS).readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true).build()
        val info = execute(request("/api/info").build(), client)
        check(info.optString("version").isNotBlank()) { "Invalid server heartbeat response" }
    }
    fun events(): Flow<ServerSignal> = callbackFlow {
        val source = EventSources.createFactory(transport.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()).newEventSource(
            request("/api/event").header("Accept", "text/event-stream").build(), object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) { trySendBlocking(ServerSignal.Open) }
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val eventType = runCatching { JSONObject(data).optString("type") }.getOrDefault("")
                    if (projectInventoryChanged(eventType.ifEmpty { type.orEmpty() })) {
                        trySendBlocking(ServerSignal.ProjectInventory)
                        return
                    }
                    val activity = sessionActivity(data)
                    val terminal = terminalEvent(data)
                    when {
                        terminal != null -> trySendBlocking(ServerSignal.Terminal(terminal, activity))
                        activity != null -> trySendBlocking(ServerSignal.Activity(activity))
                        else -> trySend(ServerSignal.Refresh)
                    }
                }
                override fun onClosed(eventSource: EventSource) { close(IOException("Event stream closed")) }
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(response?.let { ApiFailure(it.code) } ?: IOException("Connection lost"))
                }
            })
        awaitClose { source.cancel() }
    }
}

private fun queryValue(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

fun JSONObject.rows(): List<JSONObject> = optJSONArray("data")?.objects().orEmpty()
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.displayText(): String {
    if (has("text")) return optString("text")
    return optJSONArray("content")?.objects()?.joinToString("\n\n") { part ->
        if (part.has("text")) part.optString("text") else part.toString(2)
    } ?: toString(2)
}
