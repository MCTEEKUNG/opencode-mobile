package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TerminalDataTest {
    @Test fun mergesToolEventsAndHistoryWithoutLosingCommandOrOutput() {
        val live = listOf(
            terminalEvent("""{"type":"session.tool.input.started","created":100,"data":{"sessionID":"ses_actual","assistantMessageID":"msg_agent","id":"provider:call.1","name":"shell"}}""")!!,
            terminalEvent("""{"type":"session.tool.called","created":110,"data":{"sessionID":"ses_actual","assistantMessageID":"msg_agent","id":"provider:call.1","input":{"command":"echo ready"}}}""")!!,
            terminalEvent("""{"type":"session.tool.progress","created":120,"data":{"sessionID":"ses_actual","assistantMessageID":"msg_agent","id":"provider:call.1","metadata":{"output":"working"}}}""")!!,
            terminalEvent("""{"type":"session.tool.success","created":130,"data":{"sessionID":"ses_actual","assistantMessageID":"msg_agent","id":"provider:call.1","content":[{"type":"text","text":"done"},{"type":"file","uri":"https://example.com/output"}]}}""")!!,
        )
        val sameMessage = JSONObject("""{"id":"msg_agent","content":[{"type":"tool","id":"provider:call.1","name":"shell","state":{"status":"running","input":{"command":"echo ready"}}}]}""")
        val assessment = terminalRecords("ses_actual", listOf(sameMessage), live).single()
        assertEquals("shell", assessment.title)
        assertEquals(TerminalPhase.COMPLETE, assessment.phase)
        assertEquals("echo ready", assessment.command)
        assertEquals("done", assessment.output)
        assertEquals(100L, assessment.created)
        assertTrue(terminalRecords("ses_other", emptyList(), live).isEmpty())
    }

    @Test fun usesRealShellHistoryShapeAndScopesReadsToEncodedDirectory() {
        val shell = JSONObject("""{"type":"shell","id":"msg_shell","shellID":"sh_first","status":"exited","exit":2,"command":"echo hello","output":{"output":"failed","truncated":true}}""")
        val record = terminalRecords("ses_actual", listOf(shell), emptyList()).single()
        assertEquals(TerminalPhase.FAILED, record.phase)
        assertEquals("failed", record.output)
        assertTrue(record.note.contains("partial"))
        val path = shellOutputPath(record, "C:/My project&test", 72)!!
        assertEquals("/api/shell/sh_first/output?cursor=72&limit=8192&location%5Bdirectory%5D=C%3A%2FMy+project%26test", path)
        assertNull(shellOutputPath(record.copy(shellId = "bad/path"), "/", 0))
        assertNull(terminalListPath("sess?sessionID=other"))
    }

    @Test fun interruptionFinishesStaleToolsAndIgnoresPrompts() {
        val began = terminalEvent("""{"id":"evt_started","type":"session.execution.started","created":100,"data":{"sessionID":"ses_actual"}}""")!!
        val work = terminalEvent("""{"type":"session.tool.input.started","created":120,"data":{"sessionID":"ses_actual","assistantMessageID":"msg_agent","id":"call_44","name":"shell"}}""")!!
        val stopped = terminalEvent("""{"id":"evt_stopped","type":"session.execution.interrupted","created":130,"data":{"sessionID":"ses_actual"}}""")!!
        assertEquals(setOf(TerminalPhase.STOPPED), terminalRecords("ses_actual", emptyList(), listOf(began, work, stopped)).map { it.phase }.toSet())
        assertNull(terminalEvent("""{"type":"session.text.delta","data":{"sessionID":"ses_actual","text":"do not copy this"}}"""))
    }

    @Test fun removesControlsAndLimitsRetainedOutput() {
        assertEquals("ready\ndone", boundedTerminalOutput("\u001b[32mready\u001b[0m\r\n\u001b]0;title\u0007done"))
        assertEquals(24_000, boundedTerminalOutput("x".repeat(50_000)).length)
    }

    @Test fun supportsReadOnlyPtyAndBackgroundJobSnapshotsSeparately() {
        val ptys = JSONObject("""{"data":[{"id":"pty_owned","sessionID":"ses_actual","status":"running","title":"Terminal"},{"id":"pty_other","sessionID":"ses_other","status":"running"}]}""").persistentTerminals("ses_actual")
        assertEquals(listOf("pty_owned"), ptys.map { it.ptyId })
        assertEquals("/api/experimental/persistent-pty/pty_owned/snapshot", terminalSnapshotPath(ptys.single()))
        val jobs = JSONObject("""{"data":[{"id":"task-1","kind":"shell","status":"running","shellID":"shell_b","command":"echo hi"},{"id":"task-2","kind":"subagent","status":"completed"}]}""").backgroundJobs()
        assertEquals(listOf("Background shell", "Subagent"), jobs.map { it.title })
        assertNull(shellOutputPath(jobs.first(), "/wrong-child-directory", 0))
    }
}
