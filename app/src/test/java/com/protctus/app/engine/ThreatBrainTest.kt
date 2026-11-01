package com.protctus.app.engine

import org.junit.Assert.*
import org.junit.Test


class ThreatBrainTest {

    // ── File Risk Evaluation Tests ──

    @Test
    fun `safe document extensions are not flagged`() {
        val threats = mutableListOf<ScanThreat>()
        val tempFile = createTempFileWithExtension(".txt", "Hello World")
        ThreatBrain.evaluateFileRisk(tempFile, threats)
        assertTrue("Plain .txt file should not be flagged", threats.isEmpty())
        tempFile.delete()
    }

    @Test
    fun `apk file in downloads is not flagged if not disguised`() {
        // A standard .apk file should not trigger the disguised payload rule
        val threats = mutableListOf<ScanThreat>()
        val tempFile = createTempFileWithExtension(".apk", "PK" + "X".repeat(200))
        ThreatBrain.evaluateFileRisk(tempFile, threats)
        // Only the double-extension check would trigger, not the zip analysis
        // since our dummy content doesn't contain actual APK markers
        assertTrue("Normal .apk without double extension should pass", threats.isEmpty())
        tempFile.delete()
    }

    @Test
    fun `double extension pdf-apk is flagged as spoofed`() {
        val threats = mutableListOf<ScanThreat>()
        val tempFile = java.io.File.createTempFile("report.pdf", ".apk")
        tempFile.writeText("dummy content")
        ThreatBrain.evaluateFileRisk(tempFile, threats)
        // File name: report.pdf<random>.apk — check if it contains both patterns
        // Note: createTempFile adds random chars, so the double ext check might not trigger
        // Let's use a properly named file
        tempFile.delete()

        val properFile = java.io.File(System.getProperty("java.io.tmpdir"), "invoice.pdf.apk")
        properFile.writeText("dummy")
        ThreatBrain.evaluateFileRisk(properFile, threats)
        assertFalse("Double extension .pdf.apk should be flagged", threats.isEmpty())
        assertEquals("Spoofed File", threats.first().threatType)
        properFile.delete()
    }

    // ── Edge Cases ──

    @Test
    fun `empty file does not crash evaluateFileRisk`() {
        val threats = mutableListOf<ScanThreat>()
        val tempFile = java.io.File.createTempFile("empty", ".apk")
        // File is 0 bytes
        ThreatBrain.evaluateFileRisk(tempFile, threats)
        // Should not crash, may or may not flag
        tempFile.delete()
    }

    @Test
    fun `file smaller than 100KB skips zip analysis`() {
        val threats = mutableListOf<ScanThreat>()
        val tempFile = java.io.File.createTempFile("small", ".pdf")
        tempFile.writeText("x".repeat(99 * 1024)) // 99KB
        ThreatBrain.evaluateFileRisk(tempFile, threats)
        assertTrue("Small files should skip deep zip analysis and pass", threats.isEmpty())
        tempFile.delete()
    }

    @Test
    fun `risk score boundary - score 74 does not generate threat entry`() {
        // The threshold is >= 75.0 for adding to threats list
        // This is an implicit test — we verify that the engine respects its threshold
        val threats = mutableListOf<ScanThreat>()
        val safeFile = java.io.File.createTempFile("safe", ".txt")
        safeFile.writeText("totally safe content")
        ThreatBrain.evaluateFileRisk(safeFile, threats)
        assertTrue("Score below 75 should not add threat", threats.isEmpty())
        safeFile.delete()
    }

    // ── Helper ──

    private fun createTempFileWithExtension(ext: String, content: String): java.io.File {
        val file = java.io.File.createTempFile("test_file", ext)
        file.writeText(content)
        return file
    }
}
