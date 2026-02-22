package tv.parentapproved.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class CrashHandlerTest {

    private lateinit var crashFile: File
    private lateinit var handler: CrashHandler

    @Before
    fun setUp() {
        crashFile = File.createTempFile("crash_test_", ".txt")
        crashFile.deleteOnExit()
        crashFile.writeText("") // start empty
        handler = CrashHandler(context = null, previousHandler = null)
    }

    @Test
    fun `writeCrashLog writes entry to file`() {
        val entry = "=== CRASH 2026-01-01 12:00:00 ===\nVersion: 0.9.0 (12)\nTest crash\n\n"
        handler.writeCrashLogTo(crashFile, entry)
        val content = crashFile.readText()
        assertTrue(content.contains("Test crash"))
        assertTrue(content.contains("=== CRASH"))
    }

    @Test
    fun `writeCrashLog keeps last 5 crashes`() {
        // Write 7 crashes
        for (i in 1..7) {
            val entry = "=== CRASH 2026-01-0$i 12:00:00 ===\nCrash number $i\n\n"
            handler.writeCrashLogTo(crashFile, entry)
        }
        val content = crashFile.readText()
        // Should have entries 3-7 (last 5)
        assertFalse("Crash 1 should be trimmed", content.contains("Crash number 1"))
        assertFalse("Crash 2 should be trimmed", content.contains("Crash number 2"))
        assertTrue("Crash 3 should remain", content.contains("Crash number 3"))
        assertTrue("Crash 7 should remain", content.contains("Crash number 7"))
    }

    @Test
    fun `writeCrashLog respects 100KB cap`() {
        // Write a very long crash entry
        val bigEntry = "=== CRASH 2026-01-01 12:00:00 ===\n" + "X".repeat(150 * 1024) + "\n\n"
        handler.writeCrashLogTo(crashFile, bigEntry)
        assertTrue("File should be capped at 100KB", crashFile.length() <= CrashHandler.MAX_SIZE)
    }

    @Test
    fun `writeCrashLog handles empty file`() {
        crashFile.delete()
        crashFile.createNewFile()
        val entry = "=== CRASH 2026-01-01 12:00:00 ===\nFirst crash\n\n"
        handler.writeCrashLogTo(crashFile, entry)
        assertTrue(crashFile.readText().contains("First crash"))
    }
}
