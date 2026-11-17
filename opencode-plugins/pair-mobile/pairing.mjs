import { execFile } from "node:child_process"
import { promisify } from "node:util"

const execute = promisify(execFile)

const guidance = {
  tailscale: "Connect Tailscale on this PC and make sure the tailscale command is on PATH.",
  service: "Start the OpenCode background service with opencode service start.",
  listener: "OpenCode is not listening on the Tailscale interface. Check its service hostname.",
  pairing: "OpenCode did not generate a QR. Check that opencode pair --url is supported.",
}

export function selectOrigin(address, status) {
  const ip = address.trim()
  const parts = ip.split(".")
  if (
    parts.length !== 4 ||
    parts.some((part) => !/^(0|[1-9]\d{0,2})$/.test(part) || Number(part) > 255) ||
    parts[0] !== "100" || Number(parts[1]) < 64 || Number(parts[1]) > 127
  ) throw new Error(guidance.tailscale)

  let endpoint
  try { endpoint = new URL(status.trim()) } catch { throw new Error(guidance.service) }
  const port = endpoint.port || (endpoint.protocol === "http:" ? "80" : "")
  if (
    endpoint.protocol !== "http:" || !port || endpoint.username || endpoint.password ||
    endpoint.pathname !== "/" || endpoint.search || endpoint.hash
  ) throw new Error(guidance.service)
  if (![ip, "0.0.0.0", "[::]"].includes(endpoint.hostname)) throw new Error(guidance.listener)
  return new URL(`http://${ip}:${port}`).origin
}

export function nativeQr(output, origin) {
  const lines = output.replace(/\x1b\[[0-?]*[ -/]*[@-~]/g, "").split(/\r?\n/)
  const addresses = lines.flatMap((line) => line.match(/https?:\/\/[^\s]+\/auth\/connect\/[^\s]+/g) || [])
  if (addresses.length !== 1) throw new Error(guidance.pairing)
  let link
  try { link = new URL(addresses[0]) } catch { throw new Error(guidance.pairing) }
  if (link.origin !== origin || link.username || link.password || link.search || link.hash ||
    !/^\/auth\/connect\/[A-Za-z0-9_-]+$/.test(link.pathname)
  ) throw new Error(guidance.pairing)

  const first = lines.findIndex((line) => /^ *█{21,81}$/.test(line))
  if (first < 0) throw new Error(guidance.pairing)
  const indent = lines[first].length - lines[first].trimStart().length
  const columns = lines[first].length - indent
  const rows = lines.slice(first, first + Math.ceil(columns / 2)).map((line) => line.slice(indent))
  if (rows.length !== Math.ceil(columns / 2) || rows.some((row) => row.length !== columns || !/^[ █▀▄]+$/.test(row))) {
    throw new Error(guidance.pairing)
  }
  if (columns % 2 === 1) {
    // OpenCode leaves the unused bottom half of its final cell row black.
    if (!/^[█▀]+$/.test(rows.at(-1))) throw new Error(guidance.pairing)
    rows[rows.length - 1] = "█".repeat(columns)
  }

  // The native QR uses white blocks on black. Add a four-module quiet zone.
  const side = "████"
  const edge = "█".repeat(columns + 8)
  return [edge, edge, ...rows.map((row) => side + row + side), edge, edge]
}

async function command(executable, args, hint) {
  try {
    const { stdout } = await execute(executable, args, {
      encoding: "utf8", maxBuffer: 64 * 1024, timeout: 8000,
      windowsHide: true, env: { ...process.env, NO_COLOR: "1" },
    })
    return stdout
  } catch {
    // Child-process errors contain stdout/stderr; never surface a pairing URL from them.
    throw new Error(hint)
  }
}

export async function makePairingQr(run = command) {
  const [address, listener] = await Promise.all([
    run("tailscale", ["ip", "-4"], guidance.tailscale),
    run("opencode", ["service", "status"], guidance.service),
  ])
  const origin = selectOrigin(address, listener)
  const output = await run("opencode", ["pair", "--url", origin], guidance.pairing)
  const rows = nativeQr(output, origin)
  return { origin, rows }
}
