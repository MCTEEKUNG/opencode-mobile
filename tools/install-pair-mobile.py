"""Register the local TUI plugin in the OpenCode CLI configuration."""

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time


def open_code_config():
    result = subprocess.run(
        ["opencode", "debug", "paths"], capture_output=True, text=True, timeout=8, check=True
    )
    for line in result.stdout.splitlines():
        if match := re.fullmatch(r"config\s+(.+)", line.strip()):
            return Path(match[1]) / "cli.json"
    raise ValueError("Could not find the OpenCode CLI configuration directory.")


def register(config, plugin):
    if config.is_symlink():
        raise ValueError("Add the plugin to cli.json manually; the installer does not update symlinks.")
    old = config.read_bytes() if config.exists() else None
    doc = json.loads(old.decode("utf-8-sig")) if old is not None else {}
    if not isinstance(doc, dict):
        raise ValueError("The existing cli.json is not a JSON object.")
    plugins = doc.setdefault("plugins", [])
    if not isinstance(plugins, list):
        raise ValueError("The existing cli.json plugins setting is not an array.")
    if any((entry.get("package") if isinstance(entry, dict) else entry) == plugin for entry in plugins):
        return None
    plugins.append(plugin)

    config.parent.mkdir(parents=True, exist_ok=True)
    backup = None
    if old is not None:
        backup = config.with_name(f"{config.name}.before-pair-mobile-{time.time_ns()}.bak")
        with backup.open("xb") as out:
            out.write(old)
    temp = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=config.parent, delete=False) as out:
            temp = Path(out.name)
            json.dump(doc, out, indent=2, ensure_ascii=False)
            out.write("\n")
        if (config.read_bytes() if config.exists() else None) != old:
            raise ValueError("cli.json changed during installation. Rerun the installer.")
        os.replace(temp, config)
    finally:
        if temp is not None and temp.exists():
            temp.unlink()
    return backup


def main():
    plugin = Path(__file__).resolve().parents[1] / "opencode-plugins" / "pair-mobile"
    if not (plugin / "package.json").is_file():
        raise SystemExit("OpenCode Mobile pairing plugin is missing from this checkout.")
    try:
        config = open_code_config()
        backup = register(config, plugin.as_posix())
    except (OSError, subprocess.SubprocessError, ValueError) as exc:
        raise SystemExit(f"Could not register /pair-mobile: {exc}") from None
    print("OpenCode Mobile pairing plugin is registered. Open a new OpenCode TUI and enter /pair-mobile.")
    if backup is not None:
        print(f"Original settings backed up to {backup}")


if __name__ == "__main__":
    main()
