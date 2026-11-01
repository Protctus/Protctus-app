package com.protctus.app.engine.quark

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads and manages Quark behavioral detection rules.
 *
 * Rules are loaded from the app's assets directory (assets/quark_rules/).
 * Each rule is a JSON file matching the real Quark-Engine rule format
 * from the ev-flow/quark-rules repository.
 *
 * Thread-safe: rules are loaded once and stored immutably.
 */
class QuarkRuleLoader private constructor() {

    private val _rules = mutableListOf<QuarkRule>()

    /** All loaded rules (immutable view) */
    val rules: List<QuarkRule> get() = _rules.toList()

    /** Number of loaded rules */
    val ruleCount: Int get() = _rules.size

    /** Rules grouped by label */
    val rulesByLabel: Map<String, List<QuarkRule>>
        get() = _rules.flatMap { rule ->
            rule.labels.map { label -> label to rule }
        }.groupBy({ it.first }, { it.second })

    /**
     * Load all rules from assets/quark_rules/ directory.
     * Each .json file is parsed as a QuarkRule.
     *
     * @param context Android context for asset access
     * @return number of rules successfully loaded
     */
    fun loadFromAssets(context: Context): Int {
        _rules.clear()
        try {
            val ruleFiles = context.assets.list(RULES_ASSET_DIR) ?: emptyArray()
            Log.d(TAG, "Found ${ruleFiles.size} rule files in assets/$RULES_ASSET_DIR")

            for (fileName in ruleFiles) {
                if (!fileName.endsWith(".json")) continue
                try {
                    val jsonStr = context.assets.open("$RULES_ASSET_DIR/$fileName").use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                    val jsonObj = JSONObject(jsonStr)
                    val ruleId = fileName.removeSuffix(".json")
                    val rule = QuarkRule.fromJson(ruleId, jsonObj)
                    _rules.add(rule)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse rule: $fileName — ${e.message}")
                }
            }
            Log.i(TAG, "Loaded ${_rules.size} Quark rules successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list rule files: ${e.message}")
        }
        return _rules.size
    }

    /**
     * Load rules from raw JSON strings (for testing or dynamic rule updates).
     *
     * @param rulesJson list of pairs (ruleId, jsonString)
     * @return number of rules successfully loaded
     */
    fun loadFromJsonStrings(rulesJson: List<Pair<String, String>>): Int {
        _rules.clear()
        for ((ruleId, json) in rulesJson) {
            try {
                val rule = QuarkRule.fromJson(ruleId, JSONObject(json))
                _rules.add(rule)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse rule $ruleId: ${e.message}")
            }
        }
        return _rules.size
    }

    /**
     * Get rules that are relevant for a given set of permissions.
     * A rule is relevant if the app requests at least one of the rule's required permissions.
     * This is a pre-filter to avoid evaluating irrelevant rules.
     */
    fun getRelevantRules(appPermissions: Set<String>): List<QuarkRule> {
        return _rules.filter { rule ->
            // Rules with no permission requirements always apply (they check API usage only)
            rule.permissions.isEmpty() ||
            rule.permissions.any { perm -> appPermissions.contains(perm) }
        }
    }

    companion object {
        private const val TAG = "QuarkRuleLoader"
        private const val RULES_ASSET_DIR = "quark_rules"

        @Volatile
        private var instance: QuarkRuleLoader? = null

        fun getInstance(): QuarkRuleLoader {
            return instance ?: synchronized(this) {
                instance ?: QuarkRuleLoader().also { instance = it }
            }
        }
    }
}
