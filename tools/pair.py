"""Render OpenCode's device pairing QR for a single LAN or Tailscale origin."""

import argparse
import ipaddress
import socket
import subprocess
from urllib.parse import urlsplit


TAILSCALE = ipaddress.IPv4Network("100.64.0.0/10")
LAN = tuple(ipaddress.IPv4Network(network) for network in (
    "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
))


def read_command(argv):
    try:
        result = subprocess.run(argv, capture_output=True, text=True, timeout=8)
    except FileNotFoundError:
        raise ValueError(f"{argv[0]} was not found on PATH.") from None
    except subprocess.TimeoutExpired:
        raise ValueError(f"{argv[0]} did not respond.") from None
    if result.returncode != 0:
        raise ValueError(f"{' '.join(argv)} failed. Verify that {argv[0]} is running.")
    return result.stdout.strip()


def pairing_origin(status, address, *, lan=False):
    try:
        ip = ipaddress.IPv4Address(address)
    except ValueError:
        raise ValueError("Use a single IPv4 address, without a scheme or port.") from None
    if lan:
        if not any(ip in network for network in LAN):
            raise ValueError("--lan requires a private IPv4 address of this PC.")
    elif ip not in TAILSCALE:
        raise ValueError("No Tailscale IPv4 address. Connect this PC to Tailscale first.")

    try:
        listener = urlsplit(status.strip())
        port = listener.port
        if (listener.scheme != "http" or not port or listener.username is not None
                or listener.path not in ("", "/") or listener.query or listener.fragment):
            raise ValueError
    except ValueError:
        raise ValueError("Could not read the OpenCode service address. Run opencode service status.") from None
    if listener.hostname not in ("0.0.0.0", "::", str(ip)):
        raise ValueError(
            "OpenCode is not listening on the selected address. "
            "Run opencode service status and configure the service for LAN or VPN access."
        )
    return f"http://{ip}:{port}"


def require_local_lan_ip(address):
    # A typo must not put a fresh pairing code on a different computer's address.
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        try:
            sock.bind((address, 0))
        except OSError:
            raise ValueError("--lan must be an IPv4 address assigned to this PC.") from None


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Show a device pairing QR for the PC's Tailscale address (default) or LAN IP."
    )
    parser.add_argument("--lan", metavar="IP", help="Use this PC's private LAN IPv4 instead of Tailscale.")
    parser.add_argument("--dry-run", action="store_true", help="Print the origin without generating a pairing code.")
    args = parser.parse_args(argv)
    try:
        address = args.lan if args.lan is not None else read_command(["tailscale", "ip", "-4"])
        origin = pairing_origin(
            read_command(["opencode", "service", "status"]), address, lan=args.lan is not None
        )
        if args.lan is not None:
            require_local_lan_ip(address)
        if args.dry_run:
            print(origin)
            return 0
        return subprocess.run(["opencode", "pair", "--url", origin]).returncode
    except ValueError as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    raise SystemExit(main())
