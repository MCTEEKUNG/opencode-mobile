import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError

from contract_check import Client, OPERATIONS, page, validate_spec


class ContractTests(unittest.TestCase):
    def spec(self):
        paths = {path: {method: {}} for path, method in OPERATIONS.items()}
        for path, field in [("/api/session/{sessionID}/prompt", "text"),
                            ("/api/session/{sessionID}/permission/{requestID}/reply", "decision")]:
            paths[path]["post"]["requestBody"] = {"content": {"application/json": {"schema": {"required": [field]}}}}
        return {"paths": paths}

    def test_supported_contract(self):
        self.assertEqual(validate_spec(self.spec()), 11)

    def test_rejects_missing_endpoint(self):
        spec = self.spec(); del spec["paths"]["/api/event"]
        with self.assertRaises(ValueError):
            validate_spec(spec)

    def test_rejects_changed_mutation(self):
        spec = self.spec()
        spec["paths"]["/api/session/{sessionID}/prompt"]["post"]["requestBody"]["content"]["application/json"]["schema"]["required"] = ["parts"]
        with self.assertRaises(ValueError):
            validate_spec(spec)

    def test_v2_page(self):
        self.assertEqual(page({"data": [{"id": "s1"}], "cursor": {"next": None}}), [{"id": "s1"}])

    def test_rejects_legacy_envelope(self):
        for payload in [[], {"data": []}, {"data": {}, "cursor": {}}]:
            with self.assertRaises(ValueError):
                page(payload)

    def test_refuses_public_plaintext(self):
        for origin in ["http://pc.example", "https://user:pass@pc.example", "https://pc.example?x=y"]:
            with self.assertRaises(ValueError):
                Client(origin, "not-a-real-token")


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.received = []
        received = self.received

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                received.append((self.path, self.headers.get("Authorization"), self.headers.get("Accept")))
                if self.path == "/redirect":
                    self.send_response(302); self.send_header("Location", "/secret-destination"); self.end_headers()
                    return
                data = json.dumps({"data": [], "cursor": {}}).encode()
                self.send_response(200); self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True); self.thread.start()
        self.client = Client(f"http://127.0.0.1:{self.server.server_port}", "test-token")

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join()

    def test_basic_auth_and_json(self):
        self.assertEqual(self.client.get("/api/session"), {"data": [], "cursor": {}})
        self.assertEqual(self.received, [("/api/session", "Basic b3BlbmNvZGU6dGVzdC10b2tlbg==", "application/json")])

    def test_redirect_never_followed(self):
        with self.assertRaises(HTTPError) as error:
            self.client.get("/redirect")
        self.assertEqual(error.exception.code, 302)
        error.exception.close()
        self.assertEqual(len(self.received), 1)

    def test_rejects_absolute_request_target(self):
        with self.assertRaises(ValueError):
            self.client.get("https://other.example/api/info")
        self.assertEqual(self.received, [])


if __name__ == "__main__":
    unittest.main()
