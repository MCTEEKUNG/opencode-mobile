import Foundation

struct PairingLink {
    let url: URL
    let origin: String
    init(_ raw: String) throws {
        guard let c = URLComponents(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              c.scheme?.lowercased() == "https", let host = c.host, !host.isEmpty,
              c.user == nil, c.password == nil, c.query == nil, c.fragment == nil,
              !["localhost", "127.0.0.1", "::1", "[::1]"].contains(host.lowercased()),
              c.percentEncodedPath.range(of: #"^/auth/connect/[A-Za-z0-9_-]+$"#, options: .regularExpression) != nil,
              let url = c.url else {
            throw MobileError.message("Use an HTTPS pairing link from opencode pair --remote, without credentials, query or fragment. PC localhost is not reachable from your phone.")
        }
        var base = c; base.path = ""; base.query = nil; base.fragment = nil
        guard let origin = base.url?.absoluteString else { throw MobileError.message("Invalid server address") }
        self.url = url; self.origin = origin
    }
}
