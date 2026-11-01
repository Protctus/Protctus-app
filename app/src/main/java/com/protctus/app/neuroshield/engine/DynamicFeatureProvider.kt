package com.protctus.app.neuroshield.engine

import android.content.Context
import android.util.Log
import java.io.InputStream

/**
 * Dynamic feature provider for NeuroShield features 46-52.
 *
 * Loads precomputed statistics from assets/neuroshield_dynamic_data.json and
 * computes the dynamic lexical features that require a training corpus:
 *
 *   46: has_brand_in_sld
 *   47: brand_in_bad_tld
 *   48: is_new_gtld
 *   49: tld_phishing_rate
 *   50: sld_has_keyword
 *   51: subdomain_has_brand
 *   52: subdomain_has_keyword
 *
 * These features were zeroed in the base [LexicalFeatureExtractor] because they
 * require corpus statistics. This provider loads those statistics from a bundled
 * JSON asset and computes them at inference time, keeping training and
 * production preprocessing consistent.
 *
 * Thread-safe: all methods are pure after initialization.
 */
class DynamicFeatureProvider(private val context: Context) {

    private companion object {
        private const val TAG = "DynamicFeatureProvider"
        private const val ASSET_NAME = "neuroshield_dynamic_data.json"
    }

    private val tldPhishingRates: Map<String, Float>
    private val brands: Set<String>
    private val suspiciousTlds: Set<String>
    private val newGtlds: Set<String>
    private val keywords: Set<String>

    init {
        val data = loadJsonAsset() ?: run {
            Log.w(TAG, "Failed to load $ASSET_NAME — dynamic features will be zeroed")
            emptyMap()
        }
        tldPhishingRates = parseTldRates(data)
        brands = parseStringSet(data, "brands")
        suspiciousTlds = parseStringSet(data, "suspicious_tlds")
        newGtlds = parseStringSet(data, "new_gtlds")
        keywords = parseStringSet(data, "keywords")

        Log.i(TAG, "Dynamic features loaded: ${tldPhishingRates.size} TLD rates, ${brands.size} brands")
    }

    /**
     * Compute dynamic features 46-52 for a domain.
     *
     * @param domain Raw domain string (will be sanitized internally)
     * @return FloatArray of 7 features in order: [46, 47, 48, 49, 50, 51, 52]
     */
    fun compute(domain: String): FloatArray {
        val features = FloatArray(7)
        val clean = sanitize(domain)
        if (clean.isEmpty()) return features

        val parts = clean.split('.')
        val tld = if (parts.size >= 2) parts.last().lowercase() else ""
        val sld = if (parts.size >= 2) parts[parts.size - 2].lowercase() else clean.lowercase()
        val subdomainParts = if (parts.size >= 3) parts.dropLast(2) else emptyList()
        val subdomain = subdomainParts.joinToString(".").lowercase()

        // 46: has_brand_in_sld
        features[0] = if (brands.any { sld.contains(it) }) 1f else 0f

        // 47: brand_in_bad_tld
        features[1] = if (features[0] > 0.5f && tld in suspiciousTlds) 1f else 0f

        // 48: is_new_gtld
        features[2] = if (tld in newGtlds) 1f else 0f

        // 49: tld_phishing_rate
        features[3] = tldPhishingRates[tld] ?: 0f

        // 50: sld_has_keyword
        features[4] = if (keywords.any { sld.contains(it) }) 1f else 0f

        // 51: subdomain_has_brand
        features[5] = if (brands.any { subdomain.contains(it) }) 1f else 0f

        // 52: subdomain_has_keyword
        features[6] = if (keywords.any { subdomain.contains(it) }) 1f else 0f

        return features
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ════════════════════════════════════════════════════════════════════

    private fun loadJsonAsset(): Map<String, Any>? {
        return try {
            context.assets.open(ASSET_NAME).use { stream ->
                parseJson(stream.readBytes())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dynamic data asset", e)
            null
        }
    }

    private fun parseJson(bytes: ByteArray): Map<String, Any> {
        // Minimal JSON parser — handles the flat structure of neuroshield_dynamic_data.json:
        // { "tld_phishing_rates": { "tk": 0.25, ... }, "brands": [...], ... }
        val text = String(bytes, charset = java.nio.charset.StandardCharsets.UTF_8).trim()
        if (!text.startsWith("{")) return emptyMap()

        val result = mutableMapOf<String, Any>()
        var pos = 1
        while (pos < text.length) {
            // Skip whitespace
            while (pos < text.length && text[pos].isWhitespace()) pos++
            if (pos >= text.length || text[pos] == '}') break

            // Read key
            if (text[pos] != '"') break
            pos++
            val keyStart = pos
            while (pos < text.length && text[pos] != '"') pos++
            val key = text.substring(keyStart, pos)
            pos++ // skip closing quote

            // Skip colon and whitespace
            while (pos < text.length && text[pos] != ':' && text[pos] != ',') pos++
            if (pos < text.length && text[pos] == ':') pos++
            while (pos < text.length && text[pos].isWhitespace()) pos++

            // Read value
            when {
                text[pos] == '{' -> {
                    // Nested object (tld_phishing_rates)
                    val nested = parseNestedObject(text, pos)
                    result[key] = nested.first
                    pos = nested.second
                }
                text[pos] == '[' -> {
                    // Array of strings
                    val arr = parseStringArray(text, pos)
                    result[key] = arr.first
                    pos = arr.second
                }
                else -> {
                    // Skip unknown value type
                    while (pos < text.length && text[pos] != ',' && text[pos] != '}') pos++
                }
            }

            // Skip comma
            while (pos < text.length && text[pos] != ',' && text[pos] != '}') pos++
            if (pos < text.length && text[pos] == ',') pos++
        }
        return result
    }

    private fun parseNestedObject(text: String, start: Int): Pair<Map<String, Float>, Int> {
        val result = mutableMapOf<String, Float>()
        var pos = start + 1 // skip '{'
        while (pos < text.length) {
            while (pos < text.length && text[pos].isWhitespace()) pos++
            if (pos >= text.length || text[pos] == '}') break
            if (text[pos] != '"') { pos++; continue }
            pos++
            val keyStart = pos
            while (pos < text.length && text[pos] != '"') pos++
            val key = text.substring(keyStart, pos)
            pos++ // skip closing quote
            while (pos < text.length && text[pos] != ':') pos++
            if (pos < text.length && text[pos] == ':') pos++
            while (pos < text.length && text[pos].isWhitespace()) pos++
            val valStart = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.' || text[pos] == '-')) pos++
            val valueStr = text.substring(valStart, pos)
            result[key] = valueStr.toFloatOrNull() ?: 0f
            while (pos < text.length && text[pos] != ',' && text[pos] != '}') pos++
            if (pos < text.length && text[pos] == ',') pos++
        }
        return result to (pos + 1)
    }

    private fun parseStringArray(text: String, start: Int): Pair<Set<String>, Int> {
        val result = mutableSetOf<String>()
        var pos = start + 1 // skip '['
        while (pos < text.length) {
            while (pos < text.length && text[pos].isWhitespace()) pos++
            if (pos >= text.length || text[pos] == ']') break
            if (text[pos] != '"') { pos++; continue }
            pos++
            val keyStart = pos
            while (pos < text.length && text[pos] != '"') pos++
            result.add(text.substring(keyStart, pos))
            pos++ // skip closing quote
            while (pos < text.length && text[pos] != ',' && text[pos] != ']') pos++
            if (pos < text.length && text[pos] == ',') pos++
        }
        return result to (pos + 1)
    }

    private fun parseTldRates(data: Map<String, Any>): Map<String, Float> {
        return (data["tld_phishing_rates"] as? Map<String, *> ?: emptyMap<String, Any>())
            .entries
            .associate { it.key to (it.value as? Number ?: 0f).toFloat() }
    }

    private fun parseStringSet(data: Map<String, Any>, key: String): Set<String> {
        return (data[key] as? List<*> ?: emptyList<String>())
            .filterIsInstance<String>()
            .toSet()
    }

    /**
     * Minimal sanitization matching LexicalFeatureExtractor.sanitize()
     * (duplicated here to keep this class self-contained).
     */
    private fun sanitize(domain: String): String {
        var d = domain.trim().lowercase()
        if (d.length > 2048) d = d.substring(0, 2048)
        for (prefix in listOf("http://", "https://")) {
            if (d.startsWith(prefix)) d = d.removePrefix(prefix)
        }
        // Percent-decode
        val sb = StringBuilder(d.length)
        var i = 0
        while (i < d.length) {
            val c = d[i]
            if (c == '%' && i + 2 < d.length) {
                val hi = d[i + 1]
                val lo = d[i + 2]
                val hexVal = when {
                    hi in '0'..'9' -> hi.code - '0'.code
                    hi in 'a'..'f' -> hi.code - 'a'.code + 10
                    hi in 'A'..'F' -> hi.code - 'A'.code + 10
                    else -> -1
                }
                val hexVal2 = when {
                    lo in '0'..'9' -> lo.code - '0'.code
                    lo in 'a'..'f' -> lo.code - 'a'.code + 10
                    lo in 'A'..'F' -> lo.code - 'A'.code + 10
                    else -> -1
                }
                if (hexVal != -1 && hexVal2 != -1) {
                    sb.append((hexVal shl 4 or hexVal2).toChar())
                    i += 3
                    continue
                }
            }
            sb.append(c)
            i++
        }
        d = sb.toString()
        // IPv6 brackets
        if (d.startsWith("[") && d.contains("]")) {
            val cb = d.indexOf(']')
            d = d.substring(1, cb)
            if (d.contains(':')) {
                val after = d.substring(d.indexOf(':') + 1)
                if (after.all { it.isDigit() }) d = d.substring(0, d.indexOf(':'))
            }
        }
        if (d.startsWith("www.")) d = d.removePrefix("www.")
        for (sep in listOf('/', '?', '#')) {
            val idx = d.indexOf(sep)
            if (idx >= 0) d = d.substring(0, idx)
        }
        val lastColon = d.lastIndexOf(':')
        if (lastColon >= 0) {
            val after = d.substring(lastColon + 1)
            if (after.all { it.isDigit() }) d = d.substring(0, lastColon)
        }
        d = d.removeSuffix(".")
        return d
    }
}
