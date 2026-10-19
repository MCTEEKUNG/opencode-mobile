import Foundation

struct Computer: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    let name: String
    let origin: String
    let token: String
}

struct Location: Codable { let directory: String? }
struct SessionInfo: Codable, Identifiable {
    let id: String
    let title: String?
    let location: Location
}
struct Cursor: Decodable { let next: String?; let previous: String? }
struct Page<T: Decodable>: Decodable { let data: [T]; let cursor: Cursor }
struct Envelope<T: Decodable>: Decodable { let data: T }
struct PairingSession: Decodable { let token: String }
struct ServerInfo: Decodable { let version: String }

// Preserve unknown tool fields rather than dropping useful information as the V2 API evolves.
enum JSONValue: Codable {
    case string(String), number(Double), bool(Bool), object([String: JSONValue]), array([JSONValue]), null
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode([String: JSONValue].self) { self = .object(v) }
        else { self = .array(try c.decode([JSONValue].self)) }
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        switch self {
        case .null: try c.encodeNil()
        case .string(let v): try c.encode(v)
        case .number(let v): try c.encode(v)
        case .bool(let v): try c.encode(v)
        case .object(let v): try c.encode(v)
        case .array(let v): try c.encode(v)
        }
    }
    var pretty: String {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return (try? String(data: encoder.encode(self), encoding: .utf8)) ?? ""
    }
    var text: String? { if case .string(let v) = self { return v }; return nil }
}
struct Message: Decodable, Identifiable {
    let id: String
    let type: String
    let text: String?
    let content: [[String: JSONValue]]?
    var displayText: String {
        text ?? content?.map { $0["text"]?.text ?? JSONValue.object($0).pretty }.joined(separator: "\n\n") ?? type
    }
}
struct PermissionRequest: Decodable, Identifiable {
    let id: String
    let sessionID: String
    let action: String
    let resources: [String]
    let metadata: JSONValue?
    let source: JSONValue?
    let message: String?
    var detail: String {
        [resources.joined(separator: "\n"), message ?? "", metadata?.pretty ?? "", source?.pretty ?? ""]
            .filter { !$0.isEmpty }.joined(separator: "\n\n")
    }
}
struct FileDiff: Decodable, Identifiable {
    let file: String
    let patch: String
    let additions: Int
    let deletions: Int
    var id: String { file }
}
