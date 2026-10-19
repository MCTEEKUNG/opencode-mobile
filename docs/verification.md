# Verification record

Environment: Linux; installed OpenCode CLI reports `v2.0.32`.

## Executed

```sh
python3 -W error::ResourceWarning -m unittest discover -s tools -v
```

Result: **9 Python tests passed**. These cover the diagnostic client's Basic headers, JSON envelope expectations, missing/changed API operation detection, unsafe origins and refusal to follow redirects. These are not native application tests.

```sh
python3 tools/contract_check.py
```

Result: authenticated read-only checks against the existing local service passed:

- 11 required API operations and prompt/permission request contracts.
- Server info with Basic authentication.
- V2 session-list envelope.
- Message-list and pending-permission envelopes.
- SSE content type and initial data-event framing.

No prompts, new sessions, permission replies, pairing redemptions or remote-access changes were made by these checks. Existing message content and credentials were not printed.

## Not executed

- Android Gradle compilation, lint and JVM tests: no configured JDK/Android SDK/Gradle toolchain.
- iOS compilation/XCTest: no macOS/Xcode toolchain.
- GitHub Actions: workflow added, not run from this local directory.
- QR redemption on actual devices, remote transport, camera and physical-device acceptance.

Native tests are supplied in `android/app/src/test/` and `ios/Tests/`. Their presence must not be interpreted as a passing result. See `acceptance.md` for the release gate.
