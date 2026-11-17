package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AttachmentTest {
    private val computer = Computer("pc", "PC", "http://192.168.1.20:49374", "test-secret")
    @Test fun buildsDataUriUploadAndSupportsAttachmentOnlyMessages() {
        val file = ChatAttachment.inline("notes.txt", "text/plain", "hello".toByteArray())
        val body = promptBody("", listOf(file))
        assertEquals("", body.getString("text"))
        val uploaded = body.getJSONArray("files").getJSONObject(0)
        assertEquals("data:text/plain;base64,aGVsbG8=", uploaded.getString("uri"))
        assertEquals("notes.txt", uploaded.getString("name"))
        assertFalse(uploaded.has("data"))
        assertFalse(promptBody("hello", emptyList()).has("files"))
    }
    @Test fun rendersChatImagesAndKeepsToolGeneratedImagesWithTheirOutput() {
        val user = JSONObject("""{"id":"u","type":"user","text":"[Image1]","files":[{"data":"aGVsbG8=","mime":"image/png","name":"photo.png","source":{"type":"inline"}}]}""")
        assertTrue(user.chatVisible())
        assertTrue(user.chatAttachments().single().image)
        assertEquals("data:image/png;base64,aGVsbG8=", user.chatAttachments().single().uri)
        val tool = JSONObject("""{"id":"msg_tool","type":"assistant","content":[{"type":"tool","id":"call_file","name":"read","state":{"status":"completed","content":[{"type":"file","uri":"file:///project/photo.png","mime":"image/png"}]}}]}""")
        assertFalse(tool.chatVisible())
        assertEquals("file:///project/photo.png", terminalRecords("ses_one", listOf(tool), emptyList()).single().files.single().uri)
        val onlyFile = JSONObject("""{"type":"user","text":"","files":[{"data":"aA==","mime":"text/plain"}]}""")
        assertTrue(onlyFile.chatVisible())
    }
    @Test fun sendsCredentialsOnlyToExactPairedServer() {
        val local = attachmentRequest(ChatAttachment("a", "image/png", "http://192.168.1.20:49374/image.png"), computer, "/project")
        assertNotNull(local.header("Authorization"))
        val external = attachmentRequest(ChatAttachment("a", "image/png", "https://example.com/image.png"), computer, "/project")
        assertNull(external.header("Authorization"))
        assertThrows(IllegalArgumentException::class.java) { attachmentRequest(ChatAttachment("a", "image/png", "http://example.com/image.png"), computer, "/project") }
    }
    @Test fun mapsComputerFilesToAuthenticatedFilesystemReadAndRejectsTraversal() {
        val file = attachmentRequest(ChatAttachment("photo.png", "image/png", "file:///project/images/photo.png"), computer, "/project")
        assertEquals("/api/fs/read/images/photo.png", file.url.encodedPath)
        assertEquals("/project", file.url.queryParameter("location[directory]"))
        assertNotNull(file.header("Authorization"))
        assertThrows(IllegalArgumentException::class.java) { attachmentRequest(ChatAttachment("a", "text/plain", "file:///etc/passwd"), computer, "/project") }
        assertThrows(IllegalArgumentException::class.java) { attachmentRequest(ChatAttachment("a", "text/plain", "../secret"), computer, "/project") }
    }
    @Test fun limitsReadBeforeAllocatingUnboundedAttachment() {
        assertArrayEquals("small".toByteArray(), readAttachmentBytes("small".byteInputStream()))
        val large = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int { java.util.Arrays.fill(buffer, offset, offset + length, 0.toByte()); return length }
        }
        assertThrows(IllegalArgumentException::class.java) { readAttachmentBytes(large) }
    }
}
