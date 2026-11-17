package dev.opencode.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProjectsTest {
    @Test fun groupsSessionsByExactDirectoryAndCountsRunningWork() {
        val first = session("first", "C:\\Work\\Alpha")
        val second = session("second", "c:/work/alpha/")
        val nested = session("nested", "C:\\Work\\Alpha\\Draft")
        val projects = Projects(
            directories = listOf("C:\\Work\\Alpha", "C:\\Work\\Empty"),
            sessions = listOf(first, second, nested, session("missing", null)),
        )
        assertEquals(
            listOf(DirectoryProject("C:\\Work\\Alpha", 2, 1),
                DirectoryProject("C:\\Work\\Alpha\\Draft", 1, 0), DirectoryProject("C:\\Work\\Empty", 0, 0)),
            projects.choices(setOf("second", "missing")),
        )
        assertEquals(listOf(first, second), projects.forDirectory("C:/WORK/ALPHA"))
        assertNotEquals(directoryKey("/work/Alpha"), directoryKey("/work/alpha"))
    }

    @Test fun projectNameIsOnePortableFolderSegment() {
        assertEquals("งานใหม่ 2.0", checkedProjectName(" งานใหม่ 2.0 "))
        assertEquals("C:\\OpenCode Projects\\My project", childDirectory("C:\\OpenCode Projects", "My project"))
        assertEquals("/OpenCode Projects", childDirectory("/", PROJECTS_FOLDER))
        listOf("", "..", "one/two", "one\\two", "C:bad", "one?two", "NUL.txt", "COM1",
            "file.", "x".repeat(65)).forEach { assertTrue(it, runCatching { checkedProjectName(it) }.isFailure) }
    }

    private fun session(id: String, directory: String?) = JSONObject().put("id", id)
        .put("location", JSONObject().put("directory", directory ?: JSONObject.NULL))
}
