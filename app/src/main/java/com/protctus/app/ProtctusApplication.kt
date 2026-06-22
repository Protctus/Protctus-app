package com.protctus.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.protctus.app.engine.SignatureDatabase
import com.protctus.app.security.SignatureVerifier
import com.protctus.app.engine.VVIPAllowlist
import com.protctus.app.services.ApkDownloadObserver
import com.protctus.app.worker.DailyNotificationWorker
import com.protctus.app.utils.GlobalQuestTracker
import com.protctus.app.utils.QuestTracker
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import javax.inject.Inject

@HiltAndroidApp
class ProtctusApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var globalQuestTracker: GlobalQuestTracker

    private var apkObserver: ApkDownloadObserver? = null

    override fun onCreate() {
        super.onCreate()
        // Repackaging Defense
        SignatureVerifier.verifySignature(this)
        
        initNotificationChannels()
        SignatureDatabase.initialize(this)
        VVIPAllowlist.init(this) // Load Tranco Top-10K VVIP allowlist into memory
        
        // Bridge legacy QuestTracker (SharedPrefs) → GlobalQuestTracker (Event Bus)
        QuestTracker.init(globalQuestTracker)
        
        // Start watching for Mod APK downloads globally
        apkObserver = ApkDownloadObserver(this)
        apkObserver?.startWatching()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // Use up to 5% of available disk space
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                DailyNotificationWorker.CHANNEL_ID,
                DailyNotificationWorker.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily security alerts and quest reminders from Protctus"
            }

            val sentinelChannel = NotificationChannel(
                "SENTINEL_CHANNEL",
                "Real-Time Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs background heuristics when new apps are installed."
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(sentinelChannel)
        }
    }
}
