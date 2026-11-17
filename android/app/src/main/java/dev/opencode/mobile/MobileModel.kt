package dev.opencode.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException

data class MobileState(
    val computers: List<Computer> = emptyList(), val computer: Computer? = null,
    val sessions: List<JSONObject> = emptyList(), val session: JSONObject? = null,
    val messages: List<JSONObject> = emptyList(), val permissions: List<JSONObject> = emptyList(),
    val diffs: List<JSONObject> = emptyList(), val status: String = "Disconnected",
    val error: String? = null, val busy: Boolean = false, val drafts: Map<String, String> = emptyMap(),
    val nextMessages: String? = null, val nextSessions: String? = null,
    val attachments: Map<String, List<ChatAttachment>> = emptyMap(), val importingFiles: Boolean = false,
    val history: HistoryPresentation = HistoryPresentation(),
    val terminalEvents: List<TerminalEvent> = emptyList(),
    val workingSessions: Map<String, Set<WorkKind>> = emptyMap(),
    val search: ConversationSearch = ConversationSearch(),
    val projects: Projects = Projects(),
    val commands: List<ComposerEntry> = emptyList(), val skills: List<ComposerEntry> = emptyList(),
    val composerLoading: Boolean = false, val composerError: String? = null, val composerCatalogLoaded: Boolean = false,
    val interrupting: Boolean = false,
    val missingProject: Boolean = false,
    val models: List<ChatModel> = emptyList(), val modelsLoading: Boolean = false, val modelsError: String? = null,
)

class MobileModel(app: Application) : AndroidViewModel(app) {
    private val vault = Vault(app)
    private val workspaces = ComputerWorkspaces()
    private val mutable = MutableStateFlow(MobileState())
    val state = mutable.asStateFlow()
    private var connection: Job? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val connectivity get() = getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
    private var api: Api? = null
    private var catalogJob: Job? = null
    private var modelsJob: Job? = null
    private var searchJob: Job? = null
    private var epoch = 0
    private var foreground = false
    private var messageLimit = 100
    private var sessionLimit = 50
    private val refreshLock = kotlinx.coroutines.sync.Mutex()
    private val projectsLock = kotlinx.coroutines.sync.Mutex()
    init {
        try {
            val computers = vault.load()
            mutable.update { it.copy(computers = computers) }
            (computers.find { it.id == vault.lastComputerId() } ?: computers.firstOrNull())?.let(::connect)
        }
        catch (_: Exception) { mutable.update { it.copy(error = "Cannot unlock saved computers. Existing credentials were preserved.") } }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun report(message: String) { mutable.update { it.copy(error = message) } }
    fun draft(value: String) { state.value.session?.optString("id")?.let { id -> mutable.update { it.copy(drafts = it.drafts + (id to value)) } } }
    fun attach(uris: List<android.net.Uri>) {
        val sessionId = state.value.session?.optString("id") ?: return
        val generation = epoch
        if (state.value.importingFiles) return
        if (uris.size > 6) { report("Attach up to 6 files per message."); return }
        mutable.update { it.copy(importingFiles = true) }
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    uris.take(6).map { uri ->
                        var name = "Attachment"
                        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                        }
                        val mime = resolver.getType(uri) ?: "application/octet-stream"
                        val bytes = resolver.openInputStream(uri)?.use { readAttachmentBytes(it) } ?: error("Cannot open the selected file.")
                        ChatAttachment.inline(name, mime, bytes)
                    }
                }
                if (generation == epoch) mutable.update {
                    val combined = it.attachments[sessionId].orEmpty() + files
                    require(combined.size <= 6) { "Attach up to 6 files per message." }
                    require(combined.sumOf { file -> file.uri.length.toLong() } <= 35L * 1024 * 1024) { "Attachments together must be about 25 MB or smaller." }
                    it.copy(attachments = it.attachments + (sessionId to combined))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == epoch) report(e.message ?: "Could not read the selected files.") }
            finally { if (generation == epoch) mutable.update { it.copy(importingFiles = false) } }
        }
    }
    fun removeAttachment(index: Int) {
        val id = state.value.session?.optString("id") ?: return
        mutable.update { it.copy(attachments = it.attachments + (id to it.attachments[id].orEmpty().filterIndexed { item, _ -> item != index })) }
    }
    private fun computerApi(computer: Computer) = Api(computer, connectionTransport(
        getApplication<Application>(), computer.origin.toHttpUrl()))
    fun pair(raw: String, name: String) = action {
        // Re-read before any write; never replace an unreadable vault with an empty list.
        val stored = vault.load()
        val link = PairingLink.parse(raw)
        android.util.Log.i("PairingFlow", "redeem_start")
        val token = try { Api.redeem(link, connectionTransport(getApplication<Application>(), link.url)) } catch (e: Exception) {
            android.util.Log.w("PairingFlow", "redeem_failed: ${e.javaClass.simpleName}${if (e is ApiFailure) " HTTP ${e.status}" else ""}")
            throw e
        }
        android.util.Log.i("PairingFlow", "redeem_ok")
        val computer = vault.add(name, link.origin, token)
        // Check authentication before persisting; an invalid token must not replace a working entry.
        try { computerApi(computer).get("/api/info") } catch (e: Exception) {
            android.util.Log.w("PairingFlow", "verify_failed: ${e.javaClass.simpleName}${if (e is ApiFailure) " HTTP ${e.status}" else ""}")
            throw e
        }
        android.util.Log.i("PairingFlow", "verify_ok")
        val computers = stored.filterNot { it.origin == computer.origin } + computer
        vault.save(computers)
        mutable.update { it.copy(computers = computers) }
        connect(computer)
    }
    fun rename(computer: Computer, name: String) = action {
        val updatedName = name.trim()
        require(updatedName.isNotEmpty()) { "Enter a computer name" }
        val stored = vault.load()
        require(stored.any { it.id == computer.id }) { "Computer no longer saved" }
        val computers = stored.map { if (it.id == computer.id) it.copy(name = updatedName) else it }
        vault.save(computers)
        mutable.update { current ->
            current.copy(computers = computers, computer = current.computer?.let {
                if (it.id == computer.id) it.copy(name = updatedName) else it
            })
        }
    }
    fun forget(computer: Computer) = action {
        val computers = vault.load().filterNot { it.id == computer.id }
        vault.save(computers)
        workspaces.remove(computer.id)
        if (state.value.computer?.id == computer.id) {
            connection?.cancel(); modelsJob?.cancel(); searchJob?.cancel(); catalogJob?.cancel(); epoch++; api = null
            mutable.value = MobileState(computers = computers, busy = state.value.busy)
        } else mutable.update { it.copy(computers = computers) }
    }
    fun connect(computer: Computer) {
        if (state.value.computer?.id == computer.id && api != null) { reconnect(); return }
        workspaces.remember(state.value)
        vault.rememberComputer(computer.id)
        connection?.cancel(); modelsJob?.cancel(); searchJob?.cancel(); catalogJob?.cancel(); epoch++
        api = computerApi(computer)
        messageLimit = 100; sessionLimit = 50
        mutable.update {
            val restored = workspaces.restore(computer, it.computers)
            restored.copy(history = vault.history(computer.id), workingSessions = emptyMap(),
                projects = restored.projects.copy(selected = vault.selectedProject(computer.id), loading = false))
        }
        messageLimit = maxOf(100, state.value.messages.size)
        sessionLimit = maxOf(50, state.value.sessions.size)
        startEvents()
        if (state.value.session != null) loadModels()
    }
    fun active(value: Boolean) {
        foreground = value
        if (value) {
            if (networkCallback == null) {
                val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onLost(network: android.net.Network) {
                        viewModelScope.launch {
                            if (foreground && api != null) restartConnection(lost = true)
                        }
                    }
                    override fun onAvailable(network: android.net.Network) {
                        viewModelScope.launch {
                            if (foreground && api != null && state.value.status == PeerStatus.LOST.label) restartConnection(lost = true)
                        }
                    }
                }
                networkCallback = callback
                connectivity.registerDefaultNetworkCallback(callback)
            }
            if (api != null) mutable.update { it.copy(status = PeerStatus.CONNECTING.label) }
            startEvents()
        } else {
            connection?.cancel(); connection = null
            networkCallback?.let { connectivity.unregisterNetworkCallback(it) }; networkCallback = null
            mutable.update { it.copy(status = "Paused", workingSessions = emptyMap()) }
        }
    }
    fun reconnect() { if (foreground && api != null) restartConnection(lost = state.value.status == PeerStatus.LOST.label) }
    private fun restartConnection(lost: Boolean) {
        connection?.cancel(); connection = null
        mutable.update { it.copy(status = if (lost) PeerStatus.LOST.label else PeerStatus.CONNECTING.label, workingSessions = emptyMap()) }
        startEvents(keepLost = lost)
    }
    private fun startEvents(keepLost: Boolean = false) {
        if (!foreground || api == null || connection?.isActive == true) return
        val client = api!!; val generation = epoch
        connection = viewModelScope.launch {
            try {
                coroutineScope {
                    val refreshes = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
                    val projectRefreshes = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
                    launch {
                        for (ignored in projectRefreshes) refreshProjects(client)
                    }
                    launch {
                        for (ignored in refreshes) {
                            try { refreshNow(client, generation) }
                            catch (e: CancellationException) { throw e }
                            catch (e: ApiFailure) { if (e.status in listOf(401, 403)) throw e }
                            catch (_: Exception) { /* The independent heartbeat owns connectivity status. */ }
                            delay(400)
                        }
                    }
                    connectionUpdates({ client.probe() }, {
                        client.events().map { signal ->
                            if (generation == epoch && client === api) when (signal) {
                                ServerSignal.Open -> {
                                    projectRefreshes.trySend(Unit)
                                    val running = try {
                                        client.get("/api/session/active").optJSONObject("data")?.keys()?.asSequence()?.toSet().orEmpty()
                                    } catch (failure: CancellationException) { throw failure }
                                    catch (_: Exception) { emptySet() }
                                    if (generation == epoch && client === api) mutable.update {
                                        it.copy(workingSessions = running.associateWith { setOf(WorkKind.RESPONSE) })
                                    }
                                }
                                ServerSignal.Refresh -> Unit
                                ServerSignal.ProjectInventory -> projectRefreshes.trySend(Unit)
                                is ServerSignal.Terminal -> mutable.update {
                                    val next = signal.activity?.reduce(it.workingSessions) ?: it.workingSessions
                                    val currentId = it.session?.optString("id")
                                    if (signal.event.sessionId == currentId) it.copy(workingSessions = next,
                                        terminalEvents = (it.terminalEvents.takeLast(149) + signal.event))
                                    else if (next != it.workingSessions) it.copy(workingSessions = next) else it
                                }
                                is ServerSignal.Activity -> mutable.update {
                                    val next = signal.update.reduce(it.workingSessions)
                                    if (next == it.workingSessions) it else it.copy(workingSessions = next)
                                }
                            }
                        }
                    }).collect { update ->
                        if (generation != epoch || client !== api) return@collect
                        update.status?.let { status ->
                            if (!(keepLost && status == PeerStatus.CONNECTING)) {
                                android.util.Log.i("ConnectionStatus", status.name)
                                mutable.update { it.copy(status = status.label, workingSessions = if (status == PeerStatus.LOST) emptyMap() else it.workingSessions) }
                            }
                        }
                        if (update.refresh) refreshes.trySend(Unit)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == epoch && client === api) {
                    mutable.update { it.copy(status = if (e is ApiFailure && e.status in listOf(401, 403)) PeerStatus.AUTH_REQUIRED.label else PeerStatus.LOST.label) }
                }
            }
        }
    }
    override fun onCleared() {
        networkCallback?.let { connectivity.unregisterNetworkCallback(it) }
        networkCallback = null
        super.onCleared()
    }
    private fun updateHistory(change: (MobileState) -> HistoryPresentation) {
        val current = state.value
        val computer = current.computer ?: return
        val history = change(current)
        vault.saveHistory(computer.id, history)
        mutable.update { it.copy(history = history) }
    }
    fun toggleHistory() = updateHistory { it.history.copy(hidden = !it.history.hidden) }
    fun clearHomeHistory() {
        searchConversations("")
        updateHistory { current ->
            val latest = current.sessions.maxOfOrNull { it.sessionTime("updated") } ?: 0L
            current.history.copy(hidden = false, clearedThrough = maxOf(latest, current.history.clearedThrough)
                .takeIf { it > 0L } ?: System.currentTimeMillis())
        }
    }
    fun restoreHomeHistory() = updateHistory { it.history.copy(hidden = false, clearedThrough = 0L) }

    fun searchConversations(value: String) {
        if (value == state.value.search.query) return
        searchJob?.cancel()
        mutable.update { it.copy(search = ConversationSearch(query = value, loading = value.isNotBlank())) }
        if (value.isNotBlank()) loadSearch(debounce = true)
    }
    fun retrySearch() { loadSearch() }
    fun moreSearchResults() { loadSearch(more = true) }
    private fun loadSearch(more: Boolean = false, debounce: Boolean = false) {
        val client = api ?: return
        val snapshot = state.value.search
        val directory = state.value.projects.selected
        if (snapshot.query.isBlank() || (more && (snapshot.loading || snapshot.nextCursor == null))) return
        searchJob?.cancel()
        val generation = epoch
        mutable.update { it.copy(search = it.search.copy(loading = true, error = null)) }
        searchJob = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                val page = client.get(conversationSearchPath(snapshot.query,
                    cursor = if (more) snapshot.nextCursor else null,
                    limit = if (more) 50 else maxOf(50, snapshot.results.size), directory = directory))
                if (client === api && generation == epoch && state.value.search.query == snapshot.query && state.value.projects.selected == directory) mutable.update {
                    it.copy(search = it.search.copy(
                        results = ((if (more) it.search.results else emptyList()) + page.rows()).distinctBy { row -> row.optString("id") },
                        nextCursor = page.nextCursor(), loading = false, error = null))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (client === api && generation == epoch && state.value.search.query == snapshot.query && state.value.projects.selected == directory) mutable.update {
                    it.copy(search = it.search.copy(loading = false, error = "Could not search this computer. Try again."))
                }
            }
        }
    }
    fun refresh() = action {
        val client = api ?: return@action
        val generation = epoch
        coroutineScope {
            launch { refreshProjects(client) }
            refreshNow(client, generation)
        }
    }
    fun selectProject(directory: String?) {
        val current = state.value
        val computer = current.computer ?: return
        if (current.projects.selected == directory) return
        vault.selectProject(computer.id, directory)
        searchJob?.cancel()
        mutable.update { it.copy(projects = it.projects.copy(selected = directory),
            search = ConversationSearch(query = it.search.query, loading = it.search.query.isNotBlank())) }
        if (current.search.query.isNotBlank()) loadSearch()
    }
    private suspend fun refreshProjects(client: Api) = projectsLock.withLock {
        if (client !== api) return@withLock
        mutable.update { it.copy(projects = it.projects.copy(loading = true, failed = false)) }
        try {
            val (sessions, root, directories) = coroutineScope {
                val sessions = async { client.projectSessions() }
                val root = childDirectory(client.location(), PROJECTS_FOLDER)
                Triple(sessions.await(), root, client.projectDirectories(root))
            }
            if (client === api) mutable.update {
                it.copy(projects = it.projects.copy(sessions = sessions, root = root, directories = directories, loaded = true))
            }
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) {
            if (client === api) mutable.update { it.copy(projects = it.projects.copy(failed = true)) }
        } finally {
            if (client === api) mutable.update { it.copy(projects = it.projects.copy(loading = false)) }
        }
    }
    fun createProject(name: String) = action {
        val client = api ?: return@action
        val directory = projectsLock.withLock {
            if (client !== api) return@action
            client.createProject(name).also { created ->
                if (client === api) mutable.update { it.copy(projects = it.projects.copy(
                    directories = (it.projects.directories + created).distinctBy(::directoryKey))) }
            }
        }
        if (client === api) selectProject(directory)
    }
    private suspend fun refreshNow(client: Api, generation: Int) {
        refreshLock.lock()
        try {
            if (generation != epoch || client !== api) return
            val id = state.value.session?.optString("id")
            val sessions = client.get("/api/session?order=desc&limit=$sessionLimit")
            val recent = sessions.rows()
            val messages = id?.let { client.get("/api/session/$it/message?order=desc&limit=$messageLimit") }
            var locationMissing = false
            val permissions = id?.let {
                try { client.get("/api/session/$it/permission").rows() }
                catch (failure: ApiFailure) {
                    if (!failure.locationMissing) throw failure
                    // History remains readable even though project-scoped approvals cannot load.
                    locationMissing = true
                    emptyList()
                }
            }.orEmpty()
            if (generation == epoch && id == state.value.session?.optString("id")) mutable.update {
                val projectRows = it.projects.sessions
                val refreshedProjects = if (projectRows.isEmpty() || recent.isEmpty()) it.projects else {
                    val byId = recent.associateBy { row -> row.getString("id") }
                    it.projects.copy(sessions = projectRows.map { row -> byId[row.getString("id")] ?: row })
                }
                it.copy(sessions = recent, session = recent.find { row -> row.optString("id") == id } ?: it.session,
                    projects = refreshedProjects, nextSessions = sessions.nextCursor(),
                    messages = messages?.rows()?.reversed().orEmpty(), nextMessages = messages?.nextCursor(), permissions = permissions,
                    missingProject = it.missingProject || locationMissing)
            }
        } finally { refreshLock.unlock() }
        if (generation == epoch && client === api && searchJob?.isActive != true && state.value.search.query.isNotBlank()) loadSearch()
    }
    fun select(session: JSONObject?) {
        catalogJob?.cancel(); modelsJob?.cancel()
        val directory = session?.optJSONObject("location")?.optString("directory").orEmpty()
        state.value.computer?.let { vault.rememberDirectory(it.id, directory) }
        messageLimit = 100
        mutable.update { it.copy(session = session, terminalEvents = emptyList(), commands = emptyList(), skills = emptyList(), composerLoading = false,
            composerError = null, composerCatalogLoaded = false, missingProject = false, models = emptyList(), modelsLoading = false, modelsError = null, messages = emptyList(), permissions = emptyList(), diffs = emptyList(), nextMessages = null) }
        if (session != null) loadModels()
        val client = api ?: return
        val generation = epoch
        // Reads must not be dropped merely because a write is still in flight.
        viewModelScope.launch {
            try { refreshNow(client, generation) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == epoch) report("Could not synchronize this session. Try Refresh.") }
        }
    }
    fun loadModels() {
        modelsJob?.cancel()
        val client = api ?: return
        val id = state.value.session?.optString("id") ?: return
        val directory = state.value.session?.optJSONObject("location")?.optString("directory").orEmpty()
        mutable.update { it.copy(modelsLoading = true, modelsError = null, models = emptyList()) }
        modelsJob = viewModelScope.launch {
            try {
                val models = client.models(directory)
                if (client === api && state.value.session?.optString("id") == id) mutable.update { it.copy(models = models, missingProject = false) }
            } catch (e: CancellationException) { throw e }
            catch (failure: Exception) {
                val reason = if (failure is ApiFailure) "HTTP ${failure.status}" else failure.javaClass.simpleName
                android.util.Log.w("ModelPicker", "load_failed: $reason")
                if (client === api && state.value.session?.optString("id") == id) mutable.update {
                    val missing = (failure as? ApiFailure)?.locationMissing == true
                    it.copy(missingProject = missing || it.missingProject, modelsError = when {
                        missing -> "This chat's project folder is missing on the PC. Choose an existing folder to continue."
                        failure is ApiFailure && failure.status in setOf(401, 403) -> failure.message
                        else -> "Could not load models ($reason). Try again."
                    })
                }
            } finally {
                if (client === api && state.value.session?.optString("id") == id) mutable.update { it.copy(modelsLoading = false) }
            }
        }
    }
    fun moveProject(directory: String) = action {
        val client = api ?: return@action
        val id = state.value.session?.optString("id") ?: return@action
        require(directory.isNotBlank()) { "Enter an existing folder on the PC." }
        newSessionBody(directory)
        client.models(directory.trim()) // Verify the destination before changing the session.
        if (client !== api || state.value.session?.optString("id") != id) return@action
        client.moveSession(id, directory.trim())
        val updated = client.get("/api/session/$id").getJSONObject("data")
        if (client !== api || state.value.session?.optString("id") != id) return@action
        if (updated.optJSONObject("location")?.optString("directory") == state.value.session?.optJSONObject("location")?.optString("directory")) {
            report("OpenCode accepted the folder change. Wait for it to finish, then reopen the model picker.")
        } else select(updated)
    }
    fun chooseModel(model: ChatModel, variant: String? = null) = action {
        val client = api ?: return@action
        val session = state.value.session ?: return@action
        val id = session.getString("id")
        require(state.value.models.any { it.key == model.key }) { "This model is no longer available. Refresh the model list." }
        require(variant == null || state.value.models.first { it.key == model.key }.variants.contains(variant)) { "This effort is no longer available. Refresh models." }
        client.switchModel(id, model, variant)
        if (client === api && state.value.session?.optString("id") == id) mutable.update {
            it.copy(session = JSONObject(it.session.toString()).put("model", model.reference(variant)))
        }
    }
    fun create() = action {
        val client = api ?: return@action
        val generation = epoch
        val computer = state.value.computer ?: return@action
        val project = state.value.projects.selected
        val directory = vault.directory(computer.id)
            ?: state.value.sessions.firstNotNullOfOrNull { it.optJSONObject("location")?.optString("directory")?.takeIf(String::isNotBlank) }
        val session = if (project == null) client.createSessionWithDefaultFallback(directory)
            else client.createSession(client.location(project))
        if (generation == epoch) {
            mutable.update { it.copy(projects = it.projects.copy(sessions =
                (listOf(session) + it.projects.sessions).distinctBy { row -> row.optString("id") })) }
            select(session)
        }
    }
    fun deleteSession(session: JSONObject) = action {
        val client = api ?: return@action
        val id = session.getString("id")
        val generation = epoch
        try { client.deleteSession(id) }
        catch (failure: ApiFailure) { if (failure.status != 404) throw failure }
        catch (_: IOException) { error("Deletion not confirmed. Refresh the conversation list to check before trying again.") }
        if (generation == epoch) mutable.update {
            val selected = it.session?.optString("id") == id
            it.copy(sessions = it.sessions.filterNot { row -> row.optString("id") == id },
                projects = it.projects.copy(sessions = it.projects.sessions.filterNot { row -> row.optString("id") == id }),
                search = it.search.copy(results = it.search.results.filterNot { row -> row.optString("id") == id }),
                session = if (selected) null else it.session,
                messages = if (selected) emptyList() else it.messages,
                permissions = if (selected) emptyList() else it.permissions,
                diffs = if (selected) emptyList() else it.diffs,
                drafts = it.drafts - id, attachments = it.attachments - id)
        }
    }
    fun loadComposerCatalog() {
        val client = api ?: return
        val session = state.value.session ?: return
        if (catalogJob?.isActive == true || state.value.composerCatalogLoaded) return
        val id = session.getString("id")
        val directory = session.optJSONObject("location")?.optString("directory").orEmpty()
        mutable.update { it.copy(composerLoading = true, composerError = null) }
        catalogJob = viewModelScope.launch {
            try {
                val (commands, skills) = coroutineScope {
                    val commands = async { client.composerCatalog(ComposerCatalogKind.COMMAND, directory) }
                    val skills = async { client.composerCatalog(ComposerCatalogKind.SKILL, directory) }
                    commands.await() to skills.await()
                }
                if (client === api && state.value.session?.optString("id") == id) mutable.update {
                    it.copy(commands = commands, skills = skills, composerLoading = false, composerCatalogLoaded = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (failure: Exception) {
                if (client === api && state.value.session?.optString("id") == id) mutable.update {
                    it.copy(composerLoading = false, composerError = failure.message.takeIf { failure is ApiFailure }
                        ?: "Could not load commands and skills from the PC.")
                }
            }
        }
    }
    fun send() = action {
        val id = state.value.session?.getString("id") ?: return@action
        val text = state.value.drafts[id].orEmpty()
        val files = state.value.attachments[id].orEmpty()
        if (text.isBlank() && files.isEmpty()) return@action
        if (state.value.importingFiles || state.value.missingProject) return@action
        val generation = epoch; val client = api ?: return@action
        if (id in state.value.workingSessions) return@action
        val snapshot = state.value
        val directory = snapshot.session?.optJSONObject("location")?.optString("directory").orEmpty()
        val submission = coroutineScope {
            val commands = async {
                if (!snapshot.composerCatalogLoaded && commandName(text) != null)
                    client.composerCatalog(ComposerCatalogKind.COMMAND, directory) else snapshot.commands
            }
            val skills = async {
                if (!snapshot.composerCatalogLoaded && hasSkillMention(text))
                    client.composerCatalog(ComposerCatalogKind.SKILL, directory) else snapshot.skills
            }
            composerSubmission(text, files, commands.await(), skills.await())
        }
        if (generation != epoch || state.value.session?.optString("id") != id || id in state.value.workingSessions) return@action
        try { client.post("/api/session/$id/${submission.endpoint}", submission.body) }
        catch (e: CancellationException) { throw e }
        catch (failure: ApiFailure) {
            if (failure.locationMissing && client === api && state.value.session?.optString("id") == id) {
                mutable.update { it.copy(missingProject = true) }
            } else throw failure
            return@action
        }
        catch (_: Exception) { error("Delivery not confirmed. Check the conversation before sending again; your draft was kept.") }
        if (generation == epoch) mutable.update {
            it.copy(drafts = if (it.drafts[id] == text) it.drafts - id else it.drafts,
                attachments = it.attachments + (id to it.attachments[id].orEmpty().filterNot { file -> file in files }))
        }
        refreshNow(client, generation)
    }
    suspend fun terminalSnapshot(path: String): JSONObject {
        val client = api ?: throw IOException("Connect to the PC to view its terminal.")
        val sessionId = state.value.session?.optString("id")
        val data = client.get(path)
        if (client !== api || sessionId != state.value.session?.optString("id")) throw CancellationException()
        return data
    }

    fun interrupt() {
        val client = api ?: return
        val id = state.value.session?.optString("id") ?: return
        if (state.value.interrupting || WorkKind.RESPONSE !in state.value.workingSessions[id].orEmpty()) return
        mutable.update { it.copy(interrupting = true) }
        viewModelScope.launch {
            try {
                val result = client.post("/api/session/$id/interrupt?resume=false")
                if (client === api && result.optBoolean("interrupted", false).not()) {
                    report("OpenCode did not find an active session response to stop. Separate terminal jobs may still be running.")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (client === api) report(e.message ?: "Could not stop this session.") }
            finally { if (client === api) mutable.update { it.copy(interrupting = false) } }
        }
    }
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

internal fun JSONObject.nextCursor(): String? = optJSONObject("cursor")?.let { if (it.isNull("next")) null else it.optString("next").ifBlank { null } }
