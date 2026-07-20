package com.protctus.app.neuroshield.engine

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * NeuroShield ONNX Inference Engine
 *
 * Loads the LightGBM ONNX model exactly once and provides thread-safe inference.
 * The OrtSession is reused for the lifetime of the application process.
 *
 * Design principles:
 * - Load once, infer many: model is loaded during Application.onCreate via Hilt.
 * - Fail-open: if the model cannot be loaded, the engine enters DEGRADED mode
 *   and all inference calls return 0.0 (safe), letting heuristics decide alone.
 * - Thread-safe: OrtSession.run() is safe to call from multiple threads.
 * - No UI thread work: all inference is called from Dispatchers.Default or IO.
 * - Minimal allocation: reuses FloatBuffer for input tensor construction.
 */
class NeuroShieldEngine(private val context: Context) {

    companion object {
        private const val TAG = "NeuroShieldEngine"
        private const val MODEL_ASSET = "neuroshield_model.onnx"

        /**
         * ONNX model input tensor name.
         * The LightGBM→ONNX converter uses "float_input" by default.
         */
        private const val INPUT_NAME = "float_input"

        /**
         * ONNX model output tensor name for class probabilities.
         * LightGBM ONNX outputs: [0] = predicted labels, [1] = probability maps.
         */
        private const val OUTPUT_PROBABILITIES = "probabilities"
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var mode: EngineMode = EngineMode.INITIALIZING
        private set

    @Volatile
    var modelSizeBytes: Long = 0L
        private set

    private val featureExtractor = LexicalFeatureExtractor()

    /**
     * Initialize the ONNX Runtime environment and load the model.
     * Call exactly once during app startup (from Hilt singleton provider).
     *
     * This method is intentionally synchronous — call it from a background thread.
     */
    fun initialize() {
        if (isReady) return

        try {
            val startTime = System.nanoTime()

            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load model bytes from assets
            val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            modelSizeBytes = modelBytes.size.toLong()

            // Create session with optimizations for mobile
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(1) // Single thread — minimize CPU contention
            }

            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            isReady = true
            mode = EngineMode.FULL

            Log.i(TAG, "ONNX model loaded in ${elapsedMs}ms (${modelSizeBytes} bytes)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model — entering DEGRADED mode", e)
            isReady = false
            mode = EngineMode.DEGRADED
        }
    }

    /**
     * Run inference on a domain hostname.
     *
     * @param domain Raw domain string (e.g., "login-paypal.suspicious.tk")
     * @return Phishing probability in [0.0, 1.0]. Returns 0.0 if engine is not ready.
     */
    fun infer(domain: String): Float {
        val session = ortSession ?: return 0.0f
        val env = ortEnvironment ?: return 0.0f

        return try {
            val features = featureExtractor.extract(domain)
            inferFromFeatures(features, session, env)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed for domain: ${domain.take(64)}", e)
            0.0f
        }
    }

    /**
     * Run inference from pre-extracted features. Used by VerdictEngine
     * when features need to be inspected for indicator generation.
     *
     * @param features FloatArray of exactly [LexicalFeatureExtractor.FEATURE_COUNT] features.
     * @return Phishing probability in [0.0, 1.0].
     */
    fun inferFromFeatures(features: FloatArray): Float {
        val session = ortSession ?: return 0.0f
        val env = ortEnvironment ?: return 0.0f
        return inferFromFeatures(features, session, env)
    }

    private fun inferFromFeatures(features: FloatArray, session: OrtSession, env: OrtEnvironment): Float {
        // Create input tensor: shape [1, FEATURE_COUNT]
        val floatBuffer = FloatBuffer.wrap(features)
        val shape = longArrayOf(1L, features.size.toLong())
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        return try {
            val inputs = mapOf(INPUT_NAME to inputTensor)
            val results = session.run(inputs)

            // LightGBM ONNX output [1] = probability map: List<Map<Long, Float>>
            // Each entry maps class_id → probability. We want class 1 (phishing).
            val probabilities = results.get(OUTPUT_PROBABILITIES)
            if (probabilities.isPresent) {
                val probValue = probabilities.get().value
                extractPhishingProbability(probValue)
            } else {
                // Fallback: try output index 1 directly
                val output = results.get(1)
                if (output.isPresent) {
                    extractPhishingProbability(output.get().value)
                } else {
                    0.0f
                }
            }
        } finally {
            inputTensor.close()
        }
    }

    /**
     * Extract the phishing probability (class 1) from the ONNX output.
     *
     * LightGBM ONNX produces output_label (int64[]) and output_probability
     * (sequence of maps). The probability output format varies:
     * - Sequence<Map<Long, Float>> — standard LightGBM ONNX
     * - Float[][] — some converters
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractPhishingProbability(probValue: Any?): Float {
        return when (probValue) {
            // Standard LightGBM ONNX: List<Map<Long, Float>>
            is List<*> -> {
                val firstEntry = (probValue as? List<Map<Long, Float>>)?.firstOrNull()
                firstEntry?.get(1L) ?: firstEntry?.values?.lastOrNull() ?: 0.0f
            }
            // Array of maps
            is Array<*> -> {
                val firstMap = (probValue as? Array<Map<Long, Float>>)?.firstOrNull()
                firstMap?.get(1L) ?: firstMap?.values?.lastOrNull() ?: 0.0f
            }
            // Direct float array [batch, classes]
            is Array<FloatArray> -> {
                val row = probValue.firstOrNull()
                if (row != null && row.size >= 2) row[1] else row?.lastOrNull() ?: 0.0f
            }
            else -> {
                Log.w(TAG, "Unexpected ONNX output type: ${probValue?.javaClass?.name}")
                0.0f
            }
        }
    }

    /**
     * Get the current engine status snapshot.
     */
    fun getStatus(): EngineStatus {
        return EngineStatus(
            onnxReady = isReady,
            heuristicsReady = true, // Managed externally by PhishingDetector
            vpnActive = false,      // Managed externally by PhishingVpnService
            modelSizeBytes = modelSizeBytes,
            featureCount = LexicalFeatureExtractor.FEATURE_COUNT,
            mode = mode
        )
    }

    /**
     * Release ONNX Runtime resources. Call during Application.onTerminate
     * or when the engine is no longer needed.
     */
    fun close() {
        try {
            ortSession?.close()
            ortSession = null
            ortEnvironment?.close()
            ortEnvironment = null
            isReady = false
            mode = EngineMode.STOPPED
            Log.i(TAG, "ONNX engine closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX engine", e)
        }
    }
}
