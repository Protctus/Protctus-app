package com.protctus.edge.phishing

import kotlin.math.ln

/**
 * Viterbi-based word segmentation engine for domain names.
 * Splits "secureloginverify" → ["secure", "login", "verify"]
 * using dynamic programming with a word frequency model.
 *
 * NOT substring matching — this is probabilistic segmentation
 * that finds the most likely word boundaries in concatenated text.
 */
class ViterbiSegmenter {

    private val wordFreq = HashMap<String, Float>(500)
    private val riskWords = HashSet<String>(60)
    private var maxWordLen = 15
    private var totalLogFreq = 0f

    init {
        loadDictionary()
    }

    private fun loadDictionary() {
        // Common English words with log-frequency (higher = more common)
        val common = arrayOf(
            "the", "and", "for", "you", "that", "was", "are", "with",
            "this", "have", "from", "one", "had", "not", "but", "all",
            "can", "been", "who", "will", "more", "when", "some", "them",
            "into", "has", "two", "way", "may", "then", "its", "over",
            "such", "our", "after", "use", "your", "how", "said", "each",
            "she", "which", "their", "time", "very", "make", "like",
            "just", "know", "take", "come", "could", "good", "look",
            "year", "give", "most", "find", "here", "thing", "many",
            "well", "also", "back", "much", "get", "go", "home", "work",
            "life", "help", "shop", "store", "buy", "sell", "game",
            "play", "code", "tech", "data", "info", "web", "app", "dev",
            "net", "cloud", "host", "mail", "news", "blog", "live",
            "chat", "book", "read", "care", "food", "love", "style",
            "art", "video", "photo", "world", "online", "digital",
            "smart", "pro", "hub", "lab", "studio", "market", "media",
            "social", "center", "group", "team", "site", "page", "real",
            "best", "fast", "safe", "easy", "open", "new", "old", "big",
            "top", "hot", "my", "on", "up", "out", "now", "day", "man",
            "run", "set", "try", "ask", "own", "say", "end", "far",
            "yes", "add", "act", "age", "air", "area", "base", "bill",
            "body", "call", "came", "card", "case", "city", "club",
            "cost", "cut", "deal", "deep", "does", "done", "door",
            "down", "drop", "drug", "east", "edge", "even", "ever",
            "face", "fact", "fall", "feel", "fill", "fine", "fire",
            "firm", "five", "flat", "form", "four", "full", "fund",
            "gave", "girl", "glad", "goes", "gold", "gone", "grew",
            "grow", "hair", "half", "hall", "hand", "hard", "head",
            "hear", "high", "hill", "hold", "hole", "hope", "hour",
            "huge", "idea", "iron", "item", "jobs", "join", "jump",
            "keen", "keep", "kept", "kick", "kids", "kill", "kind",
            "king", "knew", "lack", "laid", "lake", "land", "last",
            "late", "lead", "left", "less", "lift", "line", "list",
            "load", "lock", "long", "lord", "lose", "loss", "lost",
            "luck", "made", "main", "male", "many", "mark", "mass",
            "mate", "mean", "meet", "mile", "mind", "mine", "miss",
            "mode", "mood", "moon", "move", "much", "must", "name",
            "near", "neat", "neck", "need", "next", "nice", "nine",
            "none", "nose", "note", "once", "only", "onto", "pack",
            "pair", "pale", "palm", "park", "part", "pass", "past",
            "path", "peak", "pick", "pink", "plan", "plus", "poem",
            "poll", "pool", "poor", "port", "pose", "post", "pour",
            "pull", "pump", "pure", "push", "race", "rain", "rank",
            "rare", "rate", "rest", "rich", "ride", "ring", "rise",
            "risk", "road", "rock", "rode", "role", "roll", "roof",
            "room", "root", "rose", "rule", "rush", "salt", "same",
            "sand", "sang", "save", "seal", "seat", "seed", "seek",
            "seem", "seen", "self", "send", "sent", "ship", "shot",
            "show", "shut", "sick", "side", "sign", "sing", "sits",
            "size", "skin", "slip", "slow", "snow", "soft", "soil",
            "sold", "sole", "song", "soon", "sort", "soul", "span",
            "spin", "spot", "star", "stay", "stem", "step", "stop",
            "suit", "sure", "swim", "tail", "tale", "talk", "tall",
            "tank", "tape", "task", "tell", "tend", "term", "test",
            "text", "than", "thin", "thus", "tied", "till", "tiny",
            "told", "toll", "tone", "took", "tool", "tops", "tore",
            "torn", "toss", "tour", "town", "trap", "tree", "trim",
            "trip", "true", "tube", "tune", "turn", "twin", "type",
            "ugly", "unit", "upon", "urge", "used", "user", "vast",
            "vice", "view", "vote", "wage", "wait", "wake", "walk",
            "wall", "want", "ward", "warm", "warn", "wash", "wave",
            "ways", "weak", "wear", "week", "went", "were", "west",
            "what", "when", "whom", "wide", "wife", "wild", "wind",
            "wine", "wing", "wire", "wise", "wish", "wood", "wore",
            "word", "worn", "wrap", "yard", "yeah", "year", "zero",
            "zone", "bank", "pay", "cash", "money", "credit", "invest",
            "exchange", "finance", "platform", "network", "service",
            "system", "solution", "global", "direct", "express"
        )
        var freq = 10f
        for (w in common) {
            wordFreq[w] = freq
            freq = (freq - 0.02f).coerceAtLeast(0.5f)
        }

        // Risk words — used ONLY after segmentation, not as substring matches
        val risk = arrayOf(
            "login", "signin", "logon", "auth", "password", "credential",
            "verify", "confirm", "urgent", "alert", "suspend", "expire",
            "locked", "blocked", "payment", "billing", "invoice", "refund",
            "wallet", "claim", "prize", "winner", "reward", "gift",
            "offer", "bonus", "secure", "security", "protect", "safety",
            "account", "profile", "dashboard", "portal", "panel",
            "console", "airdrop", "presale", "token", "mint", "stake",
            "swap", "defi", "kyc", "vault", "recover", "reset",
            "unlock", "update", "authenticate", "helpdesk", "support"
        )
        for (w in risk) {
            wordFreq[w] = 3.0f  // moderate frequency so they get segmented correctly
            riskWords.add(w)
        }

        totalLogFreq = ln(wordFreq.values.sum())
    }

    private fun wordCost(word: String): Float {
        val freq = wordFreq[word]
        if (freq != null) return -ln(freq) + 1f
        // Unknown word: penalize proportional to length
        return word.length * 3.0f + 8.0f
    }

    /**
     * Segment a domain label into most-likely English words.
     * E.g., "secureloginverify" → ["secure", "login", "verify"]
     */
    fun segment(text: String): List<String> {
        val s = text.lowercase().replace("-", "").replace("_", "")
        val n = s.length
        if (n == 0) return emptyList()

        val cost = FloatArray(n + 1) { Float.MAX_VALUE }
        val back = IntArray(n + 1) { -1 }
        cost[0] = 0f

        for (i in 1..n) {
            for (j in maxOf(0, i - maxWordLen) until i) {
                val w = s.substring(j, i)
                val c = cost[j] + wordCost(w)
                if (c < cost[i]) {
                    cost[i] = c
                    back[i] = j
                }
            }
        }

        val words = mutableListOf<String>()
        var i = n
        while (i > 0) {
            val j = back[i]
            if (j < 0) break
            words.add(0, s.substring(j, i))
            i = j
        }
        return words
    }

    /**
     * Score a domain for semantic risk based on word segmentation.
     * Returns 0.0 (benign) to 0.35 (high risk-word density).
     */
    fun scoreDomain(domain: String): Float {
        val label = domain.substringBeforeLast('.').substringBeforeLast('.')
        if (label.length < 4) return 0f

        val words = segment(label)
        if (words.isEmpty()) return 0f

        var riskCount = 0
        var knownCount = 0
        for (w in words) {
            if (riskWords.contains(w)) riskCount++
            if (wordFreq.containsKey(w)) knownCount++
        }

        return when {
            riskCount >= 3 -> 0.35f
            riskCount >= 2 -> 0.25f
            riskCount == 1 && knownCount >= 2 -> 0.10f
            riskCount == 1 -> 0.05f
            else -> 0.0f
        }
    }

    fun isRiskWord(word: String): Boolean = riskWords.contains(word)
}
