package com.protctus.edge.phishing

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.util.Log

object PhishingScanner {

    private val IP_PATTERN = java.util.regex.Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}\$")

    @Volatile
    private var interpreter: Interpreter? = null

    @Volatile
    var isModelLoaded = false
        private set

    fun initialize(context: Context) {
        if (interpreter == null && !isModelLoaded) {
            synchronized(this) {
                if (interpreter == null && !isModelLoaded) {
                    try {
                        val modelBuffer = loadModelFile(context, "phishing_model.tflite")
                        val options = Interpreter.Options().apply {
                            numThreads = 1
                        }
                        interpreter = Interpreter(modelBuffer, options)
                        isModelLoaded = true
                    } catch (e: Exception) {
                        android.util.Log.e("PhishingScanner", "Model init failed", e)
                        isModelLoaded = false
                    }
                }
            }
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun processUrlSequence(rawUrl: String): FloatArray {
        var targetUrl = rawUrl.trim()
        val redirectRegex = Regex("(?i)[?&](?:q|url|redirect_uri)=(https?://[^&]+)")
        var match = redirectRegex.find(targetUrl)
        
        while (match != null) {
            targetUrl = match.groupValues[1]
            try {
                targetUrl = java.net.URLDecoder.decode(targetUrl, "UTF-8")
            } catch (e: Exception) {
            }
            match = redirectRegex.find(targetUrl)
        }

        val schemeEnd = targetUrl.indexOf("://")
        val hostStart = if (schemeEnd != -1) schemeEnd + 3 else 0
        var hostEnd = targetUrl.indexOf('/', hostStart)
        if (hostEnd == -1) hostEnd = targetUrl.length
        
        var hostStr = targetUrl.substring(hostStart, hostEnd)
        val pathStr = if (hostEnd < targetUrl.length) targetUrl.substring(hostEnd) else ""
        
        try {
            hostStr = java.net.IDN.toASCII(hostStr)
        } catch (e: Exception) {
        }
        
        var cleanUrl = (hostStr + pathStr).lowercase()
        
        if (cleanUrl.length > 200) {
            val hLen = hostStr.length
            if (hLen < 200) {
                val rem = 200 - hLen
                cleanUrl = hostStr.lowercase() + cleanUrl.takeLast(rem)
            } else {
                cleanUrl = cleanUrl.take(200)
            }
        }
        
        val CHARS = "abcdefghijklmnopqrstuvwxyz0123456789-,;.!?:'\"/\\|_@#\$%^&*~+-=<>()[]{} "
        val seqArray = FloatArray(200)
        
        for (i in cleanUrl.indices) {
            val char = cleanUrl[i]
            val index = CHARS.indexOf(char)
            if (index != -1) {
                seqArray[i] = (index + 1).toFloat()
            } else {
                seqArray[i] = 0f
            }
        }
        return seqArray
    }

    private fun extractTabularFeatures(url: String): FloatArray {
        val tabArray = FloatArray(13)
        if (url.isEmpty()) return tabArray

        var host = ""
        var scheme = ""
        try {
            var processUrl = url
            if (!processUrl.contains("://")) {
                processUrl = "http://$processUrl"
            }
            val parsedUrl = java.net.URL(processUrl)
            host = parsedUrl.host ?: ""
            scheme = parsedUrl.protocol ?: ""
        } catch (e: Exception) {
        }

        val urlLen = url.length
        val domainLen = host.length
        
        var dotCount = 0
        var hyphenCount = 0
        var atCount = 0
        var specialCharCount = 0
        var digitCount = 0
        var letterCount = 0
        var slashCount = 0

        var i = 0
        while (i < urlLen) {
            val c = url[i]
            if (c == '.') dotCount++
            else if (c == '-') hyphenCount++
            else if (c == '@') atCount++
            else if (c == '/') slashCount++
            else if (c == '?' || c == '=' || c == '%') specialCharCount++
            
            if (c.isDigit()) digitCount++
            else if (c.isLetter()) letterCount++
            
            i++
        }

        var doubleSlashCount = 0
        val schemeIdx = url.indexOf("://")
        val searchStart = if (schemeIdx != -1) schemeIdx + 3 else 0
        var searchPos = searchStart
        while (true) {
            searchPos = url.indexOf("//", searchPos)
            if (searchPos != -1) {
                doubleSlashCount++
                searchPos += 2
            } else {
                break
            }
        }

        var subDomainCount = 0
        if (host.isNotEmpty()) {
            for (c in host) {
                if (c == '.') subDomainCount++
            }
        }

        val isIp = if (host.isNotEmpty() && IP_PATTERN.matcher(host).matches()) 1.0f else 0.0f
        val isHttps = if (scheme.equals("https", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) 1.0f else 0.0f
        val isShortened = if (host.contains("bit.ly") || host.contains("tinyurl.com") || host.contains("t.co") || host.contains("goo.gl") || host.contains("is.gd")) 1.0f else 0.0f
        val digitLetterRatio = if (letterCount > 0) digitCount.toFloat() / letterCount.toFloat() else 0f

        tabArray[0] = urlLen.toFloat()
        tabArray[1] = domainLen.toFloat()
        tabArray[2] = dotCount.toFloat()
        tabArray[3] = hyphenCount.toFloat()
        tabArray[4] = atCount.toFloat()
        tabArray[5] = doubleSlashCount.toFloat()
        tabArray[6] = subDomainCount.toFloat()
        tabArray[7] = isIp
        tabArray[8] = isHttps
        tabArray[9] = specialCharCount.toFloat()
        tabArray[10] = digitLetterRatio
        tabArray[11] = isShortened
        tabArray[12] = slashCount.toFloat()

        return tabArray
    }

    fun analyzeUrl(rawUrl: String): Float {
        if (!isModelLoaded) return 0.0f

        // RBI has been removed from the pipeline.

        val seqArray = processUrlSequence(rawUrl)
        val tabArray = extractTabularFeatures(rawUrl)

        require(seqArray.size == 200)
        require(tabArray.size == 13)

        val activeInterpreter = interpreter ?: return 0.0f

        val inputSeq = arrayOf(seqArray)
        val inputTab = arrayOf(tabArray)
        val outputProb = arrayOf(FloatArray(1))

        val inputs = arrayOf<Any>(inputSeq, inputTab)
        val outputs = mutableMapOf<Int, Any>(0 to outputProb)

        activeInterpreter.runForMultipleInputsOutputs(inputs, outputs)

        return outputProb[0][0]
    }

    private fun extractHost(rawUrl: String): String {
        return try {
            var processUrl = rawUrl
            if (!processUrl.contains("://")) {
                processUrl = "http://$processUrl"
            }
            var host = java.net.URL(processUrl).host ?: ""
            if (host.startsWith("www.")) host = host.substring(4)
            host.lowercase()
        } catch (e: Exception) {
            ""
        }
    }

    private fun calculateShannonEntropy(str: String): Double {
        if (str.isEmpty()) return 0.0
        val freqMap = HashMap<Char, Int>()
        for (char in str) {
            freqMap[char] = (freqMap[char] ?: 0) + 1
        }
        var entropy = 0.0
        val len = str.length.toDouble()
        for (count in freqMap.values) {
            val freq = count / len
            entropy -= freq * (Math.log(freq) / Math.log(2.0))
        }
        return entropy
    }

    private fun detectHomograph(url: String): Boolean {
        // Simple heuristic: if IDN conversion results in xn-- (punycode)
        // or contains mixed script characters that look like Latin
        return url.contains("xn--") || url.matches(".*[а-яА-Я].*".toRegex()) // Cyrillic rough check
    }

    private fun checkKeywordProximity(domain: String): Boolean {
        val targets = listOf("paypal", "bank", "google", "microsoft", "apple", "amazon", "netflix", "login", "secure")
        val suspiciousSuffixes = listOf("-secure", "-login", "-verify", "-update", "support", "auth")

        for (target in targets) {
            if (domain.contains(target)) {
                // High value target found. Check if it's combined suspiciously
                for (suffix in suspiciousSuffixes) {
                    if (domain.contains("$target$suffix") || domain.contains("$suffix$target")) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun performDeepAnalysis(url: String): Boolean {
        var host = url
        try {
            var processUrl = url
            if (!processUrl.contains("://")) {
                processUrl = "http://$processUrl"
            }
            host = java.net.URL(processUrl).host ?: url
        } catch (e: Exception) {
            // fallback
        }

        // 1. Homograph Check
        if (detectHomograph(host)) {
            return true
        }

        // 2. Entropy Check (High randomness > 4.0 is typical for DGAs in short strings)
        val entropy = calculateShannonEntropy(host)
        if (entropy > 4.0) {
            return true
        }

        // 3. Keyword Proximity Check
        if (checkKeywordProximity(host.lowercase())) {
            return true
        }

        return false
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
