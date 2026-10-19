package dev.opencode.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
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

class ApiFailure(val status: Int) : IOException(when (status) {
    401, 403 -> "Access denied. Pair this computer again."
    404 -> "Item no longer exists or pairing code has expired."
    in 300..399 -> "Redirect refused. Use the final HTTPS pairing address."
    else -> "Server returned HTTP $status"
})

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
                            if (!it.isSuccessful) throw ApiFailure(it.code)
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
    suspend fun get(path: String): JSONObject = execute(request(path).build(), transport)
    suspend fun post(path: String, body: JSONObject = JSONObject()): JSONObject = execute(
        request(path).post(body.toString().toRequestBody("application/json".toMediaType())).build(), transport)
    fun events(): Flow<Unit> = callbackFlow {
        val source = EventSources.createFactory(transport).newEventSource(
            request("/api/event").header("Accept", "text/event-stream").build(), object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) { trySend(Unit) }
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) { trySend(Unit) }
                override fun onClosed(eventSource: EventSource) { close(IOException("Event stream closed")) }
                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(response?.let { ApiFailure(it.code) } ?: IOException("Connection lost"))
                }
            })
        awaitClose { source.cancel() }
    }
}

fun JSONObject.rows(): List<JSONObject> = optJSONArray("data")?.objects().orEmpty()
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.displayText(): String {
    if (has("text")) return optString("text")
    return optJSONArray("content")?.objects()?.joinToString("\n\n") { part ->
        if (part.has("text")) part.optString("text") else part.toString(2)
    } ?: toString(2)
}
