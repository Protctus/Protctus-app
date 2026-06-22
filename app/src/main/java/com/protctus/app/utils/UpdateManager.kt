package com.protctus.app.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class AppVersionResponse(
    val version_code: Int,
    val version_name: String? = null,
    val download_url: String,
    val release_notes: String? = null,
    val is_critical: Int = 0
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val apiUrl = "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev/app/version"

    suspend fun checkForUpdates(
        showToastIfUpToDate: Boolean = false,
        onResult: (UpdateCheckResult) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(apiUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) { onResult(UpdateCheckResult.Error("Server error")) }
                        return@withContext
                    }
                    val body = response.body?.string() ?: return@withContext
                    val versionInfo = json.decodeFromString<AppVersionResponse>(body)
                    
                    val currentVersionCode = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
                        }
                    } catch (e: Exception) {
                        0L
                    }

                    withContext(Dispatchers.Main) {
                        if (versionInfo.version_code > currentVersionCode && versionInfo.download_url.isNotBlank()) {
                            onResult(UpdateCheckResult.Available(versionInfo))
                        } else {
                            if (showToastIfUpToDate) {
                                Toast.makeText(context, "Protctus is already up to date!", Toast.LENGTH_SHORT).show()
                            }
                            onResult(UpdateCheckResult.UpToDate)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(UpdateCheckResult.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    sealed class UpdateCheckResult {
        data class Available(val info: AppVersionResponse) : UpdateCheckResult()
        object UpToDate : UpdateCheckResult()
        data class Error(val message: String) : UpdateCheckResult()
    }

    fun downloadAndInstall(url: String, versionName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        
        val request = DownloadManager.Request(uri)
            .setTitle("Protctus Update")
            .setDescription("Downloading version $versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "protctus_update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    val uri = downloadManager.getUriForDownloadedFile(id)
                    if (uri != null) {
                        installApk(uri)
                    } else {
                        Toast.makeText(context, "Update download failed.", Toast.LENGTH_SHORT).show()
                    }
                    context.unregisterReceiver(this)
                }
            }
        }
        
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
    }

    private fun installApk(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to open APK. Please install manually from Downloads.", Toast.LENGTH_LONG).show()
        }
    }
}
