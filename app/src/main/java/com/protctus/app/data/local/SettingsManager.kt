package com.protctus.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore extension property
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "protctus_settings")

class SettingsManager(private val context: Context) {
    
    companion object {
        val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("is_biometric_enabled")
        val REAL_TIME_PROTECTION_KEY = booleanPreferencesKey("is_real_time_protection")
        val PHISHING_SHIELD_KEY = booleanPreferencesKey("is_phishing_shield_enabled")
        val JUICE_JACKING_KEY = booleanPreferencesKey("is_juice_jacking_enabled")
        val USB_PROTECTION_KEY = booleanPreferencesKey("is_usb_protection_enabled")
        val STALKERWARE_WHITELIST_KEY = stringSetPreferencesKey("stalkerware_whitelist")
        val GHOST_APP_DETECTOR_KEY = booleanPreferencesKey("is_ghost_app_detector_enabled")
        val APP_LANGUAGE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("app_language")
        val HAS_SEEN_WALKTHROUGH_KEY = booleanPreferencesKey("has_seen_walkthrough")
        val HEURISTICS_ENABLED_KEY = booleanPreferencesKey("is_heuristics_enabled")
        val INVISIBLE_WATCHER_KEY = booleanPreferencesKey("is_invisible_watcher_enabled")
        /** User-consented cloud telemetry (blocked/suspicious domain and malware reports). Default OFF (off by default). */
        val DATA_SHARING_ENABLED_KEY = booleanPreferencesKey("data_sharing_enabled")
        val IS_PREMIUM_USER_KEY = booleanPreferencesKey("is_premium_user")
        val SUBSCRIPTION_TIER_KEY = androidx.datastore.preferences.core.intPreferencesKey("subscription_tier")
        val PREMIUM_PAYMENT_ID_KEY = androidx.datastore.preferences.core.stringPreferencesKey("premium_payment_id")
        val LAST_PREMIUM_AD_TIMESTAMP_KEY = androidx.datastore.preferences.core.longPreferencesKey("last_premium_ad_timestamp")
    }

    val lastPremiumAdTimestamp: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_PREMIUM_AD_TIMESTAMP_KEY] ?: 0L
        }

    suspend fun setLastPremiumAdTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_PREMIUM_AD_TIMESTAMP_KEY] = timestamp
        }
    }

    val subscriptionTier: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SUBSCRIPTION_TIER_KEY] ?: 0 // 0 = Free, 1 = Premium, 2 = Elite
        }

    suspend fun setSubscriptionTier(tier: Int) {
        context.dataStore.edit { preferences ->
            preferences[SUBSCRIPTION_TIER_KEY] = tier
            // Legacy support for isPremiumUser flag
            preferences[IS_PREMIUM_USER_KEY] = tier > 0
        }
    }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] ?: true
        }
        
    val isRealTimeProtectionEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[REAL_TIME_PROTECTION_KEY] ?: true
        }
        
    val isPhishingShieldEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PHISHING_SHIELD_KEY] ?: true
        }

    val isJuiceJackingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[JUICE_JACKING_KEY] ?: false
        }

    val isUsbProtectionEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[USB_PROTECTION_KEY] ?: false
        }

    val stalkerwareWhitelist: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[STALKERWARE_WHITELIST_KEY] ?: emptySet()
        }

    val isGhostAppDetectorEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[GHOST_APP_DETECTOR_KEY] ?: false
        }

    val appLanguage: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[APP_LANGUAGE_KEY] ?: "en"
        }

    val hasSeenWalkthrough: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAS_SEEN_WALKTHROUGH_KEY] ?: false
        }

    val isHeuristicsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HEURISTICS_ENABLED_KEY] ?: false
        }

    val isInvisibleWatcherEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[INVISIBLE_WATCHER_KEY] ?: false
        }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }
    
    suspend fun setRealTimeProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REAL_TIME_PROTECTION_KEY] = enabled
        }
    }
    
    suspend fun setPhishingShieldEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PHISHING_SHIELD_KEY] = enabled
        }
    }

    suspend fun setJuiceJackingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[JUICE_JACKING_KEY] = enabled
        }
    }

    suspend fun setUsbProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USB_PROTECTION_KEY] = enabled
        }
    }

    suspend fun addStalkerwareWhitelist(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentList = preferences[STALKERWARE_WHITELIST_KEY] ?: emptySet()
            preferences[STALKERWARE_WHITELIST_KEY] = currentList + packageName
        }
    }

    suspend fun setGhostAppDetectorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GHOST_APP_DETECTOR_KEY] = enabled
        }
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE_KEY] = languageCode
        }
    }

    suspend fun setHasSeenWalkthrough(seen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_WALKTHROUGH_KEY] = seen
        }
    }

    suspend fun setHeuristicsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HEURISTICS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setInvisibleWatcherEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[INVISIBLE_WATCHER_KEY] = enabled
        }
    }

    /** Whether the user has explicitly opted into cloud telemetry sharing. Default false (off by default). */
    val isDataSharingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DATA_SHARING_ENABLED_KEY] ?: false
        }

    suspend fun setDataSharingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DATA_SHARING_ENABLED_KEY] = enabled
        }
    }

    val isPremiumUser: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_PREMIUM_USER_KEY] ?: false
        }

    val premiumPaymentId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PREMIUM_PAYMENT_ID_KEY] ?: ""
        }

    suspend fun setPremiumUser(isPremium: Boolean, paymentId: String = "") {
        context.dataStore.edit { preferences ->
            preferences[IS_PREMIUM_USER_KEY] = isPremium
            if (paymentId.isNotEmpty()) {
                preferences[PREMIUM_PAYMENT_ID_KEY] = paymentId
            }
        }
    }
}
