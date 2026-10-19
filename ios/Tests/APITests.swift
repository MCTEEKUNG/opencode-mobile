import XCTest
@testable import OpenCodeMobile

final class StubProtocol: URLProtocol {
    static var handler: ((URLRequest) throws -> (Int, Data))?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            guard let handler = Self.handler else { throw URLError(.badServerResponse) }
            let (code, data) = try handler(request)
            let response = HTTPURLResponse(url: request.url!, statusCode: code, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data); client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() { }
}

final class APITests: XCTestCase {
    override func tearDown() { StubProtocol.handler = nil; super.tearDown() }
    private func api() -> API {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubProtocol.self]
        return API(Computer(name: "PC", origin: "https://pc.example", token: "test-token"), configuration: configuration)
    }
    func testBasicAuthenticationAndV2Prompt() async throws {
        StubProtocol.handler = { request in
            XCTAssertEqual(request.url?.path, "/api/session/s1/prompt")
            XCTAssertEqual(request.httpMethod, "POST")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Basic b3BlbmNvZGU6dGVzdC10b2tlbg==")
            XCTAssertEqual(request.value(forHTTPHeaderField: "Accept"), "application/json")
            // URLProtocol may receive the body as a stream; the transport test
            // verifies headers/route, while Android's fixture verifies JSON bytes.
            return (200, Data(#"{"data":{}}"#.utf8))
        }
        _ = try await api().post("/api/session/s1/prompt", body: ["text": "hello"])
    }
    func testPermission204() async throws {
        StubProtocol.handler = { _ in (204, Data()) }
        let data = try await api().post("/api/session/s1/permission/r1/reply", body: ["decision": "once"])
        XCTAssertTrue(data.isEmpty)
    }
    func testExpiredAuthorization() async throws {
        StubProtocol.handler = { _ in (401, Data()) }
        do {
            let _: ServerInfo = try await api().get("/api/info")
            XCTFail("Unauthorized request was accepted")
        } catch let error as MobileError { XCTAssertTrue(error.requiresPairing) }
    }
    func testV2PageDecode() throws {
        let data = Data(#"{"data":[{"id":"s1","title":"Review","location":{"directory":"/workspace"}}],"cursor":{"next":"opaque","previous":null}}"#.utf8)
        let page = try JSONDecoder().decode(Page<SessionInfo>.self, from: data)
        XCTAssertEqual(page.data.first?.location.directory, "/workspace")
        XCTAssertEqual(page.cursor.next, "opaque")
    }
}
