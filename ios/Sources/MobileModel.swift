import Foundation
import Observation

@MainActor @Observable final class MobileModel {
    var computers: [Computer] = []
    var computer: Computer?
    var sessions: [SessionInfo] = []
    var selected: SessionInfo?
    var messages: [Message] = []
    var permissions: [PermissionRequest] = []
    var diffs: [FileDiff] = []
    var drafts: [String: String] = [:]
    var nextMessages: String?
    var nextSessions: String?
    var status = "Disconnected"
    var error: String?
    var busy = false
    private let vault = Vault()
    private var api: API?
    private var epoch = 0
    private var active = false
    private var dirty = false
    private var refreshRunning = false
    private var messageLimit = 100
    private var sessionLimit = 50
    private var snapshotRevision = 0
    private var lastConnected = Date.distantPast
    private var eventsTask: Task<Void, Never>?
    private var syncTask: Task<Void, Never>?

    init() {
        do { computers = try vault.load() }
        catch { self.error = "Cannot unlock saved computers. Existing credentials were preserved." }
    }
    func setActive(_ value: Bool) {
        active = value
        eventsTask?.cancel(); eventsTask = nil; syncTask?.cancel(); syncTask = nil
        if value { startConnection() } else { status = computer == nil ? "Disconnected" : "Paused" }
    }
    func connect(_ computer: Computer) {
        eventsTask?.cancel(); syncTask?.cancel(); epoch += 1
        self.computer = computer; api = API(computer)
        snapshotRevision += 1
        selected = nil; sessions = []; messages = []; permissions = []; diffs = []; drafts = [:]
        messageLimit = 100; sessionLimit = 50
        nextMessages = nil; nextSessions = nil
        status = "Connecting…"; startConnection()
    }
    private func startConnection() {
        guard active, let client = api else { return }
        let generation = epoch
        eventsTask = Task { [weak self] in
            var attempt = 0
            while !Task.isCancelled {
                guard let self, generation == self.epoch else { return }
                do {
                    try await client.events { [weak self] in
                        await self?.receivedEvent(generation)
                    }
                } catch {
                    if Task.isCancelled || generation != self.epoch { return }
                    if let failure = error as? MobileError, failure.requiresPairing {
                        self.status = "Pair again"; self.error = failure.localizedDescription; return
                    }
                    self.status = "Reconnecting…"
                    if Date().timeIntervalSince(self.lastConnected) < 2 { attempt = 0 }
                    let seconds = min(32.0, pow(2, Double(attempt))) + Double.random(in: 0...0.25)
                    do { try await Task.sleep(for: .seconds(seconds)) } catch { return }
                    attempt = min(attempt + 1, 5)
                }
            }
        }
        syncTask = Task { [weak self] in
            var failures = 0
            while !Task.isCancelled {
                guard let self, generation == self.epoch else { return }
                if self.dirty {
                    self.dirty = false
                    do { try await self.refreshNow(client, generation: generation); failures = 0 }
                    catch {
                        if Task.isCancelled || generation != self.epoch { return }
                        self.status = "Sync failed"
                        self.error = (error as? MobileError)?.localizedDescription ?? "Cannot synchronize server data. Check V2 compatibility or try Refresh."
                        if let failure = error as? MobileError {
                            if failure.requiresPairing { self.status = "Pair again"; self.eventsTask?.cancel(); return }
                            if case .http(404) = failure { self.select(nil) }
                        }
                        failures = min(failures + 1, 5)
                        do { try await Task.sleep(for: .seconds(pow(2, Double(failures)))) } catch { return }
                        self.dirty = true
                    }
                }
                do { try await Task.sleep(for: .milliseconds(500)) } catch { return }
            }
        }
    }
    private func receivedEvent(_ generation: Int) {
        guard generation == epoch else { return }; lastConnected = Date(); dirty = true
    }
    func select(_ session: SessionInfo?) {
        selected = session; messages = []; permissions = []; diffs = []; nextMessages = nil
        snapshotRevision += 1
        messageLimit = 100
        dirty = true
    }
    func refresh() { perform { try await self.refreshCurrent() } }
    private func refreshCurrent() async throws {
        guard let api else { return }; try await refreshNow(api, generation: epoch)
    }
    private func refreshNow(_ client: API, generation: Int) async throws {
        guard !refreshRunning else { dirty = true; return }
        refreshRunning = true; defer { refreshRunning = false }
        let revision = snapshotRevision
        let id = selected?.id
        do {
            let page: Page<SessionInfo> = try await client.get("/api/session?order=desc&limit=\(sessionLimit)")
            var messagesPage: Page<Message>?
            var pending: [PermissionRequest] = []
            if let id {
                messagesPage = try await client.get("/api/session/\(API.segment(id))/message?order=desc&limit=\(messageLimit)")
                let response: Envelope<[PermissionRequest]> = try await client.get("/api/session/\(API.segment(id))/permission")
                pending = response.data
            }
            guard generation == epoch, selected?.id == id, revision == snapshotRevision, !Task.isCancelled else { dirty = true; return }
            sessions = page.data; nextSessions = page.cursor.next
            messages = messagesPage.map { Array($0.data.reversed()) } ?? []; nextMessages = messagesPage?.cursor.next
            permissions = pending
            status = "Connected"
        } catch {
            // A late failure belongs to the same snapshot identity as its data.
            // In particular, A's 404 must never clear newly selected session B.
            guard generation == epoch, selected?.id == id, revision == snapshotRevision else {
                dirty = true
                return
            }
            throw error
        }
    }
    func pair(_ link: PairingLink, name: String) {
        perform {
            let stored = try self.vault.load()
            let token = try await API.redeem(link)
            let computer = Computer(name: name.isEmpty ? "My computer" : name, origin: link.origin, token: token)
            let _: ServerInfo = try await API(computer).get("/api/info")
            let updated = stored.filter { $0.origin != computer.origin } + [computer]
            try self.vault.save(updated); self.computers = updated; self.connect(computer)
        }
    }
    func forget(_ computer: Computer) {
        perform {
            let updated = try self.vault.load().filter { $0.id != computer.id }
            try self.vault.save(updated); self.computers = updated
            if self.computer?.id == computer.id {
                self.eventsTask?.cancel(); self.syncTask?.cancel(); self.epoch += 1
                self.computer = nil; self.api = nil; self.select(nil); self.sessions = []; self.drafts = [:]
                self.status = "Disconnected"
            }
        }
    }
    func create(directory: String) {
        perform {
            guard directory.hasPrefix("/") || directory.range(of: #"^[A-Za-z]:[\\/]"#, options: .regularExpression) != nil else {
                throw MobileError.message("Enter an absolute directory on your PC")
            }
            guard let api = self.api else { return }; let generation = self.epoch
            let data = try await api.post("/api/session", body: ["location": ["directory": directory]])
            let session = try JSONDecoder().decode(Envelope<SessionInfo>.self, from: data).data
            if generation == self.epoch { self.select(session) }
        }
    }
    func send() {
        guard let id = selected?.id, let text = drafts[id], !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        perform {
            guard let api = self.api else { return }; let generation = self.epoch
            do { _ = try await api.post("/api/session/\(API.segment(id))/prompt", body: ["text": text]) }
            catch { throw MobileError.message("Delivery not confirmed. Check the conversation before sending again; your draft was kept.") }
            if generation == self.epoch, self.drafts[id] == text { self.drafts[id] = nil }
            self.dirty = true
        }
    }
    func interrupt() { sessionAction { api, id in _ = try await api.post("/api/session/\(id)/interrupt") } }
    func reply(_ request: PermissionRequest, decision: String) {
        sessionAction { api, id in _ = try await api.post("/api/session/\(id)/permission/\(API.segment(request.id))/reply", body: ["decision": decision]) }
    }
    func loadDiff() {
        sessionAction { api, id in
            let response: Envelope<[FileDiff]> = try await api.get("/api/session/\(id)/diff")
            if api === self.api, API.segment(self.selected?.id ?? "") == id { self.diffs = response.data }
        }
    }
    func moreMessages() {
        sessionAction { api, id in
            guard let cursor = self.nextMessages else { return }
            self.snapshotRevision += 1
            let page: Page<Message> = try await api.get("/api/session/\(id)/message?cursor=\(API.segment(cursor))&limit=100")
            guard api === self.api, API.segment(self.selected?.id ?? "") == id else { return }
            var seen = Set<String>()
            self.messages = (Array(page.data.reversed()) + self.messages).filter { seen.insert($0.id).inserted }
            self.messageLimit = max(self.messageLimit, self.messages.count)
            self.nextMessages = page.cursor.next
            self.snapshotRevision += 1
        }
    }
    func moreSessions() {
        perform {
            guard let api = self.api, let cursor = self.nextSessions else { return }
            self.snapshotRevision += 1
            let page: Page<SessionInfo> = try await api.get("/api/session?cursor=\(API.segment(cursor))&limit=50")
            guard api === self.api else { return }
            var seen = Set<String>()
            self.sessions = (self.sessions + page.data).filter { seen.insert($0.id).inserted }; self.nextSessions = page.cursor.next
            self.sessionLimit = max(self.sessionLimit, self.sessions.count)
            self.snapshotRevision += 1
        }
    }
    private func sessionAction(_ block: @escaping (API, String) async throws -> Void) {
        perform {
            guard let api = self.api, let id = self.selected?.id else { return }
            try await block(api, API.segment(id)); self.dirty = true
        }
    }
    private func perform(_ block: @escaping () async throws -> Void) {
        guard !busy else { return }; busy = true; error = nil
        let generation = epoch
        Task {
            defer { busy = false }
            do { try await block() }
            catch is CancellationError { }
            catch {
                guard generation == epoch else { return }
                self.error = (error as? MobileError)?.localizedDescription ?? "Connection failed or server response is incompatible. Try refreshing; pair again if access expired."
            }
        }
    }
}
