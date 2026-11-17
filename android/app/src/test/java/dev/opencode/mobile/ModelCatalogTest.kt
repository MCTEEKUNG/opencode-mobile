package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogTest {
    @Test fun fastAliasUsesItsActualModelIdAndPreservesACompatibleEffort() {
        val catalog = JSONObject("""
            {"data":[
                {"id":"gpt-sol","modelID":"sol","providerID":"openai","name":"GPT Sol","enabled":true,
                 "variants":[{"id":"low"},{"id":"high"}]},
                {"id":"gpt-sol-fast","modelID":"sol","providerID":"openai","name":"GPT Sol Fast","enabled":true,
                 "variants":[{"id":"high"}]},
                {"id":"gpt-sol","modelID":"sol","providerID":"opencode","name":"GPT Sol","enabled":true}
            ]}
        """).availableChatModels().modelChoices()
        assertEquals(listOf("openai", "opencode"), catalog.map { it.main.provider })
        val choice = catalog.first()
        val (fast, effort) = choice.toggleFast(choice.main, "high")
        assertEquals("gpt-sol-fast", fast.reference(effort).getString("id"))
        assertEquals("high", effort)
        assertTrue(choice.isFast(fast, effort))
        assertEquals(choice.main, choice.toggleFast(fast, effort).first)
        assertNull(choice.toggleFast(choice.main, "low").second)
        assertFalse(catalog.last().supportsFast(catalog.last().main))
    }

    @Test fun exactFastVariantCanBeToggledWithoutInventingAnAlias() {
        val model = ChatModel("one", "custom", "One", listOf("high", "Fast"))
        val choice = listOf(model).modelChoices().single()
        assertTrue(choice.supportsFast(model))
        assertEquals(model to "Fast", choice.toggleFast(model, "high"))
        assertEquals(model to null, choice.toggleFast(model, "Fast"))
    }

    @Test fun distinctUpstreamsAreNotMergedEvenWithMatchingIdPrefixes() {
        val main = ChatModel("one", "custom", "One", upstreamId = "one")
        val fast = ChatModel("one-fast", "custom", "One Fast", upstreamId = "different")
        val choices = listOf(main, fast).modelChoices()
        assertEquals(listOf(main, fast), choices.map { it.main })
        assertFalse(choices[0].supportsFast(main))
        assertTrue(choices[1].isFast(fast, null))
    }
}
