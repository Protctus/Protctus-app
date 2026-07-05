package com.protctus.edge.phishing

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.log2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

/**
 * Protctus Dynamic Multi-Layer Phishing Detection Engine v2
 *
 * Hybrid Architecture: 95% offline, server enrichment for high-suspicion domains.
 *
 * Layer 0: User Whitelist            → ~0μs (HashSet)
 * Layer 1: Tranco Whitelist          → ~1μs (ConcurrentHashSet)
 * Layer 2: Raw IP Guard              → ~1μs (Regex)
 * Layer 3: Dynamic Threat Analysis   → ~0.3ms
 *   3a: BK-Tree Brand Proximity      (top 5000 domains, fuzzy match ≤2 edits)
 *   3b: Markov Chain DGA Detection   (bigram model trained on trusted domains)
 *   3c: Viterbi Word Segmentation    (probabilistic risk-word detection)
 *   3d: Structural Anomaly Scoring   (TLD risk, entropy, consonant-vowel ratio)
 * Layer 4: TFLite ML Model           → ~2ms (Dual-input inference)
 * Layer 5: Hybrid Server Enrichment  → async, non-blocking
 *
 * HEURISTIC CAP: 0.80f — no heuristic engine alone exceeds this.
 * Total critical-path latency: ~2.5ms
 */
class PhishingDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    // Bloom filter for memory-efficient whitelist (~1MB vs ~40MB HashSet)
    private var trustedBloom: BloomFilter? = null
    // Small set only for BK-Tree source + Markov training (top 5K domains)
    private val bkSourceDomains: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // ── Dynamic Engines ──
    private val bkTree = BKTree()
    private val markovChain = MarkovChain()
    private val viterbiSegmenter = ViterbiSegmenter()

    // ── Server Enrichment ──
    var threatReporter: ThreatReporter? = null

    companion object {
        private const val MODEL_PATH = "phishing_model.tflite"
        private const val MAX_URL_LENGTH = 200
        private const val TAG = "PhishingDetector"
        private val IP_PATTERN = java.util.regex.Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")
        private const val CHARS = "abcdefghijklmnopqrstuvwxyz0123456789-,;.!?:'\"/\\|_@#$%^&*~`+-=<>()[]{} "

        // ── THRESHOLDS ──
        const val THRESHOLD_SAFE = 0.40f
        const val THRESHOLD_PHISHING = 0.85f
        const val THRESHOLD_NEUTRAL_PHISHING = 0.50f
        const val HEURISTIC_CAP = 0.80f
        const val REPORT_SUSPICIOUS = 0.50f
        // DNS-only path thresholds (no ML, so more conservative)
        const val DNS_BLOCK_THRESHOLD = 0.80f // Increased from 0.70f to require absolute heuristic consensus (zero false positives)
        const val DNS_SUSPICIOUS_THRESHOLD = 0.65f // Increased from 0.40f to stop notification spam for CDNs/Ads

        private const val BK_TOP_N = 5000
        private const val BK_MAX_DIST = 2

        // ── NEUTRAL INFRASTRUCTURE ──
        private val NEUTRAL_HOSTING_PLATFORMS = setOf(
            "vercel.app", "netlify.app", "onrender.com", "railway.app",
            "fly.dev", "deno.dev", "workers.dev", "pages.dev", "r2.dev",
            "web.app", "firebaseapp.com", "appspot.com", "herokuapp.com",
            "azurewebsites.net", "azurestaticapps.net", "cloudfront.net", "amplifyapp.com",
            "github.io", "gitlab.io", "bitbucket.io", "gitbook.io",
            "surge.sh", "glitch.me", "replit.app", "repl.co",
            "sites.google.com", "forms.gle", "blogspot.com",
            "bit.ly", "t.co", "tinyurl.com", "is.gd", "goo.gl",
            "rb.gy", "cutt.ly", "shorturl.at", "rebrand.ly", "tiny.cc",
            "weebly.com", "wixsite.com", "wordpress.com", "carrd.co",
            "notion.site", "webflow.io", "framer.app", "strikingly.com",
            "000webhostapp.com", "infinityfreeapp.com", "rf.gd",
            "s3.amazonaws.com", "storage.googleapis.com", "blob.core.windows.net"
        )

        // ── SUSPICIOUS TLD RISK MAP ──
        private val TLD_RISK = mapOf(
            "tk" to 0.25f, "ml" to 0.25f, "ga" to 0.25f, "cf" to 0.25f, "gq" to 0.25f,
            "buzz" to 0.20f, "top" to 0.20f, "icu" to 0.20f, "xyz" to 0.15f,
            "click" to 0.15f, "link" to 0.10f, "work" to 0.10f, "date" to 0.15f,
            "racing" to 0.15f, "win" to 0.15f, "bid" to 0.15f, "stream" to 0.15f,
            "download" to 0.15f, "loan" to 0.15f, "trade" to 0.10f, "webcam" to 0.15f
        )

        // ── HOMOGLYPH NORMALIZATION ──
        private val HOMOGLYPHS = mapOf(
            '0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '@' to 'a',
            '5' to 's', '7' to 't', '8' to 'b', '!' to 'i'
        )
        private val DIGRAPH_HOMOGLYPHS = arrayOf("rn" to "m", "vv" to "w")

        private fun normalize(value: Float, min: Float, max: Float): Float {
            if (max <= min) return 0f
            return ((value - min) / (max - min)).coerceIn(0f, 1f)
        }
    }

    // ── Init (non-blocking, fail-open) ──
    @Volatile private var trancoReady = false
    @Volatile private var enginesReady = false
    private var userWhitelist: Set<String> = emptySet()
    // Full domain set used only during init for Markov training, then released
    private var markovTrainingSet: MutableSet<String>? = java.util.concurrent.ConcurrentHashMap.newKeySet()

    init {
        initTFLite()
        loadTrustedDomains()
        CoroutineScope(Dispatchers.IO).launch {
            UserWhitelistManager(context).whitelistedDomains.collect { domains ->
                userWhitelist = domains
            }
        }
    }

    private fun loadTrustedDomains() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = context.assets.open("vvip_domains.txt")
                val reader = BufferedReader(InputStreamReader(inputStream))
                val bloom = BloomFilter(expectedItems = 500_000, fpRate = 0.0001)
                var lineNum = 0
                reader.forEachLine { line ->
                    val domain = line.split(",").lastOrNull()?.trim()?.lowercase()
                    if (!domain.isNullOrEmpty()) {
                        val clean = if (domain.startsWith("www.")) domain.substring(4) else domain
                        bloom.add(clean)
                        markovTrainingSet?.add(clean)

                        // Build BK-Tree from top N domains
                        if (lineNum < BK_TOP_N) {
                            bkSourceDomains.add(clean)
                            val name = clean.substringBeforeLast('.')
                            if (name.length >= 4) {
                                bkTree.insert(normalizeHomoglyphs(name), clean)
                            }
                        }
                        lineNum++
                    }
                }
                reader.close()
                trustedBloom = bloom
                trancoReady = true
                Log.d(TAG, "Loaded $lineNum trusted domains into BloomFilter, BK-Tree: ${bkTree.size} nodes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tranco list", e)
                trancoReady = true // fail-open
            }

            // Train Markov chain then release training set
            try {
                markovTrainingSet?.let { markovChain.train(it) }
                Log.d(TAG, "Markov chain trained")
            } catch (e: Exception) {
                Log.e(TAG, "Markov training failed", e)
            } finally {
                markovTrainingSet = null // release ~40MB
                enginesReady = true
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  TFLITE INIT
    // ════════════════════════════════════════════════════════════════

    private fun initTFLite() {
        try {
            val options = Interpreter.Options().apply {
                numThreads = 1
                setUseNNAPI(false)
            }
            val model = loadModelFile(MODEL_PATH)
            interpreter = Interpreter(model, options)
            Log.d(TAG, "TFLite model loaded.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model.", e)
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // ════════════════════════════════════════════════════════════════
    //  DOMAIN HELPERS
    // ════════════════════════════════════════════════════════════════

    private fun getSafeHost(urlOrDomain: String): String {
        var processed = urlOrDomain.lowercase().trim()
        if (!processed.startsWith("http://") && !processed.startsWith("https://")) {
            processed = "http://$processed"
        }
        return try {
            val uri = URI(processed)
            var host = uri.host ?: ""
            if (host.startsWith("www.")) host = host.substring(4)
            host
        } catch (e: Exception) { "" }
    }

    private fun extractRootDomain(domain: String): String {
        val parts = domain.split('.')
        return if (parts.size >= 2) "${parts[parts.size - 2]}.${parts[parts.size - 1]}" else domain
    }

    private fun extractSubdomain(domain: String, platform: String): String {
        if (domain == platform) return ""
        val suffix = ".$platform"
        return if (domain.endsWith(suffix)) domain.removeSuffix(suffix) else ""
    }

    // ════════════════════════════════════════════════════════════════
    //  NEUTRAL INFRASTRUCTURE CHECK
    // ════════════════════════════════════════════════════════════════

    private fun isNeutralInfrastructure(domain: String): Boolean {
        val d = domain.lowercase()
        for (p in NEUTRAL_HOSTING_PLATFORMS) {
            if (d == p || d.endsWith(".$p")) return true
        }
        return false
    }

    private fun getMatchingPlatform(domain: String): String? {
        val d = domain.lowercase()
        for (p in NEUTRAL_HOSTING_PLATFORMS) {
            if (d == p || d.endsWith(".$p")) return p
        }
        return null
    }

    // ════════════════════════════════════════════════════════════════
    //  HOMOGLYPH NORMALIZATION
    // ════════════════════════════════════════════════════════════════

    private fun normalizeHomoglyphs(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input.lowercase()) {
            sb.append(HOMOGLYPHS[c] ?: c)
        }
        var result = sb.toString()
        for ((from, to) in DIGRAPH_HOMOGLYPHS) {
            result = result.replace(from, to)
        }
        return result
    }

    // ════════════════════════════════════════════════════════════════
    //  LAYER 3: DYNAMIC THREAT ANALYSIS (~0.3ms total)
    //  Combines: BK-Tree + Markov + Viterbi + Structural
    //  ALL engines are dynamic — zero hardcoded brand/keyword lists
    // ════════════════════════════════════════════════════════════════

    /**
     * Dynamic threat score combining all algorithmic engines.
     * Returns 0.0 to HEURISTIC_CAP (0.80).
     */
    private fun dynamicThreatScore(domain: String, isNeutral: Boolean): Pair<Float, String> {
        var score = 0f
        val breakdown = StringBuilder()
        val domainLabel = domain.substringBeforeLast('.').substringBeforeLast('.')
        val normalizedLabel = normalizeHomoglyphs(domainLabel)

        // ── 3a: BK-Tree Brand Proximity (0 to 0.40) ──
        val bkResults = bkTree.search(normalizedLabel, BK_MAX_DIST)
        var bkScore = 0f
        if (bkResults.isNotEmpty()) {
            val best = bkResults.minByOrNull { it.distance }!!
            // Only flag if the incoming domain is NOT itself in the trusted list
            val incomingRoot = extractRootDomain(domain)
            val bloom = trustedBloom
            val notTrusted = bloom == null || (!bloom.mightContain(incomingRoot) && !bloom.mightContain(domain))
            if (notTrusted) {
                bkScore = when (best.distance) {
                    0 -> if (isNeutral) 0.40f else 0.30f
                    1 -> 0.35f
                    2 -> 0.20f
                    else -> 0f
                }
                if (domainLabel != normalizedLabel && bkScore > 0f) {
                    bkScore = (bkScore + 0.10f).coerceAtMost(0.40f)
                    breakdown.append("HOMOGLYPH→${best.word}(d=${best.distance}) ")
                } else if (bkScore > 0f) {
                    breakdown.append("BK→${best.word}(d=${best.distance}) ")
                }
            }
        }
        score += bkScore

        // ── 3b: Markov Chain DGA Detection (0 to 0.25) ──
        val markovScore = markovChain.scoreDomain(domain)
        if (markovScore > 0f) {
            breakdown.append("MARKOV=${String.format("%.2f", markovScore)} ")
        }
        score += markovScore

        // ── 3c: Viterbi Semantic Risk (0 to 0.35) ──
        val viterbiScore = viterbiSegmenter.scoreDomain(domain)
        if (viterbiScore > 0f) {
            breakdown.append("VITERBI=${String.format("%.2f", viterbiScore)} ")
        }
        // BK-Tree + Viterbi synergy: brand proximity + risk words = very suspicious
        if (bkScore > 0f && viterbiScore > 0f) {
            score += viterbiScore * 1.5f  // amplify when both fire
            breakdown.append("SYNERGY ")
        } else {
            score += viterbiScore
        }

        // ── 3d: Structural Anomaly Scoring (0 to 0.25) ──
        var structScore = 0f

        // TLD risk
        val tld = domain.substringAfterLast('.')
        val tldRisk = TLD_RISK[tld] ?: 0f
        structScore += tldRisk

        // Punycode / IDN homograph
        if (domain.contains("xn--")) {
            structScore += 0.15f
            breakdown.append("PUNYCODE ")
        }

        // Shannon entropy (DGA-like randomness)
        val entropy = calculateShannonEntropy(domain.substringBefore('/'))
        if (entropy > 4.2) {
            structScore += 0.10f
            breakdown.append("ENTROPY=${String.format("%.1f", entropy)} ")
        }

        // Excessive subdomain depth
        val dotCount = domain.count { it == '.' }
        if (dotCount >= 4) {
            structScore += 0.10f
            breakdown.append("DEPTH=$dotCount ")
        }

        // Consonant-vowel ratio anomaly
        val cvRatio = consonantVowelRatio(domainLabel)
        if (cvRatio > 4.0f || cvRatio < 0.3f) {
            structScore += 0.10f
            breakdown.append("CV=${String.format("%.1f", cvRatio)} ")
        }

        if (tldRisk > 0f) breakdown.append("TLD=.$tld ")
        score += structScore.coerceAtMost(0.25f)

        // ── Neutral infrastructure multiplier ──
        if (isNeutral && score > 0.1f) {
            score *= 1.3f  // hosting platforms are inherently riskier
            breakdown.append("NEUTRAL_BOOST ")
        }

        val capped = score.coerceIn(0f, HEURISTIC_CAP)
        return Pair(capped, breakdown.toString().trim())
    }

    private fun calculateShannonEntropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val len = s.length.toDouble()
        val counts = IntArray(128)
        for (c in s) { val code = c.code; if (code < 128) counts[code]++ }
        var entropy = 0.0
        for (count in counts) {
            if (count > 0) { val p = count / len; entropy -= p * log2(p) }
        }
        return entropy
    }

    private fun consonantVowelRatio(s: String): Float {
        val vowels = "aeiou"
        var v = 0; var c = 0
        for (ch in s.lowercase()) {
            if (ch.isLetter()) {
                if (ch in vowels) v++ else c++
            }
        }
        return if (v == 0) c.toFloat() else c.toFloat() / v.toFloat()
    }

    // ════════════════════════════════════════════════════════════════
    //  CORE ANALYSIS ENGINE
    // ════════════════════════════════════════════════════════════════

    /**
     * DNS-ONLY fast path for VPN service.
     * Skips TFLite (useless without full URL features) and uses heuristic
     * score directly. Non-blocking — fails open if engines not ready.
     *
     * Thresholds are more conservative (higher) to compensate for no ML.
     */
    fun analyzeDomainRiskDnsOnly(domainName: String): Float {
        val domain = getSafeHost(domainName)
        if (domain.isEmpty()) return 0.0f

        // L0: User Whitelist
        if (userWhitelist.contains(domain)) return 0.0f

        // L1: BloomFilter whitelist (non-blocking, fail-open)
        if (!trancoReady) {
            Log.d(TAG, "DNS FAIL-OPEN (loading) → $domain")
            return 0.0f
        }
        val isNeutral = isNeutralInfrastructure(domain)
        if (!isNeutral) {
            val bloom = trustedBloom
            if (bloom != null) {
                var current = domain
                while (current.contains('.')) {
                    if (bloom.mightContain(current)) {
                        Log.d(TAG, "L1 BLOOM → $domain (matched $current)")
                        return 0.0f
                    }
                    val dotIndex = current.indexOf('.')
                    if (dotIndex >= current.length - 1) break
                    current = current.substring(dotIndex + 1)
                }
            }
        }

        // L2: Raw IP
        if (IP_PATTERN.matcher(domain).matches()) {
            Log.d(TAG, "L2 RAW IP → $domain")
            return HEURISTIC_CAP
        }

        // L3: Dynamic Threat Analysis (non-blocking)
        if (!enginesReady) {
            Log.d(TAG, "DNS FAIL-OPEN (engines loading) → $domain")
            return 0.0f
        }
        val (heuristicScore, engineBreakdown) = dynamicThreatScore(domain, isNeutral)

        // DNS path: heuristic IS the final score (no ML dilution)
        val finalScore = heuristicScore

        if (finalScore >= DNS_BLOCK_THRESHOLD) {
            Log.d(TAG, "DNS BLOCKED → $domain (score=${"%.3f".format(finalScore)}, engines=$engineBreakdown)")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportBlocked(domain, finalScore, "dns_only $engineBreakdown")
            }
        } else if (finalScore >= DNS_SUSPICIOUS_THRESHOLD) {
            Log.d(TAG, "DNS SUSPICIOUS → $domain (score=${"%.3f".format(finalScore)})")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportSuspicious(domain, finalScore, "dns_only $engineBreakdown")
            }
        }

        return finalScore
    }

    /**
     * Full-URL analysis path for URL Scanner, shared links, etc.
     * Uses ML + heuristics. Non-blocking.
     */
    fun analyzeDomainRisk(domainName: String): Float {
        val domain = getSafeHost(domainName)
        if (domain.isEmpty()) return 0.0f

        // L0: User Whitelist
        if (userWhitelist.contains(domain)) {
            Log.d(TAG, "L0 USER WHITELISTED → $domain")
            return 0.0f
        }

        // L1: BloomFilter whitelist (non-blocking)
        if (!trancoReady) return 0.0f
        val isNeutral = isNeutralInfrastructure(domain)
        if (!isNeutral) {
            val bloom = trustedBloom
            if (bloom != null) {
                var current = domain
                while (current.contains('.')) {
                    if (bloom.mightContain(current)) {
                        Log.d(TAG, "L1 BLOOM → $domain (matched $current)")
                        return 0.0f
                    }
                    val dotIndex = current.indexOf('.')
                    if (dotIndex >= current.length - 1) break
                    current = current.substring(dotIndex + 1)
                }
            }
        }

        // L2: Raw IP
        if (IP_PATTERN.matcher(domain).matches()) {
            Log.d(TAG, "L2 RAW IP → $domain")
            return HEURISTIC_CAP
        }

        // L3: Dynamic Threat Analysis (non-blocking)
        if (!enginesReady) return 0.0f
        val (heuristicScore, engineBreakdown) = dynamicThreatScore(domain, isNeutral)

        // L4: TFLite ML Model
        val url = if (domainName.startsWith("http://") || domainName.startsWith("https://"))
            domainName else "https://$domainName"
        val mlScore = applyTFLite(url)

        // COMBINE: If ML failed (0.0), trust heuristics alone at 90%
        val combinedScore = if (mlScore <= 0.01f) {
            (heuristicScore * 0.9f).coerceIn(0f, 1f)
        } else {
            (mlScore * 0.6f + heuristicScore * 0.4f).coerceIn(0f, 1f)
        }

        // Reporting
        if (combinedScore >= HEURISTIC_CAP) {
            Log.d(TAG, "BLOCKED → $domain (combined=${"%.3f".format(combinedScore)}, ml=${"%.3f".format(mlScore)}, heur=${"%.3f".format(heuristicScore)}, engines=$engineBreakdown)")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportBlocked(domain, combinedScore, "ml=${"%.3f".format(mlScore)} heur=${"%.3f".format(heuristicScore)} $engineBreakdown")
            }
        } else if (combinedScore >= REPORT_SUSPICIOUS) {
            Log.d(TAG, "SUSPICIOUS → $domain (combined=${"%.3f".format(combinedScore)}, ml=${"%.3f".format(mlScore)}, heur=${"%.3f".format(heuristicScore)})")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportSuspicious(domain, combinedScore, "ml=${"%.3f".format(mlScore)} heur=${"%.3f".format(heuristicScore)} $engineBreakdown")
            }
        }

        return combinedScore
    }

    // ════════════════════════════════════════════════════════════════
    //  TFLITE INFERENCE
    // ════════════════════════════════════════════════════════════════

    private fun extractTabularFeatures(url: String): Array<FloatArray> {
        val tabArray = FloatArray(13)
        if (url.isEmpty()) return arrayOf(tabArray)

        val clean = url.trim().lowercase()
        val cleanNoProtocol = clean.replace(Regex("^https?://"), "")
        val host = getSafeHost(url)

        val isIp = if (host.isNotEmpty() && IP_PATTERN.matcher(host).matches()) 1.0f else 0.0f
        val isHttps = if (clean.startsWith("https")) 1.0f else 0.0f
        val isShortened = if (host.contains("bit.ly") || host.contains("t.co") || host.contains("tinyurl")) 1.0f else 0.0f

        var letterCount = 0; var digitCount = 0
        for (c in url) { if (c.isLetter()) letterCount++; if (c.isDigit()) digitCount++ }
        val digitRatio = if (letterCount > 0) digitCount.toFloat() / letterCount.toFloat() else 0f
        val specialCharsCount = url.count { it == '?' || it == '=' || it == '%' }

        tabArray[0] = normalize(url.length.toFloat(), 0f, 200f)
        tabArray[1] = normalize(host.length.toFloat(), 0f, 100f)
        tabArray[2] = normalize(url.count { it == '.' }.toFloat(), 0f, 10f)
        tabArray[3] = normalize(url.count { it == '-' }.toFloat(), 0f, 10f)
        tabArray[4] = normalize(url.count { it == '@' }.toFloat(), 0f, 5f)
        tabArray[5] = normalize((url.split("//").size - 1).toFloat(), 0f, 5f)
        tabArray[6] = normalize(host.count { it == '.' }.toFloat(), 0f, 10f)
        tabArray[7] = isIp
        tabArray[8] = isHttps
        tabArray[9] = normalize(specialCharsCount.toFloat(), 0f, 20f)
        tabArray[10] = digitRatio
        tabArray[11] = isShortened
        tabArray[12] = normalize(cleanNoProtocol.count { it == '/' }.toFloat(), 0f, 15f)

        return arrayOf(tabArray)
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim().lowercase()
        if (clean.startsWith("https://")) clean = clean.substring(8)
        else if (clean.startsWith("http://")) clean = clean.substring(7)
        return if (clean.length > MAX_URL_LENGTH) clean.substring(0, MAX_URL_LENGTH) else clean
    }

    private fun urlToByteBuffer(url: String): ByteBuffer {
        val cleaned = cleanUrl(url)
        val byteBuffer = ByteBuffer.allocateDirect(MAX_URL_LENGTH * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until MAX_URL_LENGTH) {
            if (i < cleaned.length) {
                val index = CHARS.indexOf(cleaned[i])
                byteBuffer.putFloat(if (index != -1) (index + 1).toFloat() else 0f)
            } else {
                byteBuffer.putFloat(0f)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun applyTFLite(url: String): Float {
        val interp = interpreter ?: return 0.0f
        val inputs = arrayOf(urlToByteBuffer(url), extractTabularFeatures(url))
        val scoreOutput = Array(1) { FloatArray(1) }
        val outputMap = mapOf(0 to scoreOutput)

        return try {
            synchronized(this) {
                interp.runForMultipleInputsOutputs(inputs, outputMap)
                scoreOutput[0][0]
            }
        } catch (e: Exception) {
            Log.e(TAG, "TFLite failed", e)
            0.0f
        }
    }

    /**
     * Binary phishing decision.
     */
    fun isPhishing(urlOrDomain: String): Boolean {
        val domain = getSafeHost(urlOrDomain)
        val isNeutral = isNeutralInfrastructure(domain)
        val threshold = if (isNeutral) THRESHOLD_NEUTRAL_PHISHING else THRESHOLD_PHISHING
        val score = analyzeDomainRisk(urlOrDomain)
        return score > threshold
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
