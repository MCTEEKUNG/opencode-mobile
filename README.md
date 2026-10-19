# OpenCode Mobile

Two independent native clients for **OpenCode V2**: Kotlin/Jetpack Compose on Android and Swift/SwiftUI on iOS. No WebView, hosted AI backend, or PC companion process.

> Initial implementation, not a verified release. Native compilation and device testing require the toolchains below. The Python diagnostics do not compile or test the mobile applications.

## Features implemented

- Scan a QR code or paste an HTTPS pairing link; confirm the server origin before redemption.
- Persist multiple computers using Android Keystore-backed AES-GCM or iOS Keychain.
- List sessions, load older pages, create a session at an absolute directory on the PC.
- Read messages/tool content, send prompts, interrupt execution.
- Review pending permission details and allow once/reject.
- Read per-file patches for the most recent turn.
- SSE invalidation with throttled snapshot refresh, reconnect/backoff, foreground resync.
- Keep in-memory drafts per session; never automatically resend an uncertain prompt.

### Deliberate first-release limits

- **HTTPS only**. Use `opencode pair --remote` or an existing trusted HTTPS endpoint. LAN HTTP, self-signed-certificate bypasses and loopback QR links are rejected.
- Android QR scanning uses Google Play services; devices without it can paste the link. The scanner module may need downloading on first use.
- No remote desktop, full terminal, file editing, model/agent picker, structured form replies, voice, push notifications or background streaming yet.
- Android shows selectable plain text/Markdown source; iOS also currently uses selectable text. Rich Markdown and code highlighting remain follow-ups.
- Drafts survive Android activity recreation through its ViewModel, but neither platform persists drafts across process termination.
- App data is not an offline conversation archive. The PC must remain awake with its service running.
- Forgetting a computer deletes the local credential; it does **not** revoke the server-side session.
- Pagination refresh re-fetches the loaded window. Very long histories need a more efficient repository before production release.

## Pair your PC

On the computer running OpenCode V2:

```sh
opencode pair --remote
```

Confirm remote access when prompted, then scan the QR **inside this app**, or paste the link in Add Computer. Do not open the one-time link in a browser first: doing so consumes it.

Default `opencode pair` may print a `127.0.0.1` link. That address refers to the phone when scanned, not the PC. `--remote` provides the remotely accessible address. Enabling it exposes the service through OpenCode's remote transport; only enable remote access intentionally.

Pairing flow:

1. Validate `https://<host>/auth/connect/<code>` and confirm the origin.
2. `GET` that URL with `Accept: application/json`, without existing credentials.
3. Receive `{ "token": "…" }`.
4. Authenticate subsequent requests with HTTP Basic, username `opencode`, password equal to the token.
5. Fetch a snapshot after the SSE subscription opens and whenever events arrive.

All redirects are refused, certificate verification stays enabled, and credentials are never sent through query strings. The Basic username was verified against the local installed V2 service; pairing-token authentication and remote transport still require end-to-end testing on physical devices. Pairing links expire after five minutes and are single-use; official docs describe sessions lasting 30 days.

## Android build

Requirements: JDK 17, Android SDK platform 35/build tools, **Gradle 8.11.1**, Android Studio or Android command-line tools. Minimum Android 8.0/API 26.

Open `android/` in Android Studio and select a local Gradle 8.11.1 installation, or run from that directory:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

Set `ANDROID_HOME` or create your own ignored `android/local.properties` containing `sdk.dir=…`. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

A Gradle wrapper binary is not checked in. After installing Gradle, optionally generate one locally with `gradle wrapper --gradle-version 8.11.1`. CI uses `gradle/actions/setup-gradle` with the exact version.

## iOS build

Requirements: **macOS, Xcode 16+**, XcodeGen, iOS 17+. No third-party runtime dependencies.

```sh
brew install xcodegen
```

From `ios/`:

```sh
xcodegen generate
open OpenCodeMobile.xcodeproj
```

Choose your development team in Signing & Capabilities, select an iPhone and Run. The Xcode project is generated from `ios/project.yml`; edit that file rather than the generated project. Camera scanning needs a physical device; simulator users can paste links.

Run the `OpenCodeMobile` scheme's tests in Xcode. The GitHub workflow selects an available iPhone simulator automatically and runs `xcodebuild test` without distribution signing.

## Verification available on Linux without mobile SDKs

```sh
python3 -m unittest discover -s tools -v
python3 tools/contract_check.py
```

The second command uses the existing local OpenCode service registration at `~/.local/state/opencode/service.json`. It performs **read-only** checks of required OpenAPI operations, authentication, session/message/permission response envelopes and SSE framing. It does not create sessions, redeem QR codes, send prompts, or log session content/secrets. An alternate registration can be selected with `--service-file`.

## Layout

```text
android/             Independent Compose app and JVM tests
ios/                 Independent SwiftUI app, XcodeGen spec and XCTest tests
tools/               Read-only contract diagnostics and their Python tests
docs/                Architecture and physical-device acceptance checklist
.github/workflows/   Android build/lint/tests and iOS simulator tests
```

Official references: [pairing](https://opencode.ai/v2/docs/cli/web/), [HTTP API](https://opencode.ai/v2/docs/api/), [event contract](https://opencode.ai/v2/docs/build/client/).
