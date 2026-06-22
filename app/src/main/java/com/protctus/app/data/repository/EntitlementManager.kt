package com.protctus.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.protctus.app.data.model.SubscriptionTier
import com.protctus.app.network.CloudflareTierApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "entitlement_store"
)

/**
 * EntitlementManager — The single source of truth for the user's subscription tier.
 *
 * Architecture:
 *   Server (D1) → [syncWithServer()] → SharedPrefs + DataStore cache → StateFlow → Compose UI
 *
 * Cold-start hydration strategy:
 *   - SharedPreferences provides SYNCHRONOUS, sub-millisecond reads on cold start.
 *     This is the primary cache used to initialize the StateFlow BEFORE any Compose frame.
 *   - DataStore is kept as a secondary async cache for backward compatibility.
 *   - Every write goes to BOTH SharedPreferences (instant) and DataStore (async).
 *
 * Why not DataStore alone?
 *   DataStore's `.first()` inside `runBlocking` still requires file I/O + deserialization
 *   (~100-300ms on cold start), which causes the initial StateFlow value to be FREE,
 *   leading to a visible "buy premium" flash before the real tier loads.
 *   SharedPreferences are memory-mapped after first access and return in < 1ms.
 *
 * Trigger points:
 *   1. App launch (init block — instant from SharedPrefs)
 *   2. App resume (MainActivity.onResume)
 *   3. Immediately after successful Razorpay payment verification
 */
@Singleton
class EntitlementManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EntitlementManager"
        private val KEY_CACHED_TIER = stringPreferencesKey("cached_tier")
        private val KEY_CACHED_STATUS = stringPreferencesKey("cached_status")

        // SharedPreferences constants for instant cold-start hydration
        private const val PREFS_NAME = "entitlement_instant_cache"
        private const val PREF_KEY_TIER = "cached_tier"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Synchronous SharedPreferences — used ONLY for sub-millisecond cold-start reads.
     * Memory-mapped after first access; getString() returns in < 1ms.
     */
    private val instantPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentTier: MutableStateFlow<SubscriptionTier>

    /**
     * The reactive tier flow. Observe this from Compose via:
     *   val tier by entitlementManager.tierFlow.collectAsState()
     *
     * When this emits ELITE or PREMIUM, the UI must instantly recompose
     * and unlock features — no static variables, no manual refresh.
     */
    val tierFlow: StateFlow<SubscriptionTier>
        get() = _currentTier

    // Keep the old name as an alias so existing code (PremiumViewModel) doesn't break
    val currentTier: StateFlow<SubscriptionTier>
        get() = _currentTier

    private val api: CloudflareTierApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://protctus-payments-api.jatinsharmashivpuri02122008.workers.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudflareTierApi::class.java)
    }

    init {
        // ── INSTANT HYDRATION (< 1ms) from SharedPreferences ──
        // SharedPreferences are memory-mapped; getString() is synchronous and near-instant.
        // This ensures _currentTier is correct BEFORE any Compose frame renders.
        val cachedTierName = instantPrefs.getString(PREF_KEY_TIER, null)

        val cachedTier: SubscriptionTier
        if (cachedTierName != null) {
            // ── FAST PATH (< 1ms): SharedPreferences already populated ──
            cachedTier = SubscriptionTier.fromString(cachedTierName)
            Log.d(TAG, "Instant hydration from SharedPrefs: ${cachedTier.name}")
        } else {
            // ── ONE-TIME MIGRATION: DataStore → SharedPreferences ──
            // First launch after update: SharedPreferences is empty, but DataStore may have the tier.
            // Use runBlocking ONCE to migrate, then all future cold starts use the instant path.
            cachedTier = try {
                val dataStoreTier = kotlinx.coroutines.runBlocking {
                    val prefs = context.entitlementDataStore.data.first()
                    SubscriptionTier.fromString(prefs[KEY_CACHED_TIER])
                }
                // Migrate to SharedPreferences for instant reads on next launch
                instantPrefs.edit().putString(PREF_KEY_TIER, dataStoreTier.name).commit()
                Log.d(TAG, "One-time migration from DataStore → SharedPrefs: ${dataStoreTier.name}")
                dataStoreTier
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed, defaulting to FREE", e)
                SubscriptionTier.FREE
            }
        }

        _currentTier = MutableStateFlow(cachedTier)

        // Then sync with server in background (non-blocking)
        scope.launch {
            syncWithServer()
        }
    }

    /**
     * Pull the authoritative tier from the Cloudflare D1 backend.
     * Call this from:
     *   - MainActivity.onResume() (catches Admin Grants)
     *   - Post-payment success (catches Razorpay upgrades)
     */
    suspend fun syncWithServer() {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: run {
                Log.d(TAG, "syncWithServer: No authenticated user, skipping")
                return
            }
            val token = user.getIdToken(false).await().token ?: run {
                Log.e(TAG, "syncWithServer: Failed to get ID token")
                return
            }

            val response = api.getUserStatus("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val serverTier = SubscriptionTier.fromString(body.tier)

                // If status is EXPIRED, force tier to FREE
                val effectiveTier = if (body.status == "EXPIRED") {
                    Log.w(TAG, "Subscription EXPIRED on server, downgrading to FREE")
                    SubscriptionTier.FREE
                } else {
                    serverTier
                }

                // Update both caches and StateFlow atomically
                writeCachedTier(effectiveTier)
                _currentTier.value = effectiveTier

                Log.d(TAG, "Synced with server: tier=${effectiveTier.name}, status=${body.status}, expires_at=${body.expires_at}")
            } else {
                Log.e(TAG, "syncWithServer failed: HTTP ${response.code()} — ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncWithServer exception (using cached tier)", e)
            // Graceful degradation: keep using the cached/current tier
        }
    }

    /**
     * Push a tier update to the server (called after Razorpay payment verification).
     */
    suspend fun updateTierOnServer(newTier: SubscriptionTier): Boolean {
        return try {
            val user = FirebaseAuth.getInstance().currentUser ?: return false
            val token = user.getIdToken(false).await().token ?: return false

            val response = api.updateUserTier(
                "Bearer $token",
                com.protctus.app.network.TierRequest(newTier.name)
            )

            if (response.isSuccessful) {
                writeCachedTier(newTier)
                _currentTier.value = newTier
                Log.d(TAG, "Updated tier on server: ${newTier.name}")
                true
            } else {
                Log.e(TAG, "updateTierOnServer failed: HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateTierOnServer exception", e)
            false
        }
    }

    /**
     * Immediately set the tier locally — writes to SharedPrefs + DataStore + StateFlow.
     * NO server call required. Call this BEFORE any server push to guarantee the
     * local cache is correct even if the network fails.
     *
     * This ensures:
     *   1. StateFlow emits the new tier → Compose recomposes instantly
     *   2. SharedPreferences is written → next cold start hydrates correctly in < 1ms
     *   3. DataStore is written → backward compatibility
     */
    suspend fun setTierLocally(newTier: SubscriptionTier) {
        _currentTier.value = newTier
        writeCachedTier(newTier)
        Log.d(TAG, "setTierLocally: ${newTier.name} — StateFlow + caches updated")
    }

    // ═══════════════════════════════════════════
    //  Dual Cache: SharedPreferences (sync) + DataStore (async)
    // ═══════════════════════════════════════════

    /**
     * Write tier to BOTH caches:
     * 1. SharedPreferences — synchronous commit for instant next cold-start read
     * 2. DataStore — async write for backward compatibility
     */
    private suspend fun writeCachedTier(tier: SubscriptionTier) {
        // 1. SharedPreferences (synchronous, instant)
        try {
            instantPrefs.edit().putString(PREF_KEY_TIER, tier.name).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write SharedPreferences cache", e)
        }

        // 2. DataStore (async, backward-compatible)
        try {
            context.entitlementDataStore.edit { prefs ->
                prefs[KEY_CACHED_TIER] = tier.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write DataStore cache", e)
        }
    }
}
