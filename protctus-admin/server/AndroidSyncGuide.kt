// ═══════════════════════════════════════════════════════════════════
// AndroidSyncGuide.kt — Protctus Locale Sync Client (Reference)
// ═══════════════════════════════════════════════════════════════════
//
// This is a REFERENCE implementation — not compiled as part of the
// Node.js server. Copy this into your Android project:
//
//   com/protctus/app/sync/LocaleSyncManager.kt
//
// Dependencies required in app/build.gradle.kts:
//   implementation("com.squareup.okhttp3:okhttp:4.12.0")
//   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
//   implementation("com.google.code.gson:gson:2.10.1")
//
// ═══════════════════════════════════════════════════════════════════

/*

package com.protctus.app.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════
// DATA MODELS
// ══════════════════════════════════════════════

/**
 * Mirrors the server-generated manifest.json schema.
 */
data class SyncManifest(
    @SerializedName("version_code") val versionCode: Long,
    @SerializedName("generated_at") val generatedAt: String,
    @SerializedName("languages") val languages: List<String>,
    @SerializedName("files") val files: Map<String, LanguageFiles>
)

data class LanguageFiles(
    @SerializedName("quests") val quests: String,
    @SerializedName("lab") val lab: String,
    @SerializedName("notifications") val notifications: String
)

// ══════════════════════════════════════════════
// LOCALE SYNC MANAGER
// ══════════════════════════════════════════════

/**
 * Handles delta-based locale synchronization for the Protctus app.
 *
 * Workflow:
 *   1. Fetch manifest.json from server
 *   2. Compare version_code against locally cached version
 *   3. If newer → download all JSON files for the user's active language
 *   4. Save to context.filesDir/locales/
 *   5. Delete old files only after successful download
 *   6. Update cached version_code in SharedPreferences
 *
 * Usage:
 *   val syncManager = LocaleSyncManager(context)
 *   syncManager.syncIfNeeded("hi") // Sync Hindi locale files
 */
class LocaleSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "LocaleSyncManager"

        // ── Replace this with your actual server URL ──
        // If serving from protctus-admin/public/locales/:
        private const val BASE_URL = "https://your-server.com/locales"

        // SharedPreferences keys
        private const val PREFS_NAME = "protctus_sync_prefs"
        private const val KEY_VERSION_CODE = "locale_version_code"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"

        // Local storage subdirectory
        private const val LOCALES_DIR = "locales"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Local Version Tracking ──

    private fun getCachedVersionCode(): Long =
        prefs.getLong(KEY_VERSION_CODE, 0L)

    private fun setCachedVersionCode(version: Long) {
        prefs.edit()
            .putLong(KEY_VERSION_CODE, version)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    // ── Locales Directory ──

    private fun getLocalesDir(): File {
        val dir = File(context.filesDir, LOCALES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ══════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════

    /**
     * Main entry point — checks manifest and syncs if newer version available.
     *
     * @param languageCode ISO 639-1 code (e.g., "hi", "kn", "bn")
     * @return true if sync was performed, false if already up-to-date
     */
    suspend fun syncIfNeeded(languageCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for locale updates...")

            // Step 1: Fetch remote manifest
            val manifest = fetchManifest() ?: run {
                Log.w(TAG, "Failed to fetch manifest — skipping sync")
                return@withContext false
            }

            // Step 2: Compare versions
            val cachedVersion = getCachedVersionCode()
            Log.d(TAG, "Local version: $cachedVersion, Remote version: ${manifest.versionCode}")

            if (manifest.versionCode <= cachedVersion) {
                Log.d(TAG, "Already up-to-date — no sync needed")
                return@withContext false
            }

            // Step 3: Check if requested language is available
            val langFiles = manifest.files[languageCode]
            if (langFiles == null) {
                Log.w(TAG, "Language '$languageCode' not available in manifest")
                return@withContext false
            }

            // Step 4: Download all files for this language
            Log.d(TAG, "New version available! Downloading $languageCode locale files...")

            val localesDir = getLocalesDir()
            val tempDir = File(context.cacheDir, "locale_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                // Download to temp dir first (atomic swap)
                downloadFile(langFiles.quests, tempDir)
                downloadFile(langFiles.lab, tempDir)
                downloadFile(langFiles.notifications, tempDir)

                // Also download English base (fallback)
                manifest.files["en"]?.let { enFiles ->
                    downloadFile(enFiles.quests, tempDir)
                    downloadFile(enFiles.lab, tempDir)
                    downloadFile(enFiles.notifications, tempDir)
                }

                // Step 5: Delete old files and move new ones in
                deleteOldLocaleFiles(localesDir)
                moveFiles(tempDir, localesDir)

                // Step 6: Update cached version
                setCachedVersionCode(manifest.versionCode)

                Log.d(TAG, "✅ Sync complete! Version: ${manifest.versionCode}")
                return@withContext true

            } catch (e: Exception) {
                // Cleanup temp on failure — don't corrupt existing files
                tempDir.deleteRecursively()
                Log.e(TAG, "Sync download failed — keeping existing files", e)
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Sync check failed", e)
            return@withContext false
        }
    }

    /**
     * Forces a full sync regardless of version check.
     */
    suspend fun forceSync(languageCode: String): Boolean {
        // Reset version to force re-download
        setCachedVersionCode(0L)
        return syncIfNeeded(languageCode)
    }

    /**
     * Reads a downloaded locale JSON file as a raw string.
     *
     * @param fileName e.g., "lab_hi.json"
     * @return JSON string or null if file doesn't exist
     */
    fun readLocaleFile(fileName: String): String? {
        val file = File(getLocalesDir(), fileName)
        return if (file.exists()) file.readText() else null
    }

    /**
     * Checks if locale files exist locally for the given language.
     */
    fun hasLocalFiles(languageCode: String): Boolean {
        val dir = getLocalesDir()
        return File(dir, "lab_${languageCode}.json").exists() &&
               File(dir, "quest_${languageCode}.json").exists()
    }

    // ══════════════════════════════════════════════
    // INTERNAL HELPERS
    // ══════════════════════════════════════════════

    private fun fetchManifest(): SyncManifest? {
        val request = Request.Builder()
            .url("$BASE_URL/manifest.json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Manifest fetch failed: HTTP ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            gson.fromJson(body, SyncManifest::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Manifest fetch error: ${e.message}")
            null
        }
    }

    private fun downloadFile(fileName: String, targetDir: File) {
        val request = Request.Builder()
            .url("$BASE_URL/$fileName")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Download failed for $fileName: HTTP ${response.code}")
        }

        val targetFile = File(targetDir, fileName)
        FileOutputStream(targetFile).use { fos ->
            response.body?.byteStream()?.copyTo(fos)
                ?: throw RuntimeException("Empty response body for $fileName")
        }

        Log.d(TAG, "  Downloaded: $fileName (${targetFile.length()} bytes)")
    }

    private fun deleteOldLocaleFiles(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension == "json") {
                file.delete()
                Log.d(TAG, "  Deleted old: ${file.name}")
            }
        }
    }

    private fun moveFiles(from: File, to: File) {
        from.listFiles()?.forEach { file ->
            val target = File(to, file.name)
            file.copyTo(target, overwrite = true)
            file.delete()
        }
        from.delete()
    }
}

// ══════════════════════════════════════════════
// USAGE EXAMPLE — ViewModel Integration
// ══════════════════════════════════════════════

/*
// In your ViewModel or Repository:

class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val syncManager = LocaleSyncManager(context)

    init {
        // Check for locale updates on app launch
        viewModelScope.launch {
            val userLang = getUserLanguage() // "hi", "kn", etc.
            val synced = syncManager.syncIfNeeded(userLang)
            if (synced) {
                Log.d("HomeVM", "Locale files updated — refreshing UI")
                refreshLocalizedContent(userLang)
            }
        }
    }

    private fun refreshLocalizedContent(lang: String) {
        val labJson = syncManager.readLocaleFile("lab_${lang}.json")
        val questJson = syncManager.readLocaleFile("quest_${lang}.json")

        // Parse and update LiveData / StateFlow...
        labJson?.let {
            val chapters = Gson().fromJson(it, Array<LabChapter>::class.java)
            _labChapters.value = chapters.toList()
        }
    }
}
*/

*/
