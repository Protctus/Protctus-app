package com.protctus.app.neuroshield.engine

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * HistGradientBoosting ONNX inference engine.
 *
 * Loads the HistGradientBoosting ONNX model exported from the training pipeline
 * (`ml/training/models/neuroshield_hgb.onnx`) and provides thread-safe inference.
 * No TFLite or LightGBM runtime dependency is required — uses ONNX Runtime Mobile.
 *
 * Model: HistGradientBoostingClassifier trained on 53 lexical features.
 * Test metrics (held-out): acc=0.9230, rec=0.8447, prec=0.9178, f1=0.8797, fpr=0.0378.
 *
 * Thread-safe: all methods are pure after initialization.
 */
class HistGradientBoostingModel(private val context: Context) {

    companion object {
        private const val TAG = "HistGradientBoostingModel"
        private const val MODEL_ASSET = "neuroshield_hgb.onnx"

        /** ONNX model input tensor name. */
        private const val INPUT_NAME = "float_input"

        /** ONNX model output tensor name for class probabilities. */
        private const val OUTPUT_PROBABILITIES = "probabilities"
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    var modelSizeBytes: Long = 0L
        private set

    private val featureExtractor = LexicalFeatureExtractor()
    private var dynamicFeatureProvider: DynamicFeatureProvider? = null

    /**
     * Initialize the ONNX Runtime environment and load the HGB model.
     * Call exactly once during app startup from a background thread.
     */
    fun initialize() {
        if (isReady) return

        try {
            val startTime = System.nanoTime()

            ortEnvironment = OrtEnvironment.getEnvironment()

            // Initialize dynamic feature provider (loads neuroshield_dynamic_data.json)
            dynamicFeatureProvider = DynamicFeatureProvider(context)

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

            Log.i(TAG, "HGB ONNX model loaded in ${elapsedMs}ms (${modelSizeBytes} bytes)")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load HGB ONNX model — entering degraded mode", e)
            isReady = false
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
            val baseFeatures = featureExtractor.extract(domain)
            val dynamicFeatures = dynamicFeatureProvider?.compute(domain) ?: FloatArray(7)
            val features = FloatArray(LexicalFeatureExtractor.FEATURE_COUNT)
            System.arraycopy(baseFeatures, 0, features, 0, 46)
            System.arraycopy(dynamicFeatures, 0, features, 46, 7)
            inferFromFeatures(features, session, env)
        } catch (e: Exception) {
            Log.e(TAG, "HGB inference failed for domain: ${domain.take(64)}", e)
            0.0f
        }
    }

    /**
     * Run inference from pre-extracted features.
     *
     * @param features FloatArray of exactly [LexicalFeatureExtractor.FEATURE_COUNT] features.
     * @return Phishing probability in [0.0, 1.0]. Returns 0.0 if engine is not ready.
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

            val probabilities = results.get(OUTPUT_PROBABILITIES)
            if (probabilities != null) {
                try {
                    val tensor = probabilities as OnnxTensor
                    val value = tensor.getValue() as? FloatArray
                    val prob = value?.getOrElse(1) { 0f }?.coerceIn(0f, 1f) ?: 0.0f
                    prob
                } catch (e: Exception) {
                    0.0f
                }
            } else {
                0.0f
            }
        } finally {
            inputTensor.close()
        }
    }

    /**
     * Get the current engine status snapshot.
     */
    fun getStatus(): EngineStatus {
        return EngineStatus(
            onnxReady = isReady,
            heuristicsReady = true,
            vpnActive = false,
            modelSizeBytes = modelSizeBytes,
            featureCount = LexicalFeatureExtractor.FEATURE_COUNT,
            mode = if (isReady) EngineMode.FULL else EngineMode.DEGRADED
        )
    }

    /**
     * Release ONNX Runtime resources.
     */
    fun close() {
        try {
            ortSession?.close()
            ortSession = null
            ortEnvironment?.close()
            ortEnvironment = null
            isReady = false
            Log.i(TAG, "HGB ONNX engine closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing HGB engine", e)
        }
    }
}
