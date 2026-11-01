package com.protctus.edge.phishing

import android.content.Context
import android.util.Log
import com.protctus.app.neuroshield.engine.EnsemblePhishingEngine
import com.protctus.app.neuroshield.engine.ThreatVerdict
import com.protctus.app.neuroshield.engine.TrustedDomainCache
import com.protctus.app.neuroshield.engine.VerdictLevel
import kotlin.math.log2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI

/**
 * Protctus NeuroShield Multi-Layer Phishing Detection Engine v4
 *
 * Hybrid Architecture: 100% offline, on-device inference via ensemble of models.
 *
 * Layer 0: User Whitelist            → ~0μs (HashSet)
 * Layer 1: Tranco Whitelist          → ~1μs (BloomFilter)
 * Layer 2: Raw IP Guard              → ~1μs (Regex)
 * Layer 3: Dynamic Threat Analysis   → ~0.3ms
 *   3a: BK-Tree Brand Proximity      (top 5000 domains, fuzzy match ≤2 edits)
 *   3b: Markov Chain DGA Detection   (bigram model trained on trusted domains)
 *   3c: Viterbi Word Segmentation    (probabilistic risk-word detection)
 *   3d: Structural Anomaly Scoring   (TLD risk, entropy, consonant-vowel ratio)
 * Layer 4: LightGBM Model            → ~1-2ms (53 features, native inference)
 * Layer 5: ONNX LogisticRegression   → ~1-2ms (53 features)
 * Layer 6: Ensemble Verdict Engine   → score fusion + classification
 *
 * HEURISTIC CAP: 0.80f — no heuristic engine alone exceeds this.
 * Total critical-path latency: ~3ms
 */
class PhishingDetector(private val context: Context) {

    // ── Ensemble Engine (LightGBM + ONNX + Heuristics) ──
    private val ensembleEngine = EnsemblePhishingEngine(context)

    // ── Legacy Heuristic Engines (preserved for fallback) ──
    private var trustedBloom: BloomFilter? = null
    private val bkSourceDomains: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val bkTree = BKTree()
    private val markovChain = MarkovChain()
    private val viterbiSegmenter = ViterbiSegmenter()

    // ── Server Enrichment ──
    var threatReporter: ThreatReporter? = null

    companion object {
        private const val TAG = "PhishingDetector"
        private val IP_PATTERN = java.util.regex.Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")

        // ── THRESHOLDS ──
        const val THRESHOLD_SAFE = 0.30f
        const val THRESHOLD_PHISHING = 0.65f
        const val HEURISTIC_CAP = 0.80f

        // DNS-only path thresholds
        const val DNS_BLOCK_THRESHOLD = 0.65f
        const val DNS_SUSPICIOUS_THRESHOLD = 0.30f

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
        private val DIGLYPH_DIGRAPHS = arrayOf("rn" to "m", "vv" to "w")

        private fun normalize(value: Float, min: Float, max: Float): Float {
            if (max <= min) return 0f
            return ((value - min) / (max - min)).coerceIn(0f, 1f)
        }
    }

    // ── Init (non-blocking, fail-open) ──
    @Volatile private var trancoReady = false
    @Volatile private var enginesReady = false
    private var userWhitelist: Set<String> = emptySet()
    private val markovTrainingSet: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val verdictCache = TrustedDomainCache()

    init {
        loadTrustedDomains()
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
                markovTrainingSet.clear() // release ~40MB
                enginesReady = true
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  DOMAIN HELPERS
    // ════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════
    //  NEUTRAL INFRASTRUCTURE CHECK
    // ════════════════════════════════════════════════════════════════════

    private fun isNeutralInfrastructure(domain: String): Boolean {
        val d = domain.lowercase()
        for (p in NEUTRAL_HOSTING_PLATFORMS) {
            if (d == p || d.endsWith(".$p")) return true
        }
        return false
    }

    // ════════════════════════════════════════════════════════════════════
    //  HOMOGLYPH NORMALIZATION
    // ════════════════════════════════════════════════════════════════════

    private fun normalizeHomoglyphs(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input.lowercase()) {
            sb.append(HOMOGLYPHS[c] ?: c)
        }
        var result = sb.toString()
        for ((from, to) in DIGLYPH_DIGRAPHS) {
            result = result.replace(from, to)
        }
        return result
    }

    // ════════════════════════════════════════════════════════════════════
    //  LAYER 3: DYNAMIC THREAT ANALYSIS (~0.3ms total)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Dynamic threat score combining all algorithmic engines.
     * Returns 0.0 to HEURISTIC_CAP (0.80).
     */
    private fun dynamicThreatScore(domain: String, isNeutral: Boolean): Pair<Float, String> {
        var score = 0f
        val breakdown = StringBuilder()
        val domainLabel = extractSld(domain)
        val normalizedLabel = normalizeHomoglyphs(domainLabel)

        // ── 3a: BK-Tree Brand Proximity (0 to 0.40) ──
        val bkResults = bkTree.search(normalizedLabel, BK_MAX_DIST)
        var bkScore = 0f
        if (bkResults.isNotEmpty()) {
            val best = bkResults.minByOrNull { it.distance }!!
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
            breakdown.append("MARKOV=${"%.2f".format(markovScore)} ")
        }
        score += markovScore

        // ── 3c: Viterbi Semantic Risk (0 to 0.35) ──
        val viterbiScore = viterbiSegmenter.scoreDomain(domain)
        if (viterbiScore > 0f) {
            breakdown.append("VITERBI=${"%.2f".format(viterbiScore)} ")
        }
        if (bkScore > 0f && viterbiScore > 0f) {
            score += viterbiScore * 1.5f
            breakdown.append("SYNERGY ")
        } else {
            score += viterbiScore
        }

        // ── 3d: Structural Anomaly Scoring (0 to 0.25) ──
        var structScore = 0f

        val tld = domain.substringAfterLast('.')
        val tldRisk = TLD_RISK[tld] ?: 0f
        structScore += tldRisk

        if (domain.contains("xn--")) {
            structScore += 0.15f
            breakdown.append("PUNYCODE ")
        }

        val entropy = calculateShannonEntropy(domain.substringBefore('/'))
        if (entropy > 4.2) {
            structScore += 0.10f
            breakdown.append("ENTROPY=${"%.1f".format(entropy)} ")
        }

        val dotCount = domain.count { it == '.' }
        if (dotCount >= 4) {
            structScore += 0.10f
            breakdown.append("DEPTH=$dotCount ")
        }

        val cvRatio = consonantVowelRatio(domainLabel)
        if (cvRatio > 4.0f || cvRatio < 0.3f) {
            structScore += 0.10f
            breakdown.append("CV=${"%.1f".format(cvRatio)} ")
        }

        if (tldRisk > 0f) breakdown.append("TLD=.$tld ")
        score += structScore.coerceAtMost(0.25f)

        if (isNeutral && score > 0.1f) {
            score *= 1.3f
            breakdown.append("NEUTRAL_BOOST ")
        }

        val capped = score.coerceIn(0f, HEURISTIC_CAP)
        return Pair(capped, breakdown.toString().trim())
    }

    /**
     * Extract the second-level domain (SLD) — the label just before the TLD.
     */
    private fun extractSld(domain: String): String {
        val parts = domain.split('.')
        return if (parts.size >= 2) parts[parts.size - 2] else domain
    }

    // ════════════════════════════════════════════════════════════════════
    //  CORE ANALYSIS ENGINE (ENSEMBLE)
    // ════════════════════════════════════════════════════════════════════

    /**
     * DNS-path analysis with full ensemble pipeline.
     *
     * Called synchronously from the C++ VPN DNS loop via JNI.
     * Total latency budget: < 3ms.
     *
     * Returns the fused score for the VPN service to decide block/allow/alert.
     */
    fun analyzeDomainRiskDnsOnly(domainName: String): Float {
        val domain = getSafeHost(domainName)
        if (domain.isEmpty()) return 0.0f

        // L0: User Whitelist
        if (userWhitelist.contains(domain)) return 0.0f

        // Cache hit — return immediately
        val cached = verdictCache.get(domain)
        if (cached != null) return cached.confidence

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
                    if (bloom.mightContain(current)) return 0.0f
                    val dotIndex = current.indexOf('.')
                    if (dotIndex >= current.length - 1) break
                    current = current.substring(dotIndex + 1)
                }
            }
        }

        // L2: Raw IP
        if (IP_PATTERN.matcher(domain).matches()) {
            return HEURISTIC_CAP
        }

        // L3: Dynamic Threat Analysis (non-blocking)
        if (!enginesReady) return 0.0f
        val (heuristicScore, engineBreakdown) = dynamicThreatScore(domain, isNeutral)

        // L4-L6: Ensemble (LightGBM + ONNX + Heuristics)
        val verdict = ensembleEngine.evaluate(domain, heuristicScore)
        val finalScore = verdict.confidence

        // Cache the result
        verdictCache.put(domain, verdict)

        if (finalScore >= DNS_BLOCK_THRESHOLD) {
            Log.d(TAG, "DNS BLOCKED → $domain (score=${"%.3f".format(finalScore)}, engines=$engineBreakdown)")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportBlocked(domain, finalScore, "ensemble ml=$engineBreakdown")
            }
        } else if (finalScore >= DNS_SUSPICIOUS_THRESHOLD) {
            Log.d(TAG, "DNS SUSPICIOUS → $domain (score=${"%.3f".format(finalScore)})")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportSuspicious(domain, finalScore, "ensemble ml=$engineBreakdown")
            }
        }

        return finalScore
    }

    /**
     * Full-URL analysis path for URL Scanner, shared links, etc.
     * Uses ensemble ML + heuristics. Non-blocking.
     */
    fun analyzeDomainRisk(domainName: String): Float {
        val domain = getSafeHost(domainName)
        if (domain.isEmpty()) return 0.0f

        // L0: User Whitelist
        if (userWhitelist.contains(domain)) return 0.0f

        // Cache hit
        val cached = verdictCache.get(domain)
        if (cached != null) return cached.confidence

        // L1: BloomFilter whitelist
        if (!trancoReady) return 0.0f
        val isNeutral = isNeutralInfrastructure(domain)
        if (!isNeutral) {
            val bloom = trustedBloom
            if (bloom != null) {
                var current = domain
                while (current.contains('.')) {
                    if (bloom.mightContain(current)) return 0.0f
                    val dotIndex = current.indexOf('.')
                    if (dotIndex >= current.length - 1) break
                    current = current.substring(dotIndex + 1)
                }
            }
        }

        // L2: Raw IP
        if (IP_PATTERN.matcher(domain).matches()) return HEURISTIC_CAP

        // L3: Dynamic Threat Analysis
        if (!enginesReady) return 0.0f
        val (heuristicScore, engineBreakdown) = dynamicThreatScore(domain, isNeutral)

        // L4-L6: Ensemble
        val verdict = ensembleEngine.evaluate(domain, heuristicScore)
        val combinedScore = verdict.confidence

        verdictCache.put(domain, verdict)

        if (combinedScore >= HEURISTIC_CAP) {
            Log.d(TAG, "BLOCKED → $domain (combined=${"%.3f".format(combinedScore)}, engines=$engineBreakdown)")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportBlocked(domain, combinedScore, "ensemble ml=$engineBreakdown")
            }
        } else if (combinedScore >= THRESHOLD_SAFE) {
            Log.d(TAG, "SUSPICIOUS → $domain (combined=${"%.3f".format(combinedScore)}, ml=$engineBreakdown)")
            CoroutineScope(Dispatchers.IO).launch {
                threatReporter?.reportSuspicious(domain, combinedScore, "ensemble ml=$engineBreakdown")
            }
        }

        return combinedScore
    }

    /**
     * Get the full ThreatVerdict for a domain (used by repository layer).
     */
    fun getVerdict(domainName: String): ThreatVerdict {
        val domain = getSafeHost(domainName)
        if (domain.isEmpty()) {
            return ThreatVerdict(
                verdict = VerdictLevel.REQUIRES_NETWORK_VERIFICATION,
                confidence = 0f,
                explanation = "Empty or invalid domain — insufficient evidence.",
                triggeredIndicators = emptyList(),
                recommendedAction = "Enter a valid domain before scanning."
            )
        }

        if (userWhitelist.contains(domain)) {
            return trustedVerdict(domain, "User allowlist")
        }

        val cached = verdictCache.get(domain)
        if (cached != null) return cached

        if (!trancoReady) {
            return insufficientEvidenceVerdict(domain, "Trusted-domain data is still loading")
        }

        val isNeutral = isNeutralInfrastructure(domain)
        if (!isNeutral) {
            val bloom = trustedBloom
            if (bloom != null) {
                var current = domain
                while (current.contains('.')) {
                    if (bloom.mightContain(current)) {
                        return trustedVerdict(domain, "Trusted-domain allowlist")
                    }
                    val dotIndex = current.indexOf('.')
                    if (dotIndex >= current.length - 1) break
                    current = current.substring(dotIndex + 1)
                }
            }
        }

        if (IP_PATTERN.matcher(domain).matches()) {
            return ThreatVerdict(
                verdict = VerdictLevel.PHISHING,
                confidence = HEURISTIC_CAP,
                explanation = "Raw IP address used instead of a domain name.",
                triggeredIndicators = listOf("Raw IP address used instead of domain name"),
                recommendedAction = "Do not open this address unless you can independently verify it."
            )
        }

        if (!enginesReady) {
            return insufficientEvidenceVerdict(domain, "Local heuristic engines are unavailable")
        }

        val (heuristicScore, _) = dynamicThreatScore(domain, isNeutral)
        val verdict = ensembleEngine.evaluate(domain, heuristicScore)
        verdictCache.put(domain, verdict)
        return verdict
    }

    private fun trustedVerdict(domain: String, source: String): ThreatVerdict = ThreatVerdict(
        verdict = VerdictLevel.SAFE,
        confidence = 0f,
        explanation = "$source matched for $domain. This is an allowlist decision, not a full threat analysis.",
        triggeredIndicators = listOf(source),
        recommendedAction = "No immediate action required."
    )

    private fun insufficientEvidenceVerdict(domain: String, reason: String): ThreatVerdict = ThreatVerdict(
        verdict = VerdictLevel.REQUIRES_NETWORK_VERIFICATION,
        confidence = 0f,
        explanation = "$reason for $domain; it was not classified as safe.",
        triggeredIndicators = emptyList(),
        recommendedAction = "Try again after the detector finishes initialization."
    )

    /**
     * Binary phishing decision.
     */
    fun isPhishing(urlOrDomain: String): Boolean {
        val score = analyzeDomainRisk(urlOrDomain)
        return score > THRESHOLD_PHISHING
    }

    fun close() {
        ensembleEngine.close()
        verdictCache.clear()
    }

    // ════════════════════════════════════════════════════════════════════
    //  SHANNON ENTROPY & HELPERS
    // ════════════════════════════════════════════════════════════════════

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
}
