package com.protctus.app.engine

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

object FileScanner {

    // Hypatia-Derived Concurrent Scanner 
    // Uses structural coroutines instead of AsyncTask for memory efficiency
    suspend fun executeScan(
        onProgress: (Int, Float, String) -> Unit, 
        discoveredThreats: MutableList<ScanThreat>
    ): Int = withContext(Dispatchers.IO) {
        
        var filesScanned = 0
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (downloadsDir == null || !downloadsDir.exists() || !downloadsDir.isDirectory) {
            return@withContext filesScanned
        }

        val filesToScan = try {
            downloadsDir.walkTopDown().filter { it.isFile }.toList()
        } catch (e: SecurityException) {
            emptyList<File>()
        }
        
        val totalFiles = filesToScan.size
        
        // Chunk sizes for high performance coroutine batching
        val chunkedFiles = filesToScan.chunked(8) 
        
        chunkedFiles.forEachIndexed { chunkIndex, chunk ->
            // High-speed parallel hashing & structural profiling
            val scanJobs = chunk.map { file ->
                async {
                    // Hypatia Hash Calculation
                    val fileHashes = SignatureDatabase.calculateConcurrentHashes(file)
                    
                    if (fileHashes != null && SignatureDatabase.verifySigsAgainstBloomFilter(fileHashes)) {
                        synchronized(discoveredThreats) {
                            discoveredThreats.add(
                                ScanThreat(
                                    appName = file.name,
                                    packageName = file.absolutePath,
                                    threatType = "Known YARA Signature",
                                    riskScore = "100/100",
                                    detailedReason = "Cryptographic collision with known malware signature (ClamAV Database)."
                                )
                            )
                        }
                    } else {
                        // No hash match — file is clean by signature analysis
                        // ThreatBrain REMOVED — no static heuristic fallback
                    }
                    file.name
                }
            }
            
            // Wait for all 8 files in this chunk to finish parsing
            val evaluatedNames = scanJobs.awaitAll()
            
            filesScanned += chunk.size
            val currentProgress = 50f + ((filesScanned.toFloat() / totalFiles.toFloat()) * 40f)
            
            // Emit progress UI safely across coroutines
            onProgress(filesScanned, currentProgress, evaluatedNames.lastOrNull() ?: "")
        }

        filesScanned
    }
}
