import Foundation

enum MobileError: LocalizedError {
    case http(Int), message(String)
    var errorDescription: String? {
        switch self {
        case .message(let text): return text
        case .http(401), .http(403): return "Access denied. Pair this computer again."
        case .http(404): return "Item no longer exists or pairing code expired."
        case .http(300..<400): return "Redirect refused. Use the final HTTPS pairing address."
        case .http(let code): return "Server returned HTTP \(code)"
        }
    }
    var requiresPairing: Bool {
        if case .http(let code) = self { return code == 401 || code == 403 }; return false
    }
}

final class NoRedirect: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

final class API: @unchecked Sendable {
    let computer: Computer
    private let session: URLSession
    init(_ computer: Computer, configuration: URLSessionConfiguration = .ephemeral) {
        self.computer = computer
        let config = configuration
        config.urlCredentialStorage = nil; config.httpCookieStorage = nil
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60 * 60 * 24
        session = URLSession(configuration: config, delegate: NoRedirect(), delegateQueue: nil)
    }
    deinit { session.invalidateAndCancel() }
    private func request(_ path: String) throws -> URLRequest {
        guard let url = URL(string: computer.origin + path) else { throw MobileError.message("Invalid server URL") }
        var req = URLRequest(url: url)
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        let credentials = Data("opencode:\(computer.token)".utf8).base64EncodedString()
        req.setValue("Basic \(credentials)", forHTTPHeaderField: "Authorization")
        return req
    }
    private func validate(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { throw MobileError.message("Invalid response") }
        guard (200..<300).contains(http.statusCode) else { throw MobileError.http(http.statusCode) }
    }
    static func redeem(_ link: PairingLink) async throws -> String {
        let api = API(Computer(name: "", origin: link.origin, token: ""))
        // Do not attach authentication to a one-time redemption URL.
        var req = URLRequest(url: link.url); req.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await api.session.data(for: req)
        try api.validate(response)
        let token = try JSONDecoder().decode(PairingSession.self, from: data).token
        guard !token.isEmpty else { throw MobileError.message("Invalid pairing response") }
        return token
    }
    func get<T: Decodable>(_ path: String) async throws -> T {
        let (data, response) = try await session.data(for: request(path))
        try validate(response)
        return try JSONDecoder().decode(T.self, from: data)
    }
    func post(_ path: String, body: [String: Any] = [:]) async throws -> Data {
        var req = try request(path); req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await session.data(for: req)
        try validate(response); return data
    }
    func events(onChange: @Sendable () async -> Void) async throws {
        var req = try request("/api/event"); req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        let (bytes, response) = try await session.bytes(for: req)
        try validate(response)
        guard (response as? HTTPURLResponse)?.mimeType == "text/event-stream" else { throw MobileError.message("Server did not return an event stream") }
        await onChange()
        for try await line in bytes.lines {
            try Task.checkCancellation()
            // Payloads are invalidation signals, not deltas. This also handles line
            // sequences that omit empty SSE separators and multiline data fields.
            if line.hasPrefix("data:") { await onChange() }
        }
        throw MobileError.message("Event connection closed")
    }
    static func segment(_ raw: String) -> String {
        raw.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
    }
}
