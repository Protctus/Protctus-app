package com.protctus.edge.phishing

import android.content.Context
import com.protctus.app.neuroshield.engine.NeuroShieldEngine
import com.protctus.app.neuroshield.engine.VerdictEngine
import com.protctus.app.neuroshield.engine.VerdictLevel

/**
 * Legacy wrapper for Phishing detection.
 * Delegates all analysis to the new NeuroShieldEngine.
 */
object PhishingScanner {

    private var engine: NeuroShieldEngine? = null
    private val verdictEngine = VerdictEngine()

    @Volatile
    var isModelLoaded = false
        private set

    fun initialize(context: Context) {
        if (engine == null) {
            synchronized(this) {
                if (engine == null) {
                    val newEngine = NeuroShieldEngine(context.applicationContext)
                    newEngine.initialize()
                    engine = newEngine
                    isModelLoaded = newEngine.isReady
                }
            }
        }
    }

    fun analyzeUrl(rawUrl: String): Float {
        val activeEngine = engine
        if (activeEngine == null || !activeEngine.isReady) return 0.0f
        
        return activeEngine.infer(rawUrl)
    }

    fun performDeepAnalysis(url: String): Boolean {
        val score = analyzeUrl(url)
        val verdict = verdictEngine.evaluate(url, score, 0f, engine?.isReady == true)
        return verdict.verdict == VerdictLevel.PHISHING
    }

    fun close() {
        engine?.close()
        engine = null
        isModelLoaded = false
    }
}
