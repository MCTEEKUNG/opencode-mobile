package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatDataTest {
    @Test fun parsesSupportedEffortsAndBuildsModelVariantReference() {
        val models = JSONObject("""{"data":[{"id":"m1","providerID":"p1","enabled":true,"variants":[{"id":"low"},{"id":"high"},{"id":"high"},{"id":""}]}]}""").availableChatModels()
        val model = models.single()
        assertEquals(listOf("low", "high"), model.variants)
        assertEquals("high", model.reference("high").getString("variant"))
        assertFalse(model.reference().has("variant"))
        assertThrows(IllegalArgumentException::class.java) { model.reference("unsupported") }
    }
    @Test fun newChatUsesServerDefaultOrAnAbsoluteSavedDirectory() {
        assertEquals(0, newSessionBody(null).length())
        assertEquals(0, newSessionBody(" ").length())
        assertEquals("/home/user/project", newSessionBody("/home/user/project").getJSONObject("location").getString("directory"))
        assertEquals("C:/Projects/app", newSessionBody("C:/Projects/app").getJSONObject("location").getString("directory"))
        assertThrows(IllegalArgumentException::class.java) { newSessionBody("relative-path") }
    }
    @Test fun listsOnlyEnabledModelsAndUsesProviderAndReferenceId() {
        val payload = JSONObject("""{"data":[{"id":"r1","modelID":"upstream-1","providerID":"p1","name":"Model One","enabled":true},{"id":"r2","providerID":"p1","enabled":false},{"id":"r1","providerID":"p1","enabled":true},{"id":"r1","providerID":"p2","enabled":true}]}""")
        val models = payload.availableChatModels()
        assertEquals(2, models.size)
        assertEquals("Model One", models[0].name)
        assertEquals("r1", models[0].reference().getString("id"))
        assertEquals("p1", models[0].reference().getString("providerID"))
        assertFalse(models[0].reference().has("modelID"))
    }
    @Test fun keepsMixedAssistantRepliesInChatAndSendsToolOutputToTerminal() {
        val message = JSONObject("""{"id":"a1","type":"assistant","content":[{"type":"reasoning","text":"reasoning detail"},{"type":"text","text":"Hello there"},{"type":"tool","id":"call_1","name":"shell","state":{"status":"completed","input":{"command":"echo test"},"output":"done"}},{"type":"text","text":"Finished"}]}""")
        assertTrue(message.chatVisible())
        assertEquals("Hello there\n\nFinished", message.chatProse())
        assertEquals("reasoning detail", message.chatReasoning())
        val record = terminalRecords("ses_one", listOf(message), emptyList()).single()
        assertEquals("echo test", record.command)
        assertEquals("done", record.output)
        assertTrue(record.isProcess)
    }
    @Test fun shellAndToolOnlyHistoryNeverCreatesAnAssistantBubble() {
        val shell = JSONObject("""{"type":"shell","id":"msg_shell","command":"echo test","output":"done"}""")
        val tool = JSONObject("""{"type":"assistant","id":"msg_tools","error":null,"content":[{"type":"reasoning","text":"planning"},{"type":"tool","id":"call_1","name":"shell","state":{"status":"running"}}]}""")
        assertFalse(shell.chatVisible())
        assertFalse(tool.chatVisible())
        assertEquals("", shell.chatProse())
        val records = terminalRecords("ses_one", listOf(shell, tool), emptyList())
        assertEquals(2, records.size)
        assertEquals("done", records.single { it.kind == TerminalKind.SHELL }.output)
        assertTrue(records.single { it.kind == TerminalKind.TOOL }.phase.active)
    }
    @Test fun hidesInternalIdleAndModelEventsInsteadOfRenderingJson() {
        assertFalse(JSONObject("""{"type":"idle","outcome":"succeeded"}""").chatVisible())
        assertFalse(JSONObject("""{"type":"model-selected","model":{"id":"x"}}""").chatVisible())
        assertTrue(JSONObject("""{"type":"idle","outcome":"failed"}""").chatVisible())
        assertTrue(JSONObject("""{"type":"user","text":"hello"}""").chatVisible())
    }
    @Test fun reportsTokenUsageAndGenerationSpeedOfAFinishedMessage() {
        val message = JSONObject("""{"type":"assistant","time":{"created":1000,"firstToken":2000,"streamed":5000,"completed":6000},"tokens":{"input":1184,"output":150,"reasoning":0,"cache":{"read":118016,"write":0}}}""")
        val usage = message.chatUsage()!!
        assertEquals(119350, usage.tokens)
        assertEquals(50.0, usage.tokensPerSecond, 0.001)
        assertNull(JSONObject("""{"type":"assistant","time":{"created":1000},"tokens":{"output":150}}""").chatUsage())
        assertNull(JSONObject("""{"type":"user","text":"hello"}""").chatUsage())
    }
    @Test fun formatsTokenCountsAndSpeedForReading() {
        assertEquals("999", formatTokenCount(999))
        assertEquals("1k", formatTokenCount(1000))
        assertEquals("12.3k", formatTokenCount(12300))
        assertEquals("1.5M", formatTokenCount(1500000))
        assertEquals("50 tok/s", formatTokensPerSecond(50.0))
        assertEquals("7.5 tok/s", formatTokensPerSecond(7.5))
    }
}
