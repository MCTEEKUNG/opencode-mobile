package dev.opencode.mobile

/** In-memory workspace per computer; server history stays on its owning PC. */
class ComputerWorkspaces {
    private val states = mutableMapOf<String, MobileState>()
    fun remember(state: MobileState) {
        state.computer?.let { states[it.id] = state.copy(busy = false, importingFiles = false, composerLoading = false, interrupting = false, status = "Paused", error = null) }
    }
    fun restore(computer: Computer, computers: List<Computer>): MobileState =
        (states[computer.id] ?: MobileState()).copy(computers = computers, computer = computer,
            status = PeerStatus.CONNECTING.label, busy = false, importingFiles = false, modelsLoading = false, modelsError = null, search = (states[computer.id]?.search ?: ConversationSearch()).copy(loading = false))
    fun remove(id: String) { states.remove(id) }
}
