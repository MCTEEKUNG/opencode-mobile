#!/usr/bin/env python3
"""Read-only OpenCode V2 contract smoke test. Never redeems codes or sends prompts.

Uses the existing private service registration. No credentials, URLs, titles,
messages or file contents are printed or written to disk.
"""
import argparse
import base64
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Client:
    def __init__(self, origin, password):
        parsed = urllib.parse.urlsplit(origin)
        if parsed.scheme != "https" and not (
            parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"}
        ):
            raise ValueError("Diagnostics require HTTPS or local loopback")
        if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in {"", "/"}:
            raise ValueError("Invalid origin")
        self.origin = origin.rstrip("/")
        self.auth = "Basic " + base64.b64encode(("opencode:" + password).encode()).decode()
        # Do not send diagnostic credentials via ambient HTTP proxy settings.
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def open(self, path, accept="application/json"):
        if not path.startswith("/") or path.startswith("//"):
            raise ValueError("Expected relative API path")
        req = urllib.request.Request(self.origin + path, headers={"Authorization": self.auth, "Accept": accept})
        return self.opener.open(req, timeout=10)

    def get(self, path):
        with self.open(path) as response:
            return json.load(response)


OPERATIONS = {
    "/api/info": "get",
    "/api/pair": "post",
    "/auth/connect/{code}": "get",
    "/api/session": "get",
    "/api/session/{sessionID}/prompt": "post",
    "/api/session/{sessionID}/message": "get",
    "/api/session/{sessionID}/permission": "get",
    "/api/session/{sessionID}/permission/{requestID}/reply": "post",
    "/api/session/{sessionID}/interrupt": "post",
    "/api/session/{sessionID}/diff": "get",
    "/api/event": "get",
}


def validate_spec(spec):
    for path, method in OPERATIONS.items():
        if method not in spec.get("paths", {}).get(path, {}):
            raise ValueError("Missing operation: " + method.upper() + " " + path)
    for path, field in [
        ("/api/session/{sessionID}/prompt", "text"),
        ("/api/session/{sessionID}/permission/{requestID}/reply", "decision"),
    ]:
        schema = spec["paths"][path]["post"]["requestBody"]["content"]["application/json"]["schema"]
        if field not in schema.get("required", []):
            raise ValueError("Request contract changed: " + path)
    return len(OPERATIONS)


def page(value):
    if not isinstance(value, dict) or not isinstance(value.get("data"), list) or not isinstance(value.get("cursor"), dict):
        raise ValueError("Expected V2 paginated data/cursor envelope")
    return value["data"]


def smoke(client):
    spec = client.get("/openapi.json")
    count = validate_spec(spec)
    print(f"PASS: {count} required API operations and mutation request contracts")
    info = client.get("/api/info")
    if not isinstance(info.get("version"), str):
        raise ValueError("Missing server version")
    print("PASS: authenticated server info using Basic opencode:<password>")
    sessions = page(client.get("/api/session?order=desc&limit=1"))
    print("PASS: session list V2 envelope")
    if sessions:
        sid = urllib.parse.quote(sessions[0]["id"], safe="")
        page(client.get(f"/api/session/{sid}/message?order=desc&limit=1"))
        permissions = client.get(f"/api/session/{sid}/permission")
        if not isinstance(permissions.get("data"), list):
            raise ValueError("Expected permission data array")
        print("PASS: message and pending-permission envelopes")
    else:
        print("SKIP: message/permission reads (no sessions)")
    with client.open("/api/event", "text/event-stream") as response:
        if response.headers.get_content_type() != "text/event-stream":
            raise ValueError("Expected SSE content type")
        # Validate initial framing without retaining potentially sensitive payloads.
        for _ in range(32):
            line = response.readline(65537)
            if len(line) > 65536:
                raise ValueError("Initial SSE line exceeds diagnostic limit")
            if line.startswith(b"data:"):
                print("PASS: authenticated SSE connection and initial event")
                break
            if not line:
                raise ValueError("SSE ended before initial event")
        else:
            raise ValueError("No initial SSE data event")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--service-file", type=Path, default=Path.home() / ".local/state/opencode/service.json")
    args = parser.parse_args()
    try:
        registration = json.loads(args.service_file.read_text())
        smoke(Client(registration["url"], registration["password"]))
    except urllib.error.HTTPError as error:
        print(f"FAIL: HTTP {error.code}; no credentials or response bodies were logged")
        return 1
    except Exception as error:
        # Exception messages from URL libraries may include secret-bearing URLs.
        print(f"FAIL: {type(error).__name__}; verify service status and V2 compatibility")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
