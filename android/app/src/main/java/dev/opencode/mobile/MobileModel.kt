package dev.opencode.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException

data class MobileState(
    val computers: List<Computer> = emptyList(), val computer: Computer? = null,
    val sessions: List<JSONObject> = emptyList(), val session: JSONObject? = null,
    val messages: List<JSONObject> = emptyList(), val permissions: List<JSONObject> = emptyList(),
    val diffs: List<JSONObject> = emptyList(), val status: String = "Disconnected",
    val error: String? = null, val busy: Boolean = false, val drafts: Map<String, String> = emptyMap(),
    val nextMessages: String? = null, val nextSessions: String? = null,
)

class MobileModel(app: Application) : AndroidViewModel(app) {
    private val vault = Vault(app)
    private val mutable = MutableStateFlow(MobileState())
    val state = mutable.asStateFlow()
    private var connection: Job? = null
    private var api: Api? = null
    private var epoch = 0
    private var foreground = false
    private var messageLimit = 100
    private var sessionLimit = 50
    private val refreshLock = kotlinx.coroutines.sync.Mutex()
    init {
        try { mutable.update { it.copy(computers = vault.load()) } }
        catch (_: Exception) { mutable.update { it.copy(error = "Cannot unlock saved computers. Existing credentials were preserved.") } }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun report(message: String) { mutable.update { it.copy(error = message) } }
    fun draft(value: String) { state.value.session?.optString("id")?.let { id -> mutable.update { it.copy(drafts = it.drafts + (id to value)) } } }
    fun pair(raw: String, name: String) = action {
        // Re-read before any write; never replace an unreadable vault with an empty list.
        val stored = vault.load()
        val link = PairingLink.parse(raw)
        val token = Api.redeem(link)
        val computer = vault.add(name, link.origin, token)
        // Check authentication before persisting; an invalid token must not replace a working entry.
        Api(computer).get("/api/info")
        val computers = stored.filterNot { it.origin == computer.origin } + computer
        vault.save(computers)
        mutable.update { it.copy(computers = computers) }
        connect(computer)
    }
    fun forget(computer: Computer) = action {
        val computers = vault.load().filterNot { it.id == computer.id }
        vault.save(computers)
        if (state.value.computer?.id == computer.id) {
            connection?.cancel(); epoch++; api = null
            mutable.value = MobileState(computers = computers, busy = state.value.busy)
        } else mutable.update { it.copy(computers = computers) }
    }
    fun connect(computer: Computer) {
        connection?.cancel(); epoch++
        api = Api(computer)
        messageLimit = 100; sessionLimit = 50
        mutable.update { MobileState(computers = it.computers, computer = computer, status = "Connecting…", busy = it.busy) }
        startEvents()
    }
    fun active(value: Boolean) {
        foreground = value
        if (value) startEvents() else { connection?.cancel(); connection = null; mutable.update { it.copy(status = "Paused") } }
    }
    private fun startEvents() {
        if (!foreground || api == null || connection?.isActive == true) return
        val client = api!!; val generation = epoch
        connection = viewModelScope.launch {
            var attempt = 0
            while (isActive && generation == epoch) {
                try {
                    client.events().conflate().collect {
                        mutable.update { it.copy(status = "Connected") }
                        attempt = 0
                        refreshNow(client, generation)
                        delay(400) // Coalesce token bursts without building an unbounded event queue.
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (generation != epoch) return@launch
                    mutable.update { it.copy(status = if (e is ApiFailure && e.status in listOf(401, 403)) "Pair again" else "Reconnecting…") }
                    if (e is ApiFailure && e.status in listOf(401, 403)) { report(e.message.orEmpty()); return@launch }
                    delay((1000L shl attempt.coerceAtMost(5)) + kotlin.random.Random.nextLong(250))
                    attempt++
                }
            }
        }
    }
    fun refresh() = action { api?.let { refreshNow(it, epoch) } }
    private suspend fun refreshNow(client: Api, generation: Int) {
        refreshLock.lock()
        try {
            if (generation != epoch || client !== api) return
            val id = state.value.session?.optString("id")
            val sessions = client.get("/api/session?order=desc&limit=$sessionLimit")
            val messages = id?.let { client.get("/api/session/$it/message?order=desc&limit=$messageLimit") }
            val permissions = id?.let { client.get("/api/session/$it/permission").rows() }.orEmpty()
            if (generation == epoch && id == state.value.session?.optString("id")) mutable.update {
                it.copy(sessions = sessions.rows(), nextSessions = sessions.nextCursor(),
                    messages = messages?.rows()?.reversed().orEmpty(), nextMessages = messages?.nextCursor(), permissions = permissions)
            }
        } finally { refreshLock.unlock() }
    }
    fun select(session: JSONObject?) {
        messageLimit = 100
        mutable.update { it.copy(session = session, messages = emptyList(), permissions = emptyList(), diffs = emptyList(), nextMessages = null) }
        val client = api ?: return
        val generation = epoch
        // Reads must not be dropped merely because a write is still in flight.
        viewModelScope.launch {
            try { refreshNow(client, generation) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == epoch) report("Could not synchronize this session. Try Refresh.") }
        }
    }
    fun create(directory: String) = action {
        require(directory.startsWith("/") || Regex("^[A-Za-z]:[\\\\/].*").matches(directory)) { "Enter an absolute directory on the PC" }
        val client = api ?: return@action; val generation = epoch
        val session = client.post("/api/session", JSONObject().put("location", JSONObject().put("directory", directory))).getJSONObject("data")
        if (generation == epoch) select(session)
    }
    fun send() = action {
        val id = state.value.session?.getString("id") ?: return@action
        val text = state.value.drafts[id].orEmpty()
        if (text.isBlank()) return@action
        val generation = epoch; val client = api ?: return@action
        try { client.post("/api/session/$id/prompt", JSONObject().put("text", text)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error("Delivery not confirmed. Check the conversation before sending again; your draft was kept.") }
        if (generation == epoch) mutable.update {
            it.copy(drafts = if (it.drafts[id] == text) it.drafts - id else it.drafts)
        }
        refreshNow(client, generation)
    }
    fun interrupt() = sessionAction { client, id -> client.post("/api/session/$id/interrupt") }
    fun reply(requestID: String, decision: String) = sessionAction { client, id ->
        client.post("/api/session/$id/permission/$requestID/reply", JSONObject().put("decision", decision))
    }
    fun loadDiff() = sessionAction { client, id ->
        val result = client.get("/api/session/$id/diff").rows()
        if (state.value.session?.optString("id") == id && client === api) mutable.update { it.copy(diffs = result) }
    }
    fun moreMessages() = sessionAction { client, id ->
        refreshLock.withLock {
            if (client !== api || state.value.session?.optString("id") != id) return@withLock
            val cursor = state.value.nextMessages ?: return@withLock
            val page = client.get("/api/session/$id/message?cursor=${java.net.URLEncoder.encode(cursor, "UTF-8")}&limit=100")
            if (client === api && state.value.session?.optString("id") == id) mutable.update {
                val combined = (page.rows().reversed() + it.messages).distinctBy { row -> row.optString("id") }
                messageLimit = maxOf(messageLimit, combined.size)
                it.copy(messages = combined, nextMessages = page.nextCursor())
            }
        }
    }
    fun moreSessions() = action {
        val client = api ?: return@action
        refreshLock.withLock {
            if (client !== api) return@withLock
            val cursor = state.value.nextSessions ?: return@withLock
            val page = client.get("/api/session?cursor=${java.net.URLEncoder.encode(cursor, "UTF-8")}&limit=50")
            if (client === api) mutable.update {
                val combined = (it.sessions + page.rows()).distinctBy { row -> row.optString("id") }
                sessionLimit = maxOf(sessionLimit, combined.size)
                it.copy(sessions = combined, nextSessions = page.nextCursor())
            }
        }
    }
    private fun sessionAction(block: suspend (Api, String) -> Unit) = action {
        val client = api ?: return@action; val generation = epoch
        val id = state.value.session?.optString("id") ?: return@action
        block(client, id)
        if (client === api) refreshNow(client, generation)
    }
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        val generation = epoch
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == epoch) report(if (e is IOException) e.message ?: "Network unavailable" else e.message ?: "Operation failed") }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}

private fun JSONObject.nextCursor(): String? = optJSONObject("cursor")?.let { if (it.isNull("next")) null else it.optString("next").ifBlank { null } }
