package com.protctus.app.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object QuestTracker {
    private const val PREFS_NAME = "protctus_quest_tracker"
    private const val KEY_COMPLETED_ACTIONS = "completed_actions"
    private const val KEY_LAST_DATE = "last_quest_date"

    private var globalTracker: GlobalQuestTracker? = null

    private fun checkAndResetDaily(prefs: SharedPreferences) {
        val cal = java.util.Calendar.getInstance()
        val today = "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        if (today != lastDate) {
            prefs.edit()
                .putStringSet(KEY_COMPLETED_ACTIONS, emptySet())
                .putString(KEY_LAST_DATE, today)
                .apply()
        }
    }

    fun init(tracker: GlobalQuestTracker) {
        globalTracker = tracker
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Marks an explicit action_trigger as completed. 
     * E.g., "scan_url" or "dark_web_scan"
     */
    fun markTaskCompleted(context: Context, actionTrigger: String) {
        val trigger = actionTrigger.lowercase().trim()
        val prefs = getPrefs(context)
        checkAndResetDaily(prefs)
        val currentSet = prefs.getStringSet(KEY_COMPLETED_ACTIONS, emptySet()) ?: emptySet()
        val newSet = currentSet.toMutableSet()
        newSet.add(trigger)
        prefs.edit().putStringSet(KEY_COMPLETED_ACTIONS, newSet).apply()

        // Emit exactly the raw string trigger to the event bus
        globalTracker?.let { tracker ->
            CoroutineScope(Dispatchers.IO).launch {
                tracker.emitEvent(trigger)
            }
        }
    }

    /**
     * Checks if the exact action_trigger string was completed. Zero heuristics.
     */
    fun isActionCompleted(context: Context, actionTrigger: String): Boolean {
        val prefs = getPrefs(context)
        checkAndResetDaily(prefs)
        val currentSet = prefs.getStringSet(KEY_COMPLETED_ACTIONS, emptySet()) ?: emptySet()
        return currentSet.contains(actionTrigger.lowercase().trim())
    }

    /**
     * Legacy signature alias used directly when fetching/claiming to mark the specific ID.
     */
    fun markActionCompleted(context: Context, action: String) {
        markTaskCompleted(context, action)
    }

    fun clearAction(context: Context, action: String) {
        val prefs = getPrefs(context)
        checkAndResetDaily(prefs)
        val currentSet = prefs.getStringSet(KEY_COMPLETED_ACTIONS, emptySet()) ?: emptySet()
        val newSet = currentSet.toMutableSet()
        newSet.remove(action.lowercase().trim())
        prefs.edit().putStringSet(KEY_COMPLETED_ACTIONS, newSet).apply()
    }
}
