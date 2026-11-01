package com.protctus.app.engine

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests for [UrlScannerEngine] URL parsing and risk logic.
 *
 * Verifies the public contract of the URL scanner:
 *   - Host extraction normalizes scheme/www/port/path/query/fragment.
 *   - Raw IP addresses are detected.
 *   - Malformed/empty input does not crash (fail-open to SAFE).
 *   - Allowlist short-circuits to SAFE.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UrlScannerEngineTest {

    private lateinit var context: Context
    private lateinit var engine: UrlScannerEngine

    @Before
    fun setUp() {
        context = org.robolectric.Robolectric.applicationContext
        engine = UrlScannerEngine(context)
    }

    @Test
    fun `extractDomain strips scheme www port path query fragment`() {
        // Access via public analyzeUrl is async; instead verify via the
        // IP-pattern + allowlist behavior through analyzeUrl (suspend).
        runBlocking {
            val safe = engine.analyzeUrl("https://www.google.com:443/search?q=x")
            assertEquals(ScanVerdict.SAFE, safe) // google.com in GLOBAL_ALLOWLIST

            val rawIp = engine.analyzeUrl("http://192.168.1.1/admin")
            assertEquals(ScanVerdict.BLOCKED, rawIp) // raw IP → max risk → BLOCKED
        }
    }

    @Test
    fun `malformed empty input fails open to SAFE instead of crashing`() {
        runBlocking {
            assertEquals(ScanVerdict.SAFE, engine.analyzeUrl(""))
            assertEquals(ScanVerdict.SAFE, engine.analyzeUrl("   "))
            assertEquals(ScanVerdict.SAFE, engine.analyzeUrl("not-a-url!!!"))
        }
    }

    @Test
    fun `heuristic risk caps at 0.4f`() {
        // reflectively invoke calculateHeuristicRisk to check the cap
        val method = UrlScannerEngine::class.java.getDeclaredMethod(
            "calculateHeuristicRisk", String::class.java
        )
        method.isAccessible = true
        // Entropy-heavy long domain → high heuristic, but capped at 0.4f
        val score = method.invoke(engine, "a".repeat(200) + ".com") as Float
        assertTrue("Heuristic score must be capped at 0.4f, was $score", score <= 0.4f + 1e-6)
    }

    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
