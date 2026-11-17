# Pair a phone from the OpenCode terminal

## Inside OpenCode: `/pair-mobile`

Install OpenCode CLI 2.3 or later, Git, and Python 3 on the computer you want to control. Connect the computer and your phone to the same Tailscale network.

Clone the repository on the computer that runs OpenCode. Then register the plugin.

```powershell
git clone https://github.com/MCTEEKUNG/opencode-mobile.git
cd opencode-mobile
python tools/install-pair-mobile.py
```

On Linux and macOS, use `python3 tools/install-pair-mobile.py`. Keep the checkout in place after installation.

Run `opencode service status` to check the listener. If it listens on localhost, run `opencode service set hostname 0.0.0.0` and then `opencode service start`. Limit inbound access to trusted networks with your firewall.

Open a new OpenCode terminal session and enter **`/pair-mobile`** in its input. The QR appears in a dialog on that screen. Connect Tailscale on both devices, then scan it in OpenCode Mobile under **Settings → Connect another computer → Scan QR code**. Press Esc to close the dialog. If the terminal is too small, enlarge it or zoom out and run the command again.

The plugin uses the **local PC's** OpenCode background service. It checks the Tailscale address and listener, then generates the code locally using `opencode pair --url`. It opens no public tunnel and sends no prompt containing the code. The installer appends one local plugin to `cli.json` and backs up the old file. Keep this checkout at its current path while the plugin is installed. To remove it, remove the entry ending in `opencode-plugins/pair-mobile` from `cli.json` and reopen the TUI.

## From a separate shell

On the PC running OpenCode, open a terminal in this repository. The helper uses Python 3 and invokes the existing [OpenCode pairing command](https://opencode.ai/docs/cli/commands#pair) to draw the QR in your terminal.

**Tailscale:** Connect both devices to the same tailnet and run:

```powershell
python tools/pair.py
```

This reads the PC's Tailscale IPv4 and OpenCode service port. The QR contains the Tailscale address only. It works on mobile data with the VPN connected; it does not require Serve or Funnel.

**Home Wi-Fi:** Join the PC's network, then supply the PC's LAN IP:

```powershell
python tools/pair.py --lan 192.168.1.20
```

Replace `192.168.1.20` with your computer's Wi-Fi address.

In OpenCode Mobile, go to **Settings → Connect another computer → Scan QR code**. Review the shown address and connect. Scan within five minutes; OpenCode's code can be used once. Keep the QR private and scan it in the app instead of opening it in a browser.

This is device pairing. The CLI's conversation-sharing command does not grant app access to the computer. The helper leaves the server and network settings unchanged. The PC must stay awake, and OpenCode must listen on an address the phone can reach. Use `python tools/pair.py --dry-run` to check the selected address without making a code.
