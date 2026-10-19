import Foundation
import Security

struct Vault {
    private let service = "dev.opencode.mobile.computers"
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: "computers"]
    }
    func load() throws -> [Computer] {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &result)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess, let data = result as? Data else { throw MobileError.message("Cannot unlock saved computers. Credentials were preserved.") }
        return try JSONDecoder().decode([Computer].self, from: data)
    }
    func save(_ computers: [Computer]) throws {
        let data = try JSONEncoder().encode(computers)
        let attrs: [String: Any] = [kSecValueData as String: data,
                                   kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
        if status == errSecItemNotFound {
            var q = query; attrs.forEach { q[$0.key] = $0.value }
            guard SecItemAdd(q as CFDictionary, nil) == errSecSuccess else { throw MobileError.message("Cannot save credentials") }
        } else if status != errSecSuccess { throw MobileError.message("Cannot save credentials") }
    }
}
