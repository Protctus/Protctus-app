package com.protctus.app.engine.quark

import org.json.JSONObject

/**
 * Represents a single Quark behavioral detection rule.
 *
 * Derived from the real Quark-Engine rule format (quark-engine/quark/core/struct/ruleobject.py).
 * Each rule defines a "crime" — a suspicious behavior — described by:
 *   - required permissions
 *   - a pair of suspicious Android APIs
 *   - a threat score weight
 *   - category labels
 *
 * Five-stage evaluation (from Quark's criminal-law-inspired model):
 *   Stage 1: App requests the required permissions
 *   Stage 2: App contains at least one of the two APIs
 *   Stage 3: App contains BOTH APIs
 *   Stage 4: Both APIs are reachable from a common parent (simplified for mobile)
 *   Stage 5: APIs share data flow (simplified to permission+API correlation for mobile)
 */
data class QuarkRule(
    val ruleId: String,
    val crime: String,
    val permissions: List<String>,
    val api: List<ApiTarget>,
    val score: Double,
    val labels: List<String>
) {
    /** Five-stage check state — mirrors Quark's check_item[0..4] */
    val checkItem = BooleanArray(5) { false }

    /** Reset check state for re-evaluation */
    fun resetCheck() {
        for (i in checkItem.indices) checkItem[i] = false
    }

    /** Number of stages passed (0–5) */
    val confidence: Int get() = checkItem.count { it }

    /** Confidence as percentage (0–100) */
    val confidencePercent: Int get() = confidence * 20

    /**
     * Weighted score calculation — directly from Quark source:
     *   get_score(confidence) = (2^(confidence-1) × score) / 16
     *
     * Source: quark/core/struct/ruleobject.py line 124-136
     */
    fun getWeightedScore(): Double {
        if (confidence == 0) return 0.0
        return (Math.pow(2.0, (confidence - 1).toDouble()) * score) / 16.0
    }

    companion object {
        /**
         * Parse a Quark rule from JSON.
         * Format matches real Quark rules from ev-flow/quark-rules.
         */
        fun fromJson(ruleId: String, json: JSONObject): QuarkRule {
            val crime = json.getString("crime")

            val permArray = json.getJSONArray("permission")
            val permissions = (0 until permArray.length()).map { permArray.getString(it) }

            val apiArray = json.getJSONArray("api")
            val apis = (0 until apiArray.length()).map { i ->
                val apiObj = apiArray.getJSONObject(i)
                ApiTarget(
                    className = apiObj.getString("class"),
                    methodName = apiObj.getString("method"),
                    descriptor = apiObj.getString("descriptor")
                )
            }

            val score = json.getDouble("score")

            val labelArray = json.getJSONArray("label")
            val labels = (0 until labelArray.length()).map { labelArray.getString(it) }

            return QuarkRule(
                ruleId = ruleId,
                crime = crime,
                permissions = permissions,
                api = apis,
                score = score,
                labels = labels
            )
        }
    }
}

/**
 * Represents a target API method in Dalvik descriptor format.
 * Matches Quark's rule "api" entries.
 */
data class ApiTarget(
    val className: String,
    val methodName: String,
    val descriptor: String
) {
    /**
     * Convert from Dalvik descriptor to Java-style for matching against
     * Android PackageManager data.
     *
     * e.g., "Landroid/telephony/SmsManager;" → "android.telephony.SmsManager"
     */
    val javaClassName: String
        get() = className
            .removePrefix("L")
            .removeSuffix(";")
            .replace('/', '.')

    /** Full method signature for matching */
    val fullSignature: String
        get() = "$className->$methodName$descriptor"
}
