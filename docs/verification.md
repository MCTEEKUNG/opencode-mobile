# Verification record

## Windows / physical Android verification — 2026-10-19

- Chat/model update: 25 JVM tests passed; lint and APK build passed. The model picker on SM-S928B loaded enabled models from the paired server. Mock transport tests verify the deep-object location query and POST model reference/authentication. Verification sent no test prompts and did not switch a live session model.

- LAN update: all 12 JVM tests passed, including localhost replacement, port/code preservation, private IPv4 validation and HTTP redemption/authenticated session loading against a mock server. `lintDebug` and `assembleDebug` passed; updated APK installed successfully on SM-S928B. Pairing with the user's other computer still requires their LAN server to be reachable.
- JDK 17, Gradle wrapper 8.11.1, Android SDK Platform 35/build tools 35.0.0.
- `./tools/android.ps1 -Install -Serial <device-serial>`: build succeeded, all 7 JVM tests passed, `lintDebug` completed, debug APK installed successfully.
- Physical Samsung SM-S928B: `am start -W` returned `Status: ok` for `dev.opencode.mobile/.MainActivity`.
- Fixed JVM test isolation: each HTTPS mock transport now owns its dispatcher and connection pool, so teardown cannot shut down another test's shared executor.
- Pairing, prompt exchange, QR scanning and the remaining physical-device acceptance checklist still require end-to-end verification.

## Earlier Linux verification

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

- Android checks were unavailable during the earlier Linux run; see the Windows results above.
- iOS compilation/XCTest: no macOS/Xcode toolchain.
- GitHub Actions: workflow added, not run from this local directory.
- QR redemption on actual devices, remote transport, camera and physical-device acceptance.

Native tests are supplied in `android/app/src/test/` and `ios/Tests/`. Their presence must not be interpreted as a passing result. See `acceptance.md` for the release gate.

## Android attachment and compact chat header update

- 35 JVM tests passed, including file payload serialization, image-only history, tool attachments, bounded reads and credential isolation for attachment URLs. Lint and APK build passed.
- Physical SM-S928B: selected a generated PNG through the Android document picker, verified its decoded inline preview, and opened the full-screen image viewer. Removed the sample draft attachment afterward. Header navigation/actions share one row.
- Prompt attachment POST was checked with MockWebServer; verification did not send a live AI prompt or upload personal files.

## Live connection monitoring

- 41 JVM tests passed; lint/build passed. Tests cover independent heartbeat deadlines, stream loss, silent outages, recovery and auth failures.
- Physical SM-S928B: Wi-Fi temporarily disabled, Connection lost appeared; Wi-Fi restored in a finally block, the app returned to Connected automatically and the lost banner disappeared. No prompts or sessions were created during the check.
- Monitoring runs while the app is in the foreground; returning to the app verifies the connection again.

## Computer switcher

- 44 JVM tests passed; lint and APK build passed. Workspace tests verify draft/attachment isolation between computers, restoring the current conversation with fresh connectivity checks, and removing cached workspaces.
- Physical SM-S928B: verified the top computer selector opens the saved-computer sheet with current status and the connect-another-computer action. This device currently has one saved computer; cross-computer state isolation was checked in JVM tests.


## Conversation search

- 46 JVM tests passed; lint and APK build passed, installed on the physical SM-S928B. New coverage checks encoded Unicode/reserved characters and cursors over authenticated HTTP, plus search isolation/restoration between computers.
- Verified against the local OpenCode service that title queries return matching history and an unmatched query returns no rows.
- Physical device: verified an unmatched query shows the empty state, an existing-title search shows matching conversations, and search results open the chat. Returning preserves the query; Clear restores the normal list. No prompts or new conversations were submitted.
- Search runs on the selected PC after a 300 ms typing pause. Replacing the query or switching PCs cancels the previous search; SSE snapshots refresh matching results without replacing them with the unfiltered list. iOS search is not included.


## Settings screen

- 46 JVM tests passed; lint and APK build passed, installed on physical SM-S928B.
- Verified Settings opens from the home gear and the computer switcher. Light/Dark changes apply immediately; Light survived force-stop/relaunch, then the original Dark choice was restored.
- Verified rename, remove-confirmation and pairing dialogs open from Settings and cancel safely. No computer was removed or re-paired during testing. The quick-switch sheet no longer duplicates management actions.
- Android back returns to Conversations while the existing PC connection remains active. Settings preserves the selected conversation and in-memory search/draft state. iOS is unchanged.


## Follow system appearance

- 50 JVM tests passed; lint and APK build passed, installed on physical SM-S928B. New tests cover dynamic system appearance, explicit overrides, legacy preference migration and round-trip saved choices.
- Verified the existing Dark choice survived upgrade. Selected Follow system, temporarily switched Android night mode off/on, and captured light/dark app backgrounds without manually relaunching the app. The original system night mode was restored in a finally block.
- Force-stop/relaunch preserved Follow system. The selected theme preference is now a three-way value; choosing a new mode removes the old boolean override. New installs default to Follow system; existing explicit Light/Dark choices remain intact until changed.


## Theme readability review

- 55 JVM tests passed; lint and APK build passed, installed on physical SM-S928B. Contrast regressions cover normal text at 4.5:1, field outlines at 3:1, secondary labels over glass, status text, code/selection colors and neutral component roles in both palettes.
- Reviewed home, chat, Settings, computer switcher, pairing, model/effort selection, attachment menus, approvals and changes. Captured native Light/Dark screens using Follow system and inspected messages, inline code, input text, placeholders, cursors, icon states and dialog labels. No live prompt or model change was submitted; a temporary unsent test draft was cleared.
- Text buttons and code inside message bubbles now inherit the bubble foreground. Composer text/cursor/placeholder colors are explicit. Connected uses a darker green in Light mode and a lighter green in Dark mode. Dialogs, menus, chips and surface variants use explicit neutral roles rather than the default purple palette.
- Fixed chat window panning while the keyboard opens: Compose owns IME insets on Android 11+, with adjustResize retained for older Android. After a fresh launch, inspected the header and composer above the keyboard in Light and Dark. The older-Android fallback was reviewed but not tested on a second physical OS version.
- Follow system remains selected, and the original phone night-mode setting was restored after testing. Image-viewer controls retain white content on its intentional black viewing surface.


## OpenCode 2.3 project-folder recovery and LAN access

- Reproduced `LocationNotFoundError` for historical sessions with missing folders; the same API returned models for existing folders. The list is location-scoped, so an online server can still reject the project.
- Verified a temporary session could be moved to an existing folder with the same session ID. The server can accept a missing location when creating a session, so the Android client validates the remembered folder before creating a new chat.
- Installed the updated APK on SM-S928B over USB after 59 passing JVM tests, lint, and build. An authenticated request sent from the phone over Wi-Fi loaded server info, the chat list, and models; the phone also created a temporary session, set its model, and submitted a prompt with generation disabled, then removed the temporary session.
- During the verification run Windows Tailscale reported NoState and did not have a Serve endpoint; cellular access was not verified.
- Follow-up on the physical Android UI: reproduced the missing project using a temporary chat, moved it via Change project folder, reopened the model picker, selected a model and verified the server saved it on the same session. Deleted the temporary chat. A missing project on the permission list no longer prevents reading chat history.

- Tailscale follow-up: Windows was Running and pinged the online Android peer. With Android Wi-Fi disabled and no WLAN route, authenticated requests to the PC's Tailscale IPv4 returned HTTP 200 for server info, session list and model list. Restored Wi-Fi afterward. This tested the underlying VPN route; the app's HTTPS pairing through Serve requires tailnet HTTPS to be enabled.

## Direct Tailscale pairing on Android

- 61 JVM tests passed, including QR address preference and preserving the port/code when replacing the LAN address. Lint and APK build passed.
- Installed on SM-S928B and paired the Laptop-Tailscale profile directly through the PC’s Tailscale IPv4 while Wi-Fi was off. The app authenticated and reported Connected; it opened a temporary chat and displayed enabled models. Removed the test chat afterward and left Wi-Fi off.
- Direct Tailscale HTTP sockets are bound to the active Android VPN with a route to the destination. Tailscale Serve was not needed for this test.

## Conversation home, model groups and CLI pairing

- 67 Android JVM tests, lint and APK build passed; installed on SM-S928B. Seven CLI helper tests passed. Validated that the native CLI emits a single Tailscale pairing address; dry-run also verified the LAN address.
- On the phone with Wi-Fi off and the existing Tailscale connection, verified hide/show, clearing and preserving the empty state across restart, new chats appearing after clear, and restoring existing history. The clear action only filters this app's home list.
- Verified an OpenCode shell start/finish on a temporary chat made the robot appear and disappear. Work indicators follow live events received while connected; they do not guess that a pre-existing task is active before the app sees an event.
- The model picker displayed OpenAI as a group. Tapping the lightning toggle and applying saved the actual `gpt-6.1-sol-fast` ID on the temporary session; the composer displayed the lightning icon. No AI prompt was sent. Deleted the temporary session afterward.

## CLI QR, composer catalog and live terminal

- Registered the local v2.3 TUI plugin and kept a backup of the original CLI settings. Verified a fresh Windows TUI accepted /pair-mobile and its rendered QR decoded to the Tailscale address only. The new command has 4 Node tests; the previous pairing helper has 7 Python tests. No shared-service restart or public tunnel.
- Installed the final APK via USB after 79 JVM tests, lint and build. With Wi-Fi off, verified the idle composer has Send only, the / list shows PC commands, the @ list shows a PC skill, and that a running shell appears in the separate Live terminal tab and opens its output. No AI generation was started in these checks.
- Verified OpenCode accepted a structured skill mention in a temporary session with generation disabled, and returned its documented idle interrupt result. The stop UI uses session execution events; no live model response was interrupted in this run. Temporary sessions were deleted.
