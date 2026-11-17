# Architecture

Each platform owns its UI, state and HTTP implementation. Shared behavior comes from the official V2 OpenAPI contract and acceptance checklist, not a JavaScript bridge. Unknown assistant tool fields are preserved as selectable JSON for visibility.

## Transport

- Pairing: anonymous JSON redemption, followed by an authenticated server-info probe before persistence.
- Requests: HTTPS (or explicitly confirmed private-IPv4 HTTP on Android), standard certificate trust, Basic `opencode:<token>`, no cookies and no redirects.
- Writes: a single UI mutation gate; no application-level retries. Unknown delivery retains the draft and requires manual inspection before resend.
- Events: live-only SSE treated as invalidation, never as a replayable log. Refresh canonical messages and pending approvals after connection and events. Coalesce bursts to avoid an unbounded event queue.
- Lifecycle: close streaming in background, reconnect and snapshot on foreground. Work continues on the PC independent of mobile lifecycle.
- Identity: connection generation and selected-session checks discard stale snapshots. Session-scoped commands capture their destination before awaiting network I/O.

## Secure persistence

Android encrypts the computer list with AES-GCM using a non-exportable Android Keystore key; Android backups are disabled. iOS stores the list as a Keychain item with `WhenUnlockedThisDeviceOnly`. Failure to unlock does not silently delete or overwrite credentials. Pairing links never enter saved-instance state.

## API evolution

V2 endpoints are experimental. The first implementation uses tolerant projections of the published response fields and fails visibly if the response cannot be decoded. `/api/info` is checked during pairing, but there is no claim of compatibility with every V2 release. The read-only contract diagnostic checks the installed server's operation set; CI tests native parsers. Run physical-device acceptance after upgrading the PC server.

## Follow-ups

Structured forms, provider/model/agent selection, rich Markdown, efficient long-history reconciliation, offline persistence, scoped credential revocation and notification delivery require additional product and protocol work. Do not advertise background notifications without an APNs/FCM sender. No extra server or tunnel is installed automatically by either app.
