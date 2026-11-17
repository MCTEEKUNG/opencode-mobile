import assert from "node:assert/strict"
import test from "node:test"
import { makePairingQr, nativeQr, selectOrigin } from "../opencode-plugins/pair-mobile/pairing.mjs"

const origin = "http://100.64.1.10:28894"
const fakeCode = "example_test-only"
const fake = (url = origin) =>
  `${url}/auth/connect/${fakeCode}\n` + Array(11).fill("  " + "█".repeat(21)).join("\n")

test("only supports a reachable Tailscale IPv4 listener", () => {
  assert.equal(selectOrigin("100.64.1.10\r\n", "http://0.0.0.0:28894"), origin)
  assert.equal(selectOrigin("100.64.1.10", "http://[::]:28894"), origin)
  assert.throws(() => selectOrigin("100.128.0.1", "http://0.0.0.0:28894"))
  assert.throws(() => selectOrigin("100.64.1.10", "http://127.0.0.1:28894"))
  assert.throws(() => selectOrigin("100.64.1.10", "http://user:password@0.0.0.0:28894"))
})

test("keeps the QR in a white quiet zone, without returning the raw code", () => {
  const rows = nativeQr(fake(), origin)
  assert.equal(rows.length, 15)
  assert.ok(rows.every((row) => row.length === 29))
  assert.ok(!rows.join("").includes(fakeCode))
})

test("rejects local addresses, truncated output and extra pairing links", () => {
  assert.throws(() => nativeQr(fake("http://192.168.1.10:28894"), origin))
  assert.throws(() => nativeQr(fake().split("\n").slice(0, 3).join("\n"), origin))
  assert.throws(() => nativeQr(fake() + "\n" + fake(), origin))
})

test("makes one pairing request with the VPN origin and no remote flag", async () => {
  const seen = []
  const result = await makePairingQr(async (file, args) => {
    seen.push([file, ...args])
    if (file === "tailscale") return "100.64.1.10"
    if (args[0] === "service") return "http://0.0.0.0:28894"
    return fake()
  })
  assert.deepEqual(seen, [
    ["tailscale", "ip", "-4"], ["opencode", "service", "status"],
    ["opencode", "pair", "--url", origin],
  ])
  assert.deepEqual(Object.keys(result), ["origin", "rows"])
})
