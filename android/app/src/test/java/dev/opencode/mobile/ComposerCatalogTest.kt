package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ComposerCatalogTest {
    @Test fun skillCatalogUsesServerIdAndDoesNotRetainSkillBody() {
        val entries = JSONObject("""{"data":[{"id":"docs-helper","name":"Docs Helper","description":"Help with docs","content":"PRIVATE"}]}""")
            .composerEntries(ComposerCatalogKind.SKILL)
        assertEquals(listOf(ComposerEntry("docs-helper", "Help with docs")), entries)
    }

    @Test fun registeredNamespacedCommandUsesCommandEndpointAndRelativeSkillOffsets() {
        val submission = composerSubmission(" /plugin:run  Hi @docs-helper", emptyList(),
            listOf(ComposerEntry("plugin:run")), listOf(ComposerEntry("docs-helper")))
        assertEquals("command", submission.endpoint)
        assertEquals("plugin:run", submission.body.getString("name"))
        val text = submission.body.getString("text")
        assertEquals("Hi @docs-helper", text)
        val skill = submission.body.getJSONArray("skills").getJSONObject(0)
        assertEquals("docs-helper", skill.getString("id"))
        val mention = skill.getJSONObject("mention")
        assertEquals(mention.getString("text"), text.substring(mention.getInt("start"), mention.getInt("end")))
    }

    @Test fun plainMessagesKeepTextAndAttachOnlyKnownSkills() {
        val text = "📁 @docs-helper and @unknown plus user@example.com"
        val submission = composerSubmission(text, emptyList(), emptyList(), listOf(ComposerEntry("docs-helper")))
        assertEquals("prompt", submission.endpoint)
        assertEquals(text, submission.body.getString("text"))
        assertEquals(1, submission.body.getJSONArray("skills").length())
        val mention = submission.body.getJSONArray("skills").getJSONObject(0).getJSONObject("mention")
        assertEquals("@docs-helper", text.substring(mention.getInt("start"), mention.getInt("end")))
    }

    @Test fun unknownAndLocalOnlyCommandsDoNotFallBackToPromptExecution() {
        for (name in listOf("unknown", "pair-mobile")) {
            assertThrows(IllegalArgumentException::class.java) {
                composerSubmission("/$name", emptyList(), emptyList(), emptyList())
            }
        }
        assertEquals("prompt", composerSubmission("/home/user", emptyList(), emptyList(), emptyList()).endpoint)
    }
}
