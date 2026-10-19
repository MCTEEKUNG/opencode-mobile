# Physical-device acceptance checklist

These checks are **not yet executed**. Run against a disposable project before daily use.

## Pairing and security

- [ ] Fresh remote QR pairs on a physical Android device and iPhone.
- [ ] HTTPS pairing token authenticates subsequent API and SSE requests.
- [ ] A browser-consumed or expired link fails without saving an invalid computer.
- [ ] HTTP, localhost, embedded credentials, encoded path separators, query strings and fragments fail before any request.
- [ ] The origin confirmation shows the host and non-default port, but not the code.
- [ ] HTTP redirects are refused and cannot forward credentials.
- [ ] Restarting the app restores computers from secure storage.
- [ ] Forgetting removes the local credential only; user understands the distinction from server revocation.
- [ ] Server password change produces a pair-again state.
- [ ] No token, pairing URL, prompts or file contents appear in app logs/crash reporting.

## Workflow

- [ ] Existing PC session is visible; sending a prompt on mobile updates the same session on PC.
- [ ] New session uses the specified PC working directory, including spaces and Windows paths.
- [ ] Streaming text, tool calls and errors are readable and selectable.
- [ ] Permission shows full action/resources/metadata before allow-once or reject.
- [ ] A double tap cannot submit duplicate prompt/permission writes.
- [ ] Interrupt stops the selected session, not another active session.
- [ ] Changes tab shows the current turn's patches without modifying files.
- [ ] Older message/session pages remain visible after the next event refresh.

## Recovery

- [ ] Toggle airplane mode, switch Wi-Fi/mobile data, and restore connectivity.
- [ ] Resume after screen lock/background; resnapshot missed messages and permissions.
- [ ] Suspend the PC and resume it; distinguish unreachable from expired authorization.
- [ ] Switch PC or session during a slow request; old responses do not replace current state.
- [ ] Simulate a lost prompt response; draft remains and no automatic resend occurs.
- [ ] Android without Play services and iOS with denied camera access still support paste.
- [ ] Keyboard, small screens, large text and screen-reader labels are usable.
