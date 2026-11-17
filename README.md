# OpenCode Mobile

OpenCode Mobile is an independent native client for OpenCode V2. Connect to the OpenCode service on your PC to open sessions, send prompts, and review activity from your phone.

## Install on Android

1. Download the signed Android APK from [GitHub Releases](https://github.com/MCTEEKUNG/opencode-mobile/releases/latest).
2. Open the APK on Android 8.0 or later. If Android prompts you, allow installation from the app you used to open the file.
3. Open **OpenCode Mobile**. Go to **Settings > Connect another computer**.
4. To pair over Tailscale, follow the [pair-mobile installation guide](docs/pairing-cli.md). To pair over home Wi-Fi, see [Pair your PC](#pair-your-pc).

If you already installed a locally built debug APK, Android may ask you to remove it before installing the signed release. Removing the app also removes its locally saved pairings.

The iOS client is available as source. See [iOS build](#ios-build) for installation through Xcode.

## Features implemented

- Scan a QR code or paste a pairing link; confirm the server origin before redemption.
- Persist multiple computers using Android Keystore-backed AES-GCM or iOS Keychain.
- Android Settings centralizes Follow system/Light/Dark appearance and saved-computer pairing, rename and removal. The top computer selector remains available for quick switching.
- Android groups sessions by working directory and can create project folders on the PC.
- List conversations with newest activity first. Android can hide history or clear existing chats from the home list per saved computer; Restore full history brings them back without changing the server. Live OpenCode events show a small working robot beside active chats.
- Android groups models by provider and shows a lightning toggle when OpenCode offers a compatible Fast model or variant.
- Android: search conversation titles across the selected PC's history, with live results, clear/retry controls and paginated matches. Searches stay with their saved computer.
- Read messages, send prompts or project slash commands, attach a server skill with @, and interrupt an active response. Android has a separate Live terminal tab for inspecting tool output, terminal snapshots and reported background jobs.
- Android: inline image previews with full-screen zoom, open attached files, and attach photos/documents from the system picker. Up to 6 files, 15 MB per file, about 25 MB total per message. Attachments remain in memory until sent; uncertain delivery keeps them in the draft.
- Review pending permission details and allow once/reject.
- Read per-file patches for the most recent turn.
- SSE invalidation with throttled snapshot refresh, reconnect/backoff, foreground resync.
- Foreground connection status requires a live SSE stream and authenticated server heartbeat. Probes run every 3 seconds with a 3-second deadline; Android network-loss callbacks update the UI immediately when available. A silent outage is normally detected within about 6 seconds. Lost connections show the computer name and reconnect automatically.
- Keep in-memory drafts per session; never automatically resend an uncertain prompt.

### Deliberate first-release limits

- **Android same-Wi-Fi mode**: scan a localhost QR from `opencode pair`, enter the computer's private IPv4 address, then explicitly confirm HTTP. Ports and pairing codes are preserved. HTTP is accepted for private Wi-Fi IPv4 addresses (10.x, 172.16–31.x, 192.168.x) and, for Tailscale IPv4 addresses (100.64.0.0/10), only through an active VPN route. Other connections require HTTPS. iOS still requires HTTPS.
- Android QR scanning uses Google Play services; devices without it can paste the link. The scanner module may need downloading on first use.
- Android supports searching enabled server models and changing the current session model. Agent selection, remote desktop, interactive terminal input, file editing, structured form replies, voice, push notifications and background streaming remain follow-ups.
- Android chat renders headings, bold text, inline code and fenced code blocks, with selectable text, copy, timestamps and collapsible reasoning. Commands, tool output and tool-generated files are available in Live terminal. Swipe a conversation to the right to open its deletion confirmation.
- Drafts survive Android activity recreation through its ViewModel, but neither platform persists drafts across process termination.
- App data is not an offline conversation archive. The PC must remain awake with its service running.
- Forgetting a computer deletes the local credential; it does **not** revoke the server-side session.
- Pagination refresh re-fetches the loaded window. Very long histories need a more efficient repository before production release.

## Pair your PC

Keep the computer awake and the OpenCode V2 service running. For access away from home, use the [pair-mobile installation guide](docs/pairing-cli.md). For home Wi-Fi:

1. Join the same Wi-Fi network on the computer and phone. Allow the OpenCode service port on your private network.
2. On the computer, check the listener with `opencode service status`. If it listens on localhost, run `opencode service set hostname 0.0.0.0` followed by `opencode service start`. Use a firewall to limit LAN access to your trusted network.
3. Run `opencode pair --url http://192.168.1.20:49374`. Replace the example IP and port with those of your computer.
4. In OpenCode Mobile, scan the QR under **Settings > Connect another computer**, or paste the pairing link. Review the address and confirm **Connect using HTTP**.

The QR expires after five minutes and works once. Scan it inside OpenCode Mobile. Plain HTTP sends traffic unencrypted on home Wi-Fi.

### Private access outside your home with Tailscale

On Android, connect both devices to the same tailnet, then scan a fresh OpenCode pairing QR. While the phone VPN is active, the app prefers a Tailscale address offered by the QR. If the QR points to Wi-Fi, tick **Use a different Wi-Fi or Tailscale IP** and enter the PC's Tailscale IPv4 address (shown by `tailscale ip -4`). Keep the OpenCode port and use the same PC that generated the QR. The app binds direct Tailscale HTTP connections to the VPN, including pairing, chat requests, and attachments. This route does not require Tailscale Serve.

To use a private HTTPS hostname instead, set up Serve:

1. Sign in to [Tailscale on Windows](https://tailscale.com/docs/install/windows) and [Android](https://tailscale.com/docs/install/android) using the same tailnet. Keep Tailscale connected on both.
2. On the PC, run `opencode service status` to get the port. From an Administrator PowerShell on Windows, run `tailscale serve --bg http://127.0.0.1:28894` (replace 28894 if needed). If prompted, enable HTTPS for the tailnet. Use [Tailscale Serve](https://tailscale.com/docs/reference/tailscale-cli/serve); it is available only to permitted tailnet devices.
3. Copy the HTTPS origin printed by Serve. Run `opencode pair --url https://YOUR-PC.YOUR-TAILNET.ts.net` and scan the QR inside OpenCode Mobile. If a QR offers several addresses, paste the pairing URL whose host ends in `.ts.net` so the app saves the private HTTPS address.
4. Leave the PC awake with OpenCode running. With phone Wi-Fi off, open the saved Tailscale connection over mobile data and confirm the chat list and model picker load. The same Tailscale address works on Wi-Fi too.

If an older conversation reports a missing project folder, choose **Change project folder** in the chat or model picker. Enter the full path to an existing project on the PC. OpenCode retains that conversation's history. For a new conversation, the app uses the server default if OpenCode reports the remembered folder no longer exists.

## Android build

Requirements: JDK 17, Android SDK platform 35/build tools, **Gradle 8.11.1**, Android Studio or Android command-line tools. Minimum Android 8.0/API 26.

Open `android/` in Android Studio and select a local Gradle 8.11.1 installation, or run from that directory:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Set `ANDROID_HOME` or create your own ignored `android/local.properties` containing `sdk.dir=â€¦`. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

The checked-in Gradle wrapper pins Gradle 8.11.1 and verifies its distribution checksum. CI uses the same version.

On Windows, from the repository root (default JDK: `C:/Program Files/Java/jdk-17`, SDK: `%LOCALAPPDATA%/Android/Sdk`):

```powershell
./tools/android.ps1
./tools/android.ps1 -Install -Serial <adb-device-serial>
./tools/android-dev.ps1 -Serial <adb-device-serial>
```

The script runs JVM tests, lint and the debug build. `-Install` also installs the APK over USB and opens the app. Enable USB debugging and authorize the PC on the phone first. Pass `-Jdk` or `-Sdk` to override toolchain paths; environment changes apply only for the script duration.

`android-dev.ps1` keeps watching Android sources and Gradle configuration, verifies and reinstalls after changes. Press Ctrl+C to stop. This native app uses USB deployment rather than a web development server.

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
