import XCTest
@testable import OpenCodeMobile

final class PairingLinkTests: XCTestCase {
    func testValidLink() throws {
        let link = try PairingLink(" https://pc.example:8443/auth/connect/abc_123-XYZ ")
        XCTAssertEqual(link.origin, "https://pc.example:8443")
    }
    func testUnsafeLinks() {
        for raw in ["http://pc.example/auth/connect/x", "https://user:pass@pc.example/auth/connect/x",
                    "https://pc.example/auth/connect/x?token=oops", "https://pc.example/auth/connect/x#fragment",
                    "https://localhost/auth/connect/x", "https://127.0.0.1/auth/connect/x",
                    "https://pc.example/not-pairing", "https://pc.example/auth/connect/a/b", "https://pc.example/auth/connect/%2F"] {
            XCTAssertThrowsError(try PairingLink(raw))
        }
    }
    func testV2MessageProjection() throws {
        let raw = #"{"id":"m1","type":"assistant","content":[{"type":"text","text":"hello"},{"type":"tool","tool":"shell","state":{"status":"running"}}]}"#
        let message = try JSONDecoder().decode(Message.self, from: Data(raw.utf8))
        XCTAssertTrue(message.displayText.contains("hello"))
        XCTAssertTrue(message.displayText.contains("shell"))
    }
}
