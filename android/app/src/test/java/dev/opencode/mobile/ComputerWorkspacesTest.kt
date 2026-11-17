package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ComputerWorkspacesTest {
    private val first = Computer("a", "Ubuntu", "http://192.168.1.10:49374", "token-a")
    private val second = Computer("b", "Work PC", "https://pc.example", "token-b")
    @Test fun switchingKeepsDraftsAndAttachmentsOnTheirOwningComputer() {
        val cache = ComputerWorkspaces()
        val image = ChatAttachment("photo", "image/png", "data:image/png;base64,YQ==")
        cache.remember(MobileState(computer = first, session = JSONObject().put("id", "shared-id"), drafts = mapOf("shared-id" to "draft A"), attachments = mapOf("shared-id" to listOf(image))))
        val other = cache.restore(second, listOf(first, second))
        assertNull(other.session)
        assertTrue(other.drafts.isEmpty())
        assertTrue(other.attachments.isEmpty())
        cache.remember(other.copy(drafts = mapOf("shared-id" to "draft B")))
        val restored = cache.restore(first, listOf(first, second))
        assertEquals("shared-id", restored.session!!.getString("id"))
        assertEquals("draft A", restored.drafts["shared-id"])
        assertEquals(image, restored.attachments["shared-id"]!!.single())
        assertEquals("draft B", cache.restore(second, listOf(first, second)).drafts["shared-id"])
    }
    @Test fun switchingRequiresFreshConnectionCheckAndCurrentComputerMetadata() {
        val cache = ComputerWorkspaces()
        cache.remember(MobileState(computer = first, status = "Connected", busy = true, importingFiles = true, modelsLoading = true, error = "old failure"))
        val renamed = first.copy(name = "Renamed Ubuntu")
        val restored = cache.restore(renamed, listOf(renamed, second))
        assertEquals(PeerStatus.CONNECTING.label, restored.status)
        assertFalse(restored.busy)
        assertFalse(restored.importingFiles)
        assertFalse(restored.modelsLoading)
        assertNull(restored.error)
        assertEquals("Renamed Ubuntu", restored.computer!!.name)
        assertEquals(listOf(renamed, second), restored.computers)
    }
    @Test fun searchResultsStayWithTheirComputerAndDoNotRestoreAnOrphanSpinner() {
        val cache = ComputerWorkspaces()
        cache.remember(MobileState(computer = first, search = ConversationSearch(query = "find", results = listOf(JSONObject().put("id", "a")), nextCursor = "page2", loading = true)))
        assertEquals("", cache.restore(second, listOf(first, second)).search.query)
        assertTrue(cache.restore(second, listOf(first, second)).search.results.isEmpty())
        val search = cache.restore(first, listOf(first, second)).search
        assertEquals("find", search.query)
        assertEquals("a", search.results.single().getString("id"))
        assertEquals("page2", search.nextCursor)
        assertFalse(search.loading)
        cache.remove(first.id)
        assertTrue(cache.restore(first, listOf(first)).search.results.isEmpty())
    }
    @Test fun removingComputerAlsoRemovesCachedWorkspace() {
        val cache = ComputerWorkspaces()
        cache.remember(MobileState(computer = first, drafts = mapOf("id" to "draft")))
        cache.remove(first.id)
        assertTrue(cache.restore(first, listOf(first)).drafts.isEmpty())
    }
}
