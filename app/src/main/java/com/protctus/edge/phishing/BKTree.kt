package com.protctus.edge.phishing

/**
 * Burkhard-Keller Tree for O(log N) fuzzy string matching.
 * Built from top 5000 trusted domains to dynamically detect typosquatting
 * without hardcoding brand names.
 */
class BKTree {

    class Node(val word: String, val original: String) {
        val children = HashMap<Int, Node>(4)
    }

    var root: Node? = null
        private set
    var size: Int = 0
        private set

    fun insert(word: String, originalDomain: String) {
        val node = Node(word, originalDomain)
        val r = root
        if (r == null) { root = node; size = 1; return }
        var current: Node = r
        while (true) {
            val dist = damerauLevenshtein(word, current.word)
            if (dist == 0) return // duplicate
            val child = current.children[dist]
            if (child == null) {
                current.children[dist] = node
                size++
                return
            }
            current = child
        }
    }

    data class Match(val word: String, val originalDomain: String, val distance: Int)

    fun search(query: String, maxDist: Int): List<Match> {
        val results = mutableListOf<Match>()
        val r = root ?: return results
        val stack = ArrayDeque<Node>(64)
        stack.addLast(r)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val dist = damerauLevenshtein(query, current.word)
            if (dist <= maxDist) {
                results.add(Match(current.word, current.original, dist))
            }
            val low = (dist - maxDist).coerceAtLeast(1)
            val high = dist + maxDist
            for ((d, child) in current.children) {
                if (d in low..high) stack.addLast(child)
            }
        }
        return results
    }

    companion object {
        fun damerauLevenshtein(a: String, b: String): Int {
            val la = a.length; val lb = b.length
            if (la == 0) return lb
            if (lb == 0) return la
            val dp = Array(la + 1) { IntArray(lb + 1) }
            for (i in 0..la) dp[i][0] = i
            for (j in 0..lb) dp[0][j] = j
            for (i in 1..la) {
                for (j in 1..lb) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + cost)
                    }
                }
            }
            return dp[la][lb]
        }
    }
}
