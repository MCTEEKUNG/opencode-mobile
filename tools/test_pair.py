import unittest
from unittest.mock import patch

from pair import main, pairing_origin


class PairingTests(unittest.TestCase):
    def test_tailnet_origin_preserves_actual_service_port(self):
        self.assertEqual(pairing_origin("http://0.0.0.0:28894", "100.64.1.10"),
                         "http://100.64.1.10:28894")

    def test_lan_origin_uses_explicit_address(self):
        self.assertEqual(pairing_origin("http://192.168.1.7:49374", "192.168.1.7", lan=True),
                         "http://192.168.1.7:49374")

    def test_rejects_loopback_and_other_listener(self):
        for status in ("http://127.0.0.1:28894", "http://192.168.1.7:28894"):
            with self.subTest(status=status), self.assertRaisesRegex(ValueError, "not listening"):
                pairing_origin(status, "100.64.1.10")

    def test_rejects_public_and_wrong_route_addresses(self):
        for address, lan in (("8.8.8.8", True), ("192.168.1.7", False), ("100.128.0.1", False)):
            with self.subTest(address=address, lan=lan), self.assertRaises(ValueError):
                pairing_origin("http://0.0.0.0:28894", address, lan=lan)

    @patch("pair.subprocess.run")
    @patch("pair.read_command", side_effect=["100.64.1.10", "http://0.0.0.0:28894"])
    def test_passes_one_vpn_origin_to_native_qr(self, read_command, run):
        run.return_value.returncode = 0
        self.assertEqual(main([]), 0)
        run.assert_called_once_with(["opencode", "pair", "--url", "http://100.64.1.10:28894"])

    @patch("pair.subprocess.run")
    @patch("pair.read_command", return_value="http://0.0.0.0:28894")
    @patch("pair.require_local_lan_ip")
    @patch("builtins.print")
    def test_lan_dry_run_does_not_mint_a_pairing_code(self, print_value, require_local, read_command, run):
        self.assertEqual(main(["--lan", "192.168.1.7", "--dry-run"]), 0)
        read_command.assert_called_once_with(["opencode", "service", "status"])
        require_local.assert_called_once_with("192.168.1.7")
        print_value.assert_called_once_with("http://192.168.1.7:28894")
        run.assert_not_called()

    @patch("pair.subprocess.run")
    @patch("pair.read_command", return_value="http://0.0.0.0:28894")
    @patch("pair.socket.socket")
    @patch("sys.stderr")
    def test_lan_does_not_issue_code_for_address_on_another_pc(self, stderr, socket, read_command, run):
        socket.return_value.__enter__.return_value.bind.side_effect = OSError
        with self.assertRaises(SystemExit) as result:
            main(["--lan", "192.168.1.7"])
        self.assertEqual(result.exception.code, 2)
        run.assert_not_called()
