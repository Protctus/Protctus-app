package com.protctus.app.neuroshield.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Lexical Feature Extractor for NeuroShield ONNX Model
 *
 * Extracts all structural/lexical features from a domain hostname string,
 * replicating the exact feature engineering pipeline used during model training.
 *
 * All features are computed purely from the domain string — no network calls,
 * no external dependencies. The extraction is deterministic and allocation-minimal.
 *
 * The feature order MUST match the training pipeline's column order exactly,
 * as the ONNX model expects features at specific indices.
 *
 * Thread-safe: all methods are pure functions with no shared mutable state.
 */
class LexicalFeatureExtractor {

    companion object {
        /** Total number of features expected by the ONNX model. */
        const val FEATURE_COUNT = 53

        private const val MAX_DOMAIN_LENGTH = 2048
        private const val LN2 = 0.6931471805599453

        /** Country-code TLDs (ISO 3166-1 alpha-2, commonly used). */
        private val COUNTRY_TLDS = setOf(
            "ac","ad","ae","af","ag","ai","al","am","ao","aq","ar","as","at","au","aw","ax","az",
            "ba","bb","bd","be","bf","bg","bh","bi","bj","bl","bm","bn","bo","bq","br","bs","bt",
            "bv","bw","by","bz","ca","cc","cd","cf","cg","ch","ci","ck","cl","cm","cn","co","cr",
            "cu","cv","cw","cx","cy","cz","de","dj","dk","dm","do","dz","ec","ee","eg","eh","er",
            "es","et","eu","fi","fj","fk","fm","fo","fr","ga","gb","gd","ge","gf","gg","gh","gi",
            "gl","gm","gn","gp","gq","gr","gs","gt","gu","gw","gy","hk","hm","hn","hr","ht","hu",
            "id","ie","il","im","in","io","iq","ir","is","it","je","jm","jo","jp","ke","kg","kh",
            "ki","km","kn","kp","kr","kw","ky","kz","la","lb","lc","li","lk","lr","ls","lt","lu",
            "lv","ly","ma","mc","md","me","mf","mg","mh","mk","ml","mm","mn","mo","mp","mq","mr",
            "ms","mt","mu","mv","mw","mx","my","mz","na","nc","ne","nf","ng","ni","nl","no","np",
            "nr","nu","nz","om","pa","pe","pf","pg","ph","pk","pl","pm","pn","pr","ps","pt","pw",
            "py","qa","re","ro","rs","ru","rw","sa","sb","sc","sd","se","sg","sh","si","sj","sk",
            "sl","sm","sn","so","sr","ss","st","sv","sx","sy","sz","tc","td","tf","tg","th","tj",
            "tk","tl","tm","tn","to","tr","tt","tv","tw","tz","ua","ug","uk","us","uy","uz","va",
            "vc","ve","vg","vi","vn","vu","wf","ws","ye","yt","za","zm","zw"
        )

        /** Free hosting / shared-domain platforms (PSL private domains). */
        private val FREE_HOSTING_SUFFIXES = setOf(
            "blogspot.com", "wordpress.com", "weebly.com", "wixsite.com",
            "000webhostapp.com", "infinityfreeapp.com", "rf.gd",
            "herokuapp.com", "vercel.app", "netlify.app", "pages.dev",
            "web.app", "firebaseapp.com", "appspot.com",
            "github.io", "gitlab.io", "surge.sh", "glitch.me",
            "replit.app", "repl.co", "onrender.com", "railway.app",
            "fly.dev", "deno.dev", "workers.dev", "r2.dev",
            "azurewebsites.net", "azurestaticapps.net", "cloudfront.net",
            "amplifyapp.com", "notion.site", "webflow.io", "carrd.co",
            "framer.app", "strikingly.com", "sites.google.com"
        )

        private val VOWELS = "aeiou"
        private val RARE_CHARS = setOf('_', '~', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=')
    }

    /**
     * Extract all features from a domain hostname string.
     *
     * @param domain Raw domain string (e.g., "login-paypal.suspicious-site.tk").
     *               May include "www." prefix, Unicode, Punycode, or IP addresses.
     * @return FloatArray of exactly [FEATURE_COUNT] features in the model's expected order.
     */
    fun extract(domain: String): FloatArray {
        val features = FloatArray(FEATURE_COUNT)

        // Sanitize input
        val clean = sanitize(domain)
        if (clean.isEmpty()) return features

        // Parse domain components
        val parts = clean.split('.')
        val tld = if (parts.size >= 2) parts.last() else ""
        val sld = if (parts.size >= 2) parts[parts.size - 2] else clean
        val subdomainParts = if (parts.size >= 3) parts.dropLast(2) else emptyList()
        val subdomain = subdomainParts.joinToString(".")
        val domainDepth = parts.size

        // ── Basic domain features ──
        var idx = 0
        features[idx++] = clean.length.toFloat()                          // domain_length
        features[idx++] = max(0, domainDepth - 2).toFloat()               // subdomain_count
        features[idx++] = shannonEntropy(clean)                           // domain_entropy
        features[idx++] = digitRatio(clean)                               // digit_ratio
        features[idx++] = alphaRatio(clean)                               // alpha_ratio
        features[idx++] = vowelRatio(clean)                               // vowel_ratio
        features[idx++] = countDigits(clean).toFloat()                    // num_digits
        features[idx++] = countChar(clean, '.').toFloat()                 // num_dots
        features[idx++] = countChar(clean, '-').toFloat()                 // num_hyphens
        features[idx++] = tld.length.toFloat()                            // tld_length
        features[idx++] = maxConsonantRun(clean).toFloat()                // max_consonant_run
        features[idx++] = if (clean.contains("xn--")) 1f else 0f         // has_punycode
        features[idx++] = if (isRawIp(clean)) 1f else 0f                 // is_raw_ip
        features[idx++] = consonantRatio(clean)                           // consonant_ratio
        features[idx++] = countConsonants(clean).toFloat()                // consonant_count
        features[idx++] = countVowels(clean).toFloat()                    // vowel_count

        // ── Subdomain features ──
        val subdomainLengths = subdomainParts.map { it.length }
        features[idx++] = (subdomainLengths.maxOrNull() ?: 0).toFloat()   // longest_subdomain_len
        features[idx++] = if (subdomainLengths.isNotEmpty())
            subdomainLengths.average().toFloat() else 0f                  // average_subdomain_len

        // ── SLD features ──
        features[idx++] = sld.length.toFloat()                            // sld_length
        features[idx++] = shannonEntropy(sld)                             // sld_entropy
        features[idx++] = digitRatio(sld)                                 // sld_digit_ratio
        features[idx++] = countChar(sld, '-').toFloat()                   // sld_hyphen_count

        // ── Ratio features ──
        features[idx++] = if (clean.isNotEmpty())
            subdomain.length.toFloat() / clean.length.toFloat() else 0f   // subdomain_length_ratio
        features[idx++] = if (isFreeHosting(clean)) 1f else 0f            // is_free_hosting
        features[idx++] = if (tld.lowercase() in COUNTRY_TLDS) 1f else 0f // is_country_tld
        features[idx++] = if (clean.isNotEmpty())
            countChar(clean, '.').toFloat() / clean.length.toFloat() else 0f // dot_ratio
        features[idx++] = if (clean.isNotEmpty())
            countChar(clean, '-').toFloat() / clean.length.toFloat() else 0f // hyphen_ratio
        features[idx++] = if (clean.contains("--")) 1f else 0f           // has_double_hyphen
        features[idx++] = if (countDigits(clean) > 0) 1f else 0f         // has_numbers
        features[idx++] = if (subdomainParts.isNotEmpty()) 1f else 0f     // has_subdomains

        // ── SLD character features ──
        features[idx++] = countVowels(sld).toFloat()                      // sld_vowel_count
        features[idx++] = countConsonants(sld).toFloat()                  // sld_consonant_count
        features[idx++] = if (sld.isNotEmpty() && sld[0].isDigit()) 1f else 0f // sld_starts_with_digit
        features[idx++] = dgaScore(sld)                                   // sld_dga_score
        features[idx++] = countDigits(sld).toFloat()                      // sld_digit_count
        features[idx++] = countRareChars(sld).toFloat()                   // sld_rare_char_count
        features[idx++] = countDigitSequences(sld).toFloat()              // sld_digit_seq_count
        features[idx++] = maxConsonantRun(sld).toFloat()                  // sld_max_consonant_run

        // ── Subdomain structural features ──
        features[idx++] = digitRatio(subdomain)                           // subdomain_digit_ratio
        features[idx++] = subdomainParts.size.toFloat()                   // subdomain_token_count
        features[idx++] = countChar(subdomain, '-').toFloat()             // subdomain_hyphen_count
        features[idx++] = shannonEntropy(subdomain)                       // subdomain_entropy
        features[idx++] = if (shannonEntropy(sld) > 0f)
            shannonEntropy(subdomain) / shannonEntropy(sld) else 0f       // entropy_ratio

        // ── Cross-component features ──
        features[idx++] = subdomainSldSimilarity(subdomain, sld)          // subdomain_sld_similarity
        features[idx++] = charTransitionVariance(clean)                   // char_transition_variance
        features[idx++] = domainDepth.toFloat()                           // domain_depth

        // ── Dynamic features (zeroed — require training corpus) ──
        features[idx++] = 0f  // has_brand_in_sld
        features[idx++] = 0f  // brand_in_bad_tld
        features[idx++] = 0f  // is_new_gtld
        features[idx++] = 0f  // tld_phishing_rate
        features[idx++] = 0f  // sld_has_keyword
        features[idx++] = 0f  // subdomain_has_brand
        features[idx++] = 0f  // subdomain_has_keyword

        return features
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper Functions
    // ════════════════════════════════════════════════════════════════

    private fun sanitize(domain: String): String {
        var d = domain.trim().lowercase()
        if (d.length > MAX_DOMAIN_LENGTH) d = d.substring(0, MAX_DOMAIN_LENGTH)
        if (d.startsWith("http://")) d = d.removePrefix("http://")
        if (d.startsWith("https://")) d = d.removePrefix("https://")
        if (d.startsWith("www.")) d = d.removePrefix("www.")
        // Remove path, query, fragment
        val slashIdx = d.indexOf('/')
        if (slashIdx >= 0) d = d.substring(0, slashIdx)
        val questionIdx = d.indexOf('?')
        if (questionIdx >= 0) d = d.substring(0, questionIdx)
        val hashIdx = d.indexOf('#')
        if (hashIdx >= 0) d = d.substring(0, hashIdx)
        // Remove port
        val colonIdx = d.lastIndexOf(':')
        if (colonIdx >= 0) {
            val afterColon = d.substring(colonIdx + 1)
            if (afterColon.all { it.isDigit() }) d = d.substring(0, colonIdx)
        }
        d = d.removeSuffix(".")
        // Handle Punycode/IDN safely
        try {
            d = java.net.IDN.toASCII(d).lowercase()
        } catch (_: Exception) {
            // Malformed IDN — use raw string
        }
        return d
    }

    private fun shannonEntropy(s: String): Float {
        if (s.isEmpty()) return 0f
        val len = s.length.toFloat()
        val counts = IntArray(128)
        var nonAsciiCount = 0
        for (c in s) {
            val code = c.code
            if (code < 128) counts[code]++ else nonAsciiCount++
        }
        var entropy = 0.0
        for (count in counts) {
            if (count > 0) {
                val p = count.toDouble() / len
                entropy -= p * (ln(p) / LN2)
            }
        }
        if (nonAsciiCount > 0) {
            val p = nonAsciiCount.toDouble() / len
            entropy -= p * (ln(p) / LN2)
        }
        return entropy.toFloat()
    }

    private fun countDigits(s: String): Int {
        var count = 0
        for (c in s) if (c.isDigit()) count++
        return count
    }

    private fun countVowels(s: String): Int {
        var count = 0
        for (c in s) if (c.lowercaseChar() in VOWELS) count++
        return count
    }

    private fun countConsonants(s: String): Int {
        var count = 0
        for (c in s) {
            val lc = c.lowercaseChar()
            if (lc.isLetter() && lc !in VOWELS) count++
        }
        return count
    }

    private fun countChar(s: String, target: Char): Int {
        var count = 0
        for (c in s) if (c == target) count++
        return count
    }

    private fun countRareChars(s: String): Int {
        var count = 0
        for (c in s) if (c in RARE_CHARS) count++
        return count
    }

    private fun digitRatio(s: String): Float {
        if (s.isEmpty()) return 0f
        return countDigits(s).toFloat() / s.length.toFloat()
    }

    private fun alphaRatio(s: String): Float {
        if (s.isEmpty()) return 0f
        var count = 0
        for (c in s) if (c.isLetter()) count++
        return count.toFloat() / s.length.toFloat()
    }

    private fun vowelRatio(s: String): Float {
        if (s.isEmpty()) return 0f
        return countVowels(s).toFloat() / s.length.toFloat()
    }

    private fun consonantRatio(s: String): Float {
        if (s.isEmpty()) return 0f
        return countConsonants(s).toFloat() / s.length.toFloat()
    }

    private fun maxConsonantRun(s: String): Int {
        var maxRun = 0
        var currentRun = 0
        for (c in s) {
            val lc = c.lowercaseChar()
            if (lc.isLetter() && lc !in VOWELS) {
                currentRun++
                if (currentRun > maxRun) maxRun = currentRun
            } else {
                currentRun = 0
            }
        }
        return maxRun
    }

    private fun isRawIp(domain: String): Boolean {
        // IPv4 check
        val parts = domain.split('.')
        if (parts.size == 4) {
            val allNumeric = parts.all { part ->
                part.length in 1..3 && part.all { it.isDigit() } && part.toIntOrNull()?.let { it in 0..255 } == true
            }
            if (allNumeric) return true
        }
        // IPv6 check (simplified — contains colons and hex chars only)
        if (domain.contains(':') && domain.all { it == ':' || it.isDigit() || it in 'a'..'f' }) {
            return true
        }
        return false
    }

    private fun isFreeHosting(domain: String): Boolean {
        for (suffix in FREE_HOSTING_SUFFIXES) {
            if (domain == suffix || domain.endsWith(".$suffix")) return true
        }
        return false
    }

    private fun countDigitSequences(s: String): Int {
        var count = 0
        var inDigitSeq = false
        for (c in s) {
            if (c.isDigit()) {
                if (!inDigitSeq) {
                    count++
                    inDigitSeq = true
                }
            } else {
                inDigitSeq = false
            }
        }
        return count
    }

    /**
     * DGA (Domain Generation Algorithm) score.
     * High scores indicate random-looking strings typical of malware DGAs.
     * Computed via consonant-vowel transition frequency analysis.
     */
    private fun dgaScore(s: String): Float {
        if (s.length < 3) return 0f
        var transitions = 0
        var prevIsVowel: Boolean? = null
        for (c in s) {
            if (!c.isLetter()) continue
            val isVowel = c.lowercaseChar() in VOWELS
            if (prevIsVowel != null && isVowel != prevIsVowel) transitions++
            prevIsVowel = isVowel
        }
        val letterCount = s.count { it.isLetter() }
        if (letterCount < 2) return 0f
        // Normalized transition rate: high for alternating, low for runs
        val transitionRate = transitions.toFloat() / (letterCount - 1).toFloat()
        // DGA domains typically have extreme values (very low or very high transition rates)
        // Combined with entropy to produce a composite score
        val entropy = shannonEntropy(s)
        return ((1f - transitionRate) * 0.5f + min(entropy / 5f, 1f) * 0.5f)
    }

    /**
     * Jaccard similarity between subdomain and SLD character sets.
     * High similarity may indicate brand impersonation in subdomains.
     */
    private fun subdomainSldSimilarity(subdomain: String, sld: String): Float {
        if (subdomain.isEmpty() || sld.isEmpty()) return 0f
        val setA = subdomain.toSet()
        val setB = sld.toSet()
        val intersection = setA.intersect(setB).size
        val union = setA.union(setB).size
        return if (union > 0) intersection.toFloat() / union.toFloat() else 0f
    }

    /**
     * Variance of character-to-character ASCII distance transitions.
     * Legitimate domains have smooth transitions; DGA/random have high variance.
     */
    private fun charTransitionVariance(s: String): Float {
        if (s.length < 2) return 0f
        val transitions = mutableListOf<Float>()
        for (i in 0 until s.length - 1) {
            val diff = (s[i + 1].code - s[i].code).toFloat()
            transitions.add(diff)
        }
        if (transitions.isEmpty()) return 0f
        val mean = transitions.sum() / transitions.size
        var variance = 0f
        for (t in transitions) {
            val d = t - mean
            variance += d * d
        }
        return variance / transitions.size
    }
}
