package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConversationHomeTest {
    private fun row(id: String, created: Long, updated: Long) =
        JSONObject().put("id", id).put("time", JSONObject().put("created", created).put("updated", updated))

    @Test fun clearsHistoryEvenIfAnOldChatIsUpdatedAndKeepsNewChats() {
        val old = row("old", 1, 30)
        val current = row("current", 20, 20)
        val newer = row("new", 22, 33)
        val raw = listOf(old, current, newer)
        assertEquals(listOf("new"), HistoryPresentation(clearedThrough = 20).visible(raw).map { it.getString("id") })
        assertEquals(listOf("new", "old", "current"), HistoryPresentation().visible(raw).map { it.getString("id") })
        assertTrue(HistoryPresentation(hidden = true).visible(raw).isEmpty())
        assertEquals(3, raw.size)
    }

    @Test fun clearedHistoryCanSkipAPageOnlyWhenItCannotContainNewChats() {
        val history = HistoryPresentation(clearedThrough = 20)
        assertFalse(history.hasPotentialOlderPage(listOf(row("last", 10, 20))))
        assertTrue(history.hasPotentialOlderPage(listOf(row("last", 10, 21))))
        assertTrue(history.hasPotentialOlderPage(emptyList()))
    }

    @Test fun executionStartAndEndTrackWorkPerConversationAndKind() {
        fun event(type: String, id: String = "s1") =
            sessionActivity("""{"type":"session.$type","data":{"sessionID":"$id"}}""")!!
        val started = event("execution.started").reduce(emptyMap())
        val parallel = event("shell.started").reduce(started)
        val other = event("step.started", "s2").reduce(parallel)
        assertEquals(setOf(WorkKind.RESPONSE, WorkKind.SHELL), other["s1"])
        val finished = event("execution.interrupted").reduce(other)
        assertEquals(setOf(WorkKind.SHELL), finished["s1"])
        assertEquals(setOf(WorkKind.RESPONSE), finished["s2"])
        assertFalse("s1" in event("shell.ended").reduce(finished))
        assertNull(sessionActivity("""{"type":"session.created","data":{"sessionID":"s1"}}"""))
    }
}
