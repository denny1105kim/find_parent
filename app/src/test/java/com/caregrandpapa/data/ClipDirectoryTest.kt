package com.caregrandpapa.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ClipDirectoryTest {
    @Test fun `different pairs cannot share saved media`() {
        val root = File("/tmp/app-files")
        assertNotEquals(clipDirectory(root, "pair-a"), clipDirectory(root, "pair-b"))
        assertEquals(clipDirectory(root, "pair-a"), clipDirectory(root, "pair-a"))
    }
    @Test fun `untrusted pair id cannot escape media directory`() {
        val root = File("/tmp/app-files")
        val path = clipDirectory(root, "../../other-app")
        assertEquals(File(root, "clips/pairs"), path.parentFile)
        assertTrue(path.name.matches(Regex("[a-f0-9]{64}")))
    }
}
