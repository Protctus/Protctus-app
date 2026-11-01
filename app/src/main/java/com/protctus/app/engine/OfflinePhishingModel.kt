package com.protctus.app.engine

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

class OfflinePhishingModel(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val MAX_LEN = 200

    init {
        try {
            val assetFileDescriptor = context.assets.openFd("phishing_model.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options()
            options.setNumThreads(2)
            interpreter = Interpreter(mappedByteBuffer, options)
            Log.d("OfflinePhishingModel", "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e("OfflinePhishingModel", "Error reading phishing model", e)
        }
    }

    /**
     * Converts a URL into an integer sequence of ASCII codes up to MAX_LEN.
     * Uses post-padding to fill up the required shape.
     */
    private fun charMapUrl(url: String): IntArray {
        val mappedUrl = IntArray(MAX_LEN) { 0 } // Default padding is 0
        val urlLength = if (url.length > MAX_LEN) MAX_LEN else url.length
        
        for (i in 0 until urlLength) {
            val asciiCode = url[i].code
            mappedUrl[i] = if (asciiCode in 0..127) asciiCode else 0 // Only map basic ASCII, else 0
        }
        return mappedUrl
    }

    /**
     * Executes the local model to determine Phishing Risk.
     * @return Risk Score (0.0 to 1.0), where >= 0.70 is likely malicious.
     * Returns -1.0f on model failure.
     */
    fun evaluateUrl(url: String): Float {
        val tflite = interpreter ?: return -1.0f

        val inputArray = Array(1) { charMapUrl(url) }
        val outputArray = Array(1) { FloatArray(1) }

        return try {
            tflite.run(inputArray, outputArray)
            val score = outputArray[0][0]
            Log.d("OfflinePhishingModel", "TFLite Evaluation Score: $score")
            score
        } catch (e: Exception) {
            Log.e("OfflinePhishingModel", "Inference Failed", e)
            -1.0f
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
