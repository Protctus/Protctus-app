package com.protctus.edge.phishing

import android.content.Context
import android.content.SharedPreferences

class DetectionStats(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ProtctusStats", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BLOCKED_COUNT = "BLOCKED_PHISHING_COUNT"
        private const val KEY_RBI_ESCALATION_COUNT = "RBI_ESCALATION_COUNT"
        private const val KEY_RBI_CONFIRMED_COUNT = "RBI_CONFIRMED_MALICIOUS_COUNT"
    }

    /** Total phishing domains blocked (all layers combined) */
    fun getBlockedCount(): Int {
        return prefs.getInt(KEY_BLOCKED_COUNT, 0)
    }

    fun incrementBlockedCount() {
        val currentCount = getBlockedCount()
        prefs.edit().putInt(KEY_BLOCKED_COUNT, currentCount + 1).apply()
    }

    /** Total domains escalated to Cloud RBI for deep analysis */
    fun getRbiEscalationCount(): Int {
        return prefs.getInt(KEY_RBI_ESCALATION_COUNT, 0)
    }

    fun incrementRbiEscalationCount() {
        val currentCount = getRbiEscalationCount()
        prefs.edit().putInt(KEY_RBI_ESCALATION_COUNT, currentCount + 1).apply()
    }

    /** Domains confirmed malicious by Llama3 RBI AI analysis */
    fun getRbiConfirmedCount(): Int {
        return prefs.getInt(KEY_RBI_CONFIRMED_COUNT, 0)
    }

    fun incrementRbiConfirmedCount() {
        val currentCount = getRbiConfirmedCount()
        prefs.edit().putInt(KEY_RBI_CONFIRMED_COUNT, currentCount + 1).apply()
    }
}
