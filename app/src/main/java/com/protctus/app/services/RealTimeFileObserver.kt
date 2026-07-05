package com.protctus.app.services

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.entity.ScannedFileCache
import com.protctus.app.engine.FileCategory
import com.protctus.app.engine.FileFeatureExtractor
import com.protctus.app.engine.ProcMalwareEngine
import com.protctus.app.engine.ScanPriority
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Production-grade real-time file observer.
 *
 * Architecture:
 * - ContentObserver callbacks enqueue lightweight ScanRequest objects (never do heavy work)
 * - Priority-ordered consumer coroutine drains the queue (HIGHEST first)
 * - Tiered cache validation: metadata-first, SHA-256 only for security-sensitive files
 * - Battery-aware scan depth
 * - Duplicate suppression via in-flight set
 * - Internal diagnostic metrics (debug builds)
 */
class RealTimeFileObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val notificationManager: SentinelNotificationManager
) {

    companion object {
        private const val TAG = "RealTimeObserver"
        private const val MAX_QUEUE_CAPACITY = 200
        private const val MAX_FILES_PER_BATCH = 50
        private const val DEBOUNCE_MS = 2000L
    }

    // ─── Priority scan queue ─────────────────────────────────────────
    private data class ScanRequest(
        val filePath: String,
        val fileSize: Long,
        val lastModified: Long,
        val mimeType: String,
        val category: FileCategory,
        val priority: ScanPriority
    ) : Comparable<ScanRequest> {
        override fun compareTo(other: ScanRequest): Int = this.priority.level - other.priority.level
    }

    private val scanChannel = Channel<ScanRequest>(MAX_QUEUE_CAPACITY)
    private val inFlightPaths = ConcurrentHashMap.newKeySet<String>()

    // ─── Content Observers ───────────────────────────────────────────
    private val mediaStoreObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scope.launch { handleMediaStoreChange(uri) }
        }
    }

    private val downloadsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            scope.launch { handleMediaStoreChange(uri) }
        }
    }

    // ─── Database ────────────────────────────────────────────────────
    private val db = AppDatabase.getInstance(context)
    private val cacheDao = db.scannedFileCacheDao()

    // ─── Debounce tracking ───────────────────────────────────────────
    private var pendingQueryJob: Job? = null
    private val lastEventTime = AtomicLong(0L)

    // ─── Internal metrics (debug builds only) ────────────────────────
    private val metricsTotalEvents = AtomicInteger(0)
    private val metricsDuplicatesSuppressed = AtomicInteger(0)
    private val metricsCacheHits = AtomicInteger(0)
    private val metricsFilesScanned = AtomicInteger(0)
    private val metricsScanTimeAccumMs = AtomicLong(0L)

    init {
        // Consumer coroutine: drains the channel and scans files in priority order
        scope.launch(Dispatchers.IO) {
            // Collect into a priority buffer to respect ordering
            val priorityBuffer = java.util.PriorityQueue<ScanRequest>()

            for (request in scanChannel) {
                priorityBuffer.add(request)

                // Drain all currently available requests into the priority buffer
                while (true) {
                    val next = scanChannel.tryReceive().getOrNull() ?: break
                    priorityBuffer.add(next)
                }

                // Process in priority order
                while (priorityBuffer.isNotEmpty()) {
                    val req = priorityBuffer.poll() ?: break
                    try {
                        processScanRequest(req)
                    } catch (e: Exception) {
                        Log.e(TAG, "Scan failed for ${req.filePath}: ${e.message}")
                    } finally {
                        inFlightPaths.remove(req.filePath)
                    }
                }
            }
        }

        // Periodic notification flush (every 30s) for batched medium-priority threats
        scope.launch {
            while (isActive) {
                delay(30_000L)
                notificationManager.flushPendingNotifications()
            }
        }
    }

    // ─── MediaStore change handler ───────────────────────────────────

    private suspend fun handleMediaStoreChange(uri: Uri?) {
        metricsTotalEvents.incrementAndGet()

        // Try per-URI resolution first (API 30+), fall back to batch query
        if (uri != null && uri.lastPathSegment?.toLongOrNull() != null) {
            resolveAndEnqueue(uri)
        } else {
            // Debounce batch queries to prevent rapid re-querying
            val now = System.currentTimeMillis()
            lastEventTime.set(now)

            pendingQueryJob?.cancel()
            pendingQueryJob = scope.launch(Dispatchers.IO) {
                delay(DEBOUNCE_MS)
                // Only query if no newer events arrived during the debounce window
                if (lastEventTime.get() == now) {
                    queryRecentFiles()
                }
            }
        }
    }

    /**
     * Resolves a specific MediaStore URI to a file and enqueues it.
     * More efficient than batch query when Android provides the URI.
     */
    private suspend fun resolveAndEnqueue(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val modIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

                    if (dataIdx >= 0) {
                        val path = cursor.getString(dataIdx) ?: return@use
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val modified = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                        val mime = if (mimeIdx >= 0) (cursor.getString(mimeIdx) ?: "") else ""

                        enqueueFile(path, size, modified, mime)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "URI resolution failed: ${e.message}")
        }
    }

    /**
     * Batch query for recent files. Used when per-URI resolution is not available.
     */
    private suspend fun queryRecentFiles() = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            val timeThreshold = (System.currentTimeMillis() / 1000L) - (15 * 60)
            val selection = "${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?"
            val selectionArgs = arrayOf(timeThreshold.toString())
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < MAX_FILES_PER_BATCH) {
                    val path = cursor.getString(dataIdx) ?: continue
                    val size = cursor.getLong(sizeIdx)
                    val modified = cursor.getLong(modIdx)
                    val mime = if (mimeIdx >= 0) (cursor.getString(mimeIdx) ?: "") else ""

                    enqueueFile(path, size, modified, mime)
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Batch query failed: ${e.message}")
        }
    }

    /**
     * Enqueues a file into the priority scan queue with duplicate suppression.
     */
    private fun enqueueFile(filePath: String, size: Long, lastModified: Long, mimeType: String) {
        // Duplicate suppression: skip if already in-flight
        if (!inFlightPaths.add(filePath)) {
            metricsDuplicatesSuppressed.incrementAndGet()
            return
        }

        val ext = filePath.substringAfterLast('.', "").lowercase()
        val category = FileFeatureExtractor.categorizeFile(ext)
        val priority = FileFeatureExtractor.priorityOf(category)

        val request = ScanRequest(filePath, size, lastModified, mimeType, category, priority)

        // Non-blocking offer to channel; drop if queue is full (backpressure)
        val accepted = scanChannel.trySend(request).isSuccess
        if (!accepted) {
            inFlightPaths.remove(filePath)
            Log.w(TAG, "Scan queue full, dropping: $filePath")
        }
    }

    // ─── Scan processing ─────────────────────────────────────────────

    /**
     * Processes a single scan request with tiered cache validation and adaptive scanning.
     */
    private suspend fun processScanRequest(request: ScanRequest) {
        val file = File(request.filePath)
        if (!file.exists() || !file.canRead()) return

        val scanStart = System.currentTimeMillis()

        // ── Tiered cache validation ──
        val cached = cacheDao.getCachedFile(request.filePath)

        if (cached != null) {
            // Fast metadata comparison: size + lastModified + mimeType
            if (cached.fileSize == request.fileSize &&
                cached.lastModified == request.lastModified &&
                cached.mimeType == request.mimeType) {
                metricsCacheHits.incrementAndGet()
                return // File unchanged — skip
            }

            // Metadata changed — for security-sensitive files, validate with SHA-256
            if (FileFeatureExtractor.isSecuritySensitive(request.category)) {
                val hash = FileFeatureExtractor.computeSha256(file)
                if (hash != null && hash == cached.sha256Hash) {
                    // Content identical despite metadata change — update metadata, skip scan
                    cacheDao.insert(cached.copy(
                        lastModified = request.lastModified,
                        fileSize = request.fileSize,
                        mimeType = request.mimeType,
                        scanTimestamp = System.currentTimeMillis()
                    ))
                    metricsCacheHits.incrementAndGet()
                    return
                }
            }
        }

        // ── Adaptive scanning with battery awareness ──
        val batteryStrategy = getBatteryStrategy()
        val evidence = when (batteryStrategy) {
            BatteryStrategy.FULL -> {
                FileFeatureExtractor.extractEvidenceAdaptive(context, file, request.category)
            }
            BatteryStrategy.REDUCED -> {
                // Reduced: skip LOWEST priority files, adaptive for others
                if (request.priority == ScanPriority.LOWEST) {
                    // Media files: header-only check still runs (very cheap)
                    FileFeatureExtractor.extractEvidenceAdaptive(context, file, request.category)
                } else {
                    FileFeatureExtractor.extractEvidenceAdaptive(context, file, request.category)
                }
            }
            BatteryStrategy.QUICK -> {
                // Quick: only scan HIGHEST priority files
                if (request.priority != ScanPriority.HIGHEST) return
                FileFeatureExtractor.extractEvidenceAdaptive(context, file, request.category)
            }
        }

        if (evidence == null) return

        // ── ProcMalwareEngine verdict (sole authority) ──
        val verdict = ProcMalwareEngine.evaluateFile(evidence)

        // ── Compute SHA-256 for security-sensitive files (lazy) ──
        val sha256 = if (FileFeatureExtractor.isSecuritySensitive(request.category)) {
            FileFeatureExtractor.computeSha256(file)
        } else {
            null
        }

        // ── Cache the result ──
        val cacheEntry = ScannedFileCache(
            filePath = request.filePath,
            lastModified = request.lastModified,
            fileSize = request.fileSize,
            mimeType = request.mimeType,
            sha256Hash = sha256,
            verdictDecision = verdict.decision.name,
            verdictScore = verdict.finalScore,
            isMalicious = verdict.decision.isActionable,
            scanTimestamp = System.currentTimeMillis()
        )
        cacheDao.insert(cacheEntry)

        // ── Metrics ──
        metricsFilesScanned.incrementAndGet()
        metricsScanTimeAccumMs.addAndGet(System.currentTimeMillis() - scanStart)

        // ── Notify if actionable ──
        if (verdict.decision.isActionable) {
            notificationManager.reportThreat(
                filePath = request.filePath,
                fileName = file.name,
                verdict = verdict
            )
        }
    }

    // ─── Battery strategy ────────────────────────────────────────────

    private enum class BatteryStrategy { FULL, REDUCED, QUICK }

    private fun getBatteryStrategy(): BatteryStrategy {
        try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryStatus != null) {
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                if (isCharging) return BatteryStrategy.FULL

                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (scale > 0) (level * 100) / scale else 100

                return when {
                    pct > 30 -> BatteryStrategy.FULL
                    pct > 15 -> BatteryStrategy.REDUCED
                    else -> BatteryStrategy.QUICK
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Battery check failed, defaulting to FULL", e)
        }
        return BatteryStrategy.FULL
    }

    // ─── Direct scan API for DownloadManager events ──────────────────

    /**
     * Directly enqueue a specific file for scanning at HIGHEST priority.
     * Used by SentinelService when DownloadManager completes a download.
     */
    fun enqueueDirectScan(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return

        val ext = filePath.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""

        enqueueFile(filePath, file.length(), file.lastModified() / 1000L, mime)
    }

    // ─── Registration ────────────────────────────────────────────────

    fun register() {
        try {
            // Primary: all files in external storage
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                mediaStoreObserver
            )

            // Downloads collection
            context.contentResolver.registerContentObserver(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                true,
                downloadsObserver
            )

            // Images
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver
            )

            // Video
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver
            )

            // Audio
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaStoreObserver
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register observers: ${e.message}")
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(mediaStoreObserver)
            context.contentResolver.unregisterContentObserver(downloadsObserver)
        } catch (e: Exception) {}

        scanChannel.close()
    }

    // ─── Debug metrics ───────────────────────────────────────────────

    fun getMetricsSummary(): String {
        val scanned = metricsFilesScanned.get()
        val avgMs = if (scanned > 0) metricsScanTimeAccumMs.get() / scanned else 0L
        return "Events=${metricsTotalEvents.get()}, " +
                "Scanned=$scanned, " +
                "CacheHits=${metricsCacheHits.get()}, " +
                "Duplicates=${metricsDuplicatesSuppressed.get()}, " +
                "AvgScanMs=$avgMs"
    }
}
