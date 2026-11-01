package com.protctus.edge.phishing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Fast, dedicated DataStore for the whitelist to avoid locking other settings
val Context.whitelistDataStore: DataStore<Preferences> by preferencesDataStore(name = "phishing_whitelist")

class UserWhitelistManager(private val context: Context) {

    companion object {
        private val WHITELIST_DOMAINS_KEY = stringSetPreferencesKey("whitelisted_domains")
        private val BLOCKLIST_DOMAINS_KEY = stringSetPreferencesKey("blocked_domains")
        private val DECIDED_DOMAINS_KEY = stringSetPreferencesKey("decided_domains")
    }

    /**
     * Exposes the whitelist as a fast-updating Flow of Set<String>.
     */
    val whitelistedDomains: Flow<Set<String>> = context.whitelistDataStore.data
        .map { preferences ->
            preferences[WHITELIST_DOMAINS_KEY] ?: emptySet()
        }

    /**
     * Exposes the blocklist as a Flow.
     */
    val blockedDomains: Flow<Set<String>> = context.whitelistDataStore.data
        .map { preferences ->
            preferences[BLOCKLIST_DOMAINS_KEY] ?: emptySet()
        }

    /**
     * Exposes the "decided" set — domains the user has already acted on
     * (either safe or blocked). These never trigger notifications again.
     */
    val decidedDomains: Flow<Set<String>> = context.whitelistDataStore.data
        .map { preferences ->
            preferences[DECIDED_DOMAINS_KEY] ?: emptySet()
        }

    /**
     * Adds a domain to the user's local whitelist + marks as decided.
     */
    suspend fun addDomain(domain: String) {
        val cleanDomain = domain.lowercase().trim()
        context.whitelistDataStore.edit { preferences ->
            val currentWhitelist = preferences[WHITELIST_DOMAINS_KEY] ?: emptySet()
            val currentDecided = preferences[DECIDED_DOMAINS_KEY] ?: emptySet()
            preferences[WHITELIST_DOMAINS_KEY] = currentWhitelist + cleanDomain
            preferences[DECIDED_DOMAINS_KEY] = currentDecided + cleanDomain
        }
    }

    /**
     * Adds a domain to the user's local blocklist + marks as decided.
     */
    suspend fun blockDomain(domain: String) {
        val cleanDomain = domain.lowercase().trim()
        context.whitelistDataStore.edit { preferences ->
            val currentBlocklist = preferences[BLOCKLIST_DOMAINS_KEY] ?: emptySet()
            val currentDecided = preferences[DECIDED_DOMAINS_KEY] ?: emptySet()
            preferences[BLOCKLIST_DOMAINS_KEY] = currentBlocklist + cleanDomain
            preferences[DECIDED_DOMAINS_KEY] = currentDecided + cleanDomain
        }
    }

    /**
     * Removes a domain from the user's local whitelist.
     */
    suspend fun removeDomain(domain: String) {
        val cleanDomain = domain.lowercase().trim()
        context.whitelistDataStore.edit { preferences ->
            val currentList = preferences[WHITELIST_DOMAINS_KEY] ?: emptySet()
            if (currentList.contains(cleanDomain)) {
                preferences[WHITELIST_DOMAINS_KEY] = currentList - cleanDomain
            }
        }
    }

    /**
     * Checks if a domain is whitelisted.
     */
    suspend fun isWhitelisted(domain: String): Boolean {
        var isSafe = false
        context.whitelistDataStore.edit { preferences ->
            val currentList = preferences[WHITELIST_DOMAINS_KEY] ?: emptySet()
            isSafe = currentList.contains(domain.lowercase().trim())
        }
        return isSafe
    }
}
