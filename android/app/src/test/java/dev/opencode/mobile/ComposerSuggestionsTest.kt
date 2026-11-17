package dev.opencode.mobile

import org.junit.Assert.*
import org.junit.Test

class ComposerSuggestionsTest {
    @Test fun commandCompletionReplacesWholeTokenAndLeavesArguments() {
        val draft = "  /team:review-draft please"
        val token = composerToken(draft, "  /team:re".length)!!
        assertEquals("team:re", token.query)
        val result = token.replace(draft, "team:review")
        assertEquals("  /team:review please", result.text)
        assertEquals("  /team:review ".length, result.selection.start)
        assertNull(composerToken("see /team", 9))
        assertNull(composerToken("/workspace/prompt", 10))
    }

    @Test fun skillCompletionWorksAtCursorAndDoesNotTreatEmailAsAMention() {
        val draft = "Try @postgrest-old in this project"
        val token = composerToken(draft, "Try @post".length)!!
        val result = token.replace(draft, "postgres-check")
        assertEquals("Try @postgres-check in this project", result.text)
        assertEquals("Try @postgres-check ".length, result.selection.start)
        assertNull(composerToken("you@example", 11))
        assertNull(composerToken("Try @weird:skill", 16))
    }
}
