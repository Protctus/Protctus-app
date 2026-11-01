package com.protctus.edge.phishing

import kotlin.math.ln

/**
 * Bigram Markov Chain trained on trusted domain character patterns.
 * Detects DGA/gibberish domains by measuring how "normal" character
 * transitions are compared to real-world domains.
 *
 * Trained dynamically on vvip_domains.txt — zero hardcoded patterns.
 */
class MarkovChain {

    // 27 = a-z(0-25) + other(26)
    private val bigramCounts = IntArray(729)
    private val unigramCounts = IntArray(27)
    private val bigramLogProb = FloatArray(729) { -10f }
    private var trained = false

    private fun idx(c: Char): Int = if (c in 'a'..'z') (c - 'a') else 26

    /**
     * Train on a collection of domain names. Call once during init.
     * Learns character transition probabilities from real domains.
     */
    fun train(domains: Collection<String>) {
        for (domain in domains) {
            val name = domain.substringBeforeLast('.').lowercase()
            if (name.length < 2) continue
            for (i in name.indices) {
                val ci = idx(name[i])
                unigramCounts[ci]++
                if (i > 0) {
                    val pi = idx(name[i - 1])
                    bigramCounts[pi * 27 + ci]++
                }
            }
        }
        // Compute log-probabilities with Laplace smoothing
        for (i in 0 until 27) {
            val total = unigramCounts[i] + 27
            for (j in 0 until 27) {
                val count = bigramCounts[i * 27 + j] + 1
                bigramLogProb[i * 27 + j] = ln(count.toFloat() / total.toFloat())
            }
        }
        trained = true
    }

    /**
     * Score a domain name. Returns 0.0 (normal) to 0.25 (gibberish/DGA).
     * Measures average bigram log-probability against trained distribution.
     */
    fun scoreDomain(domain: String): Float {
        if (!trained) return 0f
        val name = domain.substringBeforeLast('.').lowercase()
            .replace("-", "").replace(".", "")
        if (name.length < 3) return 0f

        var totalLogProb = 0f
        var count = 0
        for (i in 1 until name.length) {
            val pi = idx(name[i - 1])
            val ci = idx(name[i])
            totalLogProb += bigramLogProb[pi * 27 + ci]
            count++
        }
        if (count == 0) return 0f
        val avg = totalLogProb / count

        // Thresholds calibrated: normal domains avg ~ -2.5 to -3.5
        return when {
            avg > -3.0f -> 0.00f  // Very normal
            avg > -3.5f -> 0.05f  // Slightly unusual
            avg > -4.0f -> 0.10f  // Unusual
            avg > -4.5f -> 0.15f  // Suspicious
            avg > -5.0f -> 0.20f  // Very suspicious
            else        -> 0.25f  // Likely DGA/gibberish
        }
    }
}
