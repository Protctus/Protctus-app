package com.protctus.app.managers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.protctus.app.data.local.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Manages a user-defined whitelist of domains that should never be blocked by the Phishing Shield.
 * Optimized for high-speed lookups within the VPN DNS interception loop.
 */
@Singleton
class UserWhitelistManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val WHITELIST_KEY = stringSetPreferencesKey("user_domain_whitelist")
    }

    /**
     * Observable flow of the current whitelist.
     */
    val whitelistFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[WHITELIST_KEY] ?: emptySet()
        }

    /**
     * Fast synchronous-like lookup for the VPN interception loop.
     * Checks if a domain (or its parent) is whitelisted.
     */
    suspend fun isWhitelisted(domain: String): Boolean {
        val normalizedDomain = domain.lowercase().trim().removeSuffix(".")
        val currentWhitelist = context.dataStore.data.first()[WHITELIST_KEY] ?: emptySet()
        
        // Check exact match or if any whitelisted entry is a parent domain
        return currentWhitelist.any { whitelisted ->
            normalizedDomain == whitelisted || normalizedDomain.endsWith(".$whitelisted")
        }
    }

    /**
     * Adds a domain to the local whitelist.
     */
    suspend fun addDomain(domain: String) {
        val normalized = domain.lowercase().trim().removeSuffix(".")
        if (normalized.isEmpty()) return
        
        context.dataStore.edit { preferences ->
            val current = preferences[WHITELIST_KEY] ?: emptySet()
            preferences[WHITELIST_KEY] = current + normalized
        }
    }

    /**
     * Removes a domain from the local whitelist.
     */
    suspend fun removeDomain(domain: String) {
        val normalized = domain.lowercase().trim().removeSuffix(".")
        context.dataStore.edit { preferences ->
            val current = preferences[WHITELIST_KEY] ?: emptySet()
            preferences[WHITELIST_KEY] = current - normalized
        }
    }
}
