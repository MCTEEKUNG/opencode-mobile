/** @jsxImportSource @opentui/solid */
import { Plugin } from "@opencode/plugin/tui"
import { makePairingQr } from "./pairing.mjs"

export default Plugin.define({
  id: "opencode-mobile.pair",
  setup(context) {
    let generating = false
    context.keymap.layer(() => ({
      mode: "global",
      commands: [{
        id: "opencode-mobile.pair",
        title: "Pair OpenCode Mobile over Tailscale",
        group: "OpenCode Mobile",
        slash: { name: "pair-mobile" },
        palette: true,
        run: async () => {
          if (generating) return
          generating = true
          try {
            if (context.renderer.width < 60 || context.renderer.height < 33) {
              await context.ui.dialog.alert({
                title: "Pair OpenCode Mobile",
                message: "Enlarge this terminal or zoom out, then enter /pair-mobile again.",
              })
              return
            }
            const { origin, rows } = await makePairingQr()
            if (context.renderer.width < rows[0].length + 4 || context.renderer.height < rows.length + 10) {
              await context.ui.dialog.alert({
                title: "Pair OpenCode Mobile",
                message: "Enlarge this terminal or zoom out, then enter /pair-mobile again.",
              })
              return
            }
            context.ui.dialog.show(() => (
              <box flexDirection="column" gap={1} padding={1}>
                <text fg={context.theme.text.base}>Pair OpenCode Mobile</text>
                <text fg={context.theme.text.base}>Scan in the app while Tailscale is connected.</text>
                <box flexDirection="column" width={rows[0].length} backgroundColor="#000000" alignSelf="center">
                  {rows.map((row) => <text fg="#FFFFFF" bg="#000000" wrapMode="none">{row}</text>)}
                </box>
                <text fg={context.theme.text.base}>{origin}</text>
                <text fg={context.theme.text.base}>One scan · expires in 5 min · Esc to close</text>
              </box>
            ))
            context.ui.dialog.set({ size: "xlarge", centered: true })
          } catch (error) {
            // Unexpected parsing/runtime errors can contain input URLs, so display only trusted guidance.
            const message = error instanceof Error && !/https?:\/\//i.test(error.message)
              && /^(Connect Tailscale|Start the OpenCode|OpenCode (is not listening|did not generate))/.test(error.message)
              ? error.message : "Could not display the pairing QR. Check OpenCode and Tailscale, then try again."
            await context.ui.dialog.alert({ title: "Pair OpenCode Mobile", message })
          } finally {
            generating = false
          }
        },
      }],
    }))
  },
})
