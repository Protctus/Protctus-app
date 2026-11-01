package com.protctus.app.neuroshield.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [VerdictEngine] score fusion and threshold classification.
 *
 * Pure JVM tests — no Android APIs. Verifies the documented decision rules:
 *   fused >= 0.65 → PHISHING
 *   fused >= 0.30 → SUSPICIOUS
 *   else          → SAFE (only when evidence is present)
 *   no evidence   → REQUIRES_NETWORK_VERIFICATION
 * with phishing as the positive class.
 */
class VerdictEngineTest {

    private val engine = VerdictEngine()

    @Test
    fun `high fused score classifies as phishing`() {
        val v = engine.evaluate("evil.example.tk", onnxScore = 0.9f, heuristicScore = 0.8f, onnxAvailable = true)
        assertEquals(VerdictLevel.PHISHING, v.verdict)
    }

    @Test
    fun `mid fused score classifies as suspicious`() {
        val v = engine.evaluate("weird.example.com", onnxScore = 0.5f, heuristicScore = 0.2f, onnxAvailable = true)
        assertEquals(VerdictLevel.SUSPICIOUS, v.verdict)
    }

    @Test
    fun `low fused score with evidence classifies as safe`() {
        val v = engine.evaluate("google.com", onnxScore = 0.01f, heuristicScore = 0.05f, onnxAvailable = true)
        assertEquals(VerdictLevel.SAFE, v.verdict)
    }

    @Test
    fun `no evidence at all is insufficient-evidence, not safe`() {
        // ONNX score ~0 and heuristic ~0 → no meaningful signal → insufficient evidence
        val v = engine.evaluate("unknown.example.com", onnxScore = 0.0f, heuristicScore = 0.0f, onnxAvailable = true)
        assertEquals(VerdictLevel.REQUIRES_NETWORK_VERIFICATION, v.verdict)
        assertTrue("Explanation must mention insufficient evidence",
            v.explanation.contains("Insufficient evidence", ignoreCase = true))
        assertTrue("Explanation must not treat missing visibility as safe",
            !v.explanation.contains("legitimate", ignoreCase = true))
    }

    @Test
    fun `onnx unavailable with heuristic signal uses degraded mode`() {
        val v = engine.evaluate("d", onnxScore = 0.0f, heuristicScore = 0.8f, onnxAvailable = false)
        // degraded: 0.8 * 0.90 = 0.72 -> phishing
        assertEquals(VerdictLevel.PHISHING, v.verdict)
    }

    @Test
    fun `onnx unavailable with no heuristic signal is insufficient evidence`() {
        val v = engine.evaluate("d", onnxScore = 0.0f, heuristicScore = 0.0f, onnxAvailable = false)
        assertEquals(VerdictLevel.REQUIRES_NETWORK_VERIFICATION, v.verdict)
    }

    @Test
    fun `threshold boundary at 0.65 is phishing`() {
        // fused = 0.7*0.65 + 0.3*0.0 = 0.455 -> suspicious (below 0.65)
        val v1 = engine.evaluate("d", onnxScore = 0.65f, heuristicScore = 0.0f, onnxAvailable = true)
        assertEquals(VerdictLevel.SUSPICIOUS, v1.verdict)

        // fused = 0.7*0.93 + 0.3*0.0 = 0.651 -> phishing (just above 0.65)
        val v2 = engine.evaluate("d", onnxScore = 0.93f, heuristicScore = 0.0f, onnxAvailable = true)
        assertEquals(VerdictLevel.PHISHING, v2.verdict)
    }

    @Test
    fun `threshold boundary at 0.30 is suspicious`() {
        // fused = 0.7*0.30 + 0.3*0.0 = 0.21 -> safe
        val v1 = engine.evaluate("d", onnxScore = 0.30f, heuristicScore = 0.0f, onnxAvailable = true)
        assertEquals(VerdictLevel.SAFE, v1.verdict)

        // fused = 0.7*0.43 + 0.3*0.0 = 0.301 -> suspicious (just above 0.30)
        val v2 = engine.evaluate("d", onnxScore = 0.43f, heuristicScore = 0.0f, onnxAvailable = true)
        assertEquals(VerdictLevel.SUSPICIOUS, v2.verdict)
    }

    @Test
    fun `verdict contains explanation and indicators`() {
        val v = engine.evaluate("192.168.1.1", onnxScore = 0.9f, heuristicScore = 0.9f, onnxAvailable = true)
        assertFalse("Explanation must be non-empty", v.explanation.isBlank())
        assertTrue("Confidence must be in [0,1]", v.confidence in 0f..1f)
        assertNotNull("Recommended action must be present", v.recommendedAction)
    }

    @Test
    fun `timestamp is set on every verdict`() {
        val v = engine.evaluate("google.com", onnxScore = 0.0f, heuristicScore = 0.0f, onnxAvailable = true)
        assertTrue("Timestamp must be a recent positive epoch-ms", v.timestamp > 0L)
    }
}
