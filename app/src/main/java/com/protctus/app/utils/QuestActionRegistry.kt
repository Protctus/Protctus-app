package com.protctus.app.utils

import com.protctus.app.navigation.Route

object QuestActionRegistry {
    
    /**
     * Converts CamelCase to snake_case (e.g. "DarkWebScan" -> "dark_web_scan")
     */
    fun String.toSnakeCase(): String {
        return this.replace(Regex("([a-z])([A-Z]+)"), "$1_$2").lowercase()
    }

    /**
     * 100% Dynamic Fuzzy Matcher.
     * Maps arbitrary backend action triggers to internal Routes without ANY static rules.
     * Works by normalizing both strings (removing spaces/underscores) and checking substring matches.
     */
    fun getRouteForTrigger(actionTrigger: String): Route {
        val normalizedTrigger = actionTrigger.lowercase().replace(Regex("[^a-z0-9]"), "")

        // Scan all sealed subclasses dynamically
        Route::class.sealedSubclasses.forEach { kclass ->
            val routeName = kclass.simpleName ?: return@forEach
            val normalizedRoute = routeName.lowercase().replace(Regex("[^a-z0-9]"), "")
            
            // Fuzzy Match: Does the trigger contain the route name, or vice versa?
            if (normalizedTrigger == normalizedRoute || 
                normalizedTrigger.contains(normalizedRoute) || 
                normalizedRoute.contains(normalizedTrigger)) {
                
                // If it's a data object, return it instantly
                kclass.objectInstance?.let { return it }
                
                // If it's a data class, try to instantiate it with default parameters
                try {
                    val constructor = kclass.constructors.firstOrNull()
                    if (constructor != null) {
                        return constructor.callBy(emptyMap()) as Route
                    }
                } catch (e: Exception) {
                    // Ignore instantiation failures and continue searching
                }
            }
        }

        // Failsafe if absolutely no fuzzy match is possible
        return Route.Dashboard
    }
}
