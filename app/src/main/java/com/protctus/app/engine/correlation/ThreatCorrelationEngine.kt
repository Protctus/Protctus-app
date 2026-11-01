package com.protctus.app.engine.correlation

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.concurrent.CopyOnWriteArrayList
import com.protctus.app.engine.quark.QuarkEngine
import com.protctus.app.engine.quark.ThreatLevel
import com.protctus.app.engine.clam.ClamSignatureEngine
import com.protctus.app.engine.ml.FeatureVectorMalwareEngine
import com.protctus.app.engine.EngineVerdict
import com.protctus.app.engine.androguard.AndroguardEngine
import com.protctus.app.engine.androguard.RiskLevel as AndroguardRiskLevel
import com.protctus.yaracore.YaraScanner
import java.io.File


class ThreatCorrelationEngine private constructor(private val context: Context) {

    private val quarkEngine = QuarkEngine.getInstance(context)
    private val clamEngine = ClamSignatureEngine.getInstance(context)
    private val androEngine = AndroguardEngine.getInstance(context)
    /** Optional additive engine: scores a 68-feature vector (Droidware-compatible). */
    private val featureVecEngine = FeatureVectorMalwareEngine.getInstance(context)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try { quarkEngine.initialize() } catch (e: Exception) { Log.e(TAG, "Quark init failed", e) }
        try { clamEngine.initialize() } catch (e: Exception) { Log.e(TAG, "ClamAV init failed", e) }
        // Feature-vector engine loads its model asset synchronously in its constructor;
        // no async init needed. Log readiness.
        if (!featureVecEngine.isReady) {
            Log.w(TAG, "FeatureVectorMalwareEngine not ready (model asset missing) — limited visibility.")
        }
    }

    /**
     * Standalone evaluation of a precomputed feature vector (e.g., 68 features extracted from
     * function call graphs, permissions, and Java source — Droidware-compatible).
     *
     * Callers are responsible for extracting and normalizing the 68 features to match training.
     * Returns an [EngineVerdict]; if the model asset is missing, returns LOW_RISK with
     * zero confidence (limited visibility — never a guess).
     */
    fun evaluateFeatureVector(features: FloatArray): EngineVerdict =
        featureVecEngine.evaluate(features)


    /**
     * Correlate Smart Scan: Non-blocking, fast, surface-level analysis.
     */
    suspend fun correlateSmartScan(pm: PackageManager, packageInfo: PackageInfo): CorrelatedVerdict = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val packageName = packageInfo.packageName
        val appLabel = getAppLabel(pm, packageInfo)
        
        if (packageName == context.packageName) {
            return@withContext buildSafeVerdict(packageName, appLabel, startTime)
        }

        val expl = ConcurrentExplanation()
        
        // Asynchronous Concurrent Engine Execution
        val quarkDeferred = async { runQuarkSmart(pm, packageInfo, expl) }
        val clamDeferred = async { runClamHash(packageInfo, expl) }
        val androDeferred = async { runAndroguardSmart(pm, packageInfo, expl) }

        val signals = mutableListOf<EvidenceSignal>()
        signals.addAll(quarkDeferred.await())
        signals.addAll(clamDeferred.await())
        signals.addAll(androDeferred.await())
        
        // Origin Context Module (Sideloaded vs Store)
        signals.addAll(evaluateOriginContext(pm, packageName, expl))

        val verdict = ConsensusCorrelationModule.evaluate(signals, expl)

        CorrelatedVerdict(
            packageName = packageName,
            appLabel = appLabel,
            finalVerdict = verdict.first,
            finalProbability = verdict.second,
            explanation = expl.build(),
            signals = signals,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Correlate Deep Scan: Exhaustive, asynchronous, comprehensive analysis.
     */
    suspend fun correlateDeepScan(
        pm: PackageManager, packageInfo: PackageInfo,
        config: AdaptiveScanConfig
    ): CorrelatedVerdict = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val packageName = packageInfo.packageName
        val appLabel = getAppLabel(pm, packageInfo)
        
        if (packageName == context.packageName) {
            return@withContext buildSafeVerdict(packageName, appLabel, startTime)
        }

        val expl = ConcurrentExplanation()

        // Concurrent Heavy Parsing
        val deferredSignals = listOf(
            async { if (config.enableQuarkDeepAnalysis) runQuarkDeep(pm, packageInfo, expl) else runQuarkSmart(pm, packageInfo, expl) },
            async { if (config.enableBytePatternScan) runClamFull(packageInfo, config, expl) else emptyList() },
            async { if (config.enableBytePatternScan) runYaraScan(packageInfo, expl) else emptyList() },
            async { if (config.enableDeepDexInspection) runAndroguardDeep(pm, packageInfo, expl) else runAndroguardSmart(pm, packageInfo, expl) }
        )

        val signals = deferredSignals.awaitAll().flatten().toMutableList()
        signals.addAll(evaluateOriginContext(pm, packageName, expl))

        val verdict = ConsensusCorrelationModule.evaluate(signals, expl)

        CorrelatedVerdict(
            packageName = packageName,
            appLabel = appLabel,
            finalVerdict = verdict.first,
            finalProbability = verdict.second,
            explanation = expl.build(),
            signals = signals,
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    // ═══════════════════════════════════════════
    // ASYNCHRONOUS ENGINE RUNNERS
    // ═══════════════════════════════════════════

    private suspend fun runQuarkSmart(pm: PackageManager, pkg: PackageInfo, expl: ConcurrentExplanation): List<EvidenceSignal> = withContext(Dispatchers.Default) {
        if (!quarkEngine.ready) return@withContext listOf(buildErrorSignal("Quark"))
        try {
            val result = quarkEngine.smartScanPackage(pm, pkg)
            val conf = (result.overallThreatLevel.numericScore / 100f).coerceIn(0f, 1f)
            if (conf > 0f) {
                result.topThreats.forEach { expl.behavioral.add(it.rule.crime) }
                // BEHAVIORAL CALIBRATION: Static Behavior is LOW certainty by default
                listOf(EvidenceSignal("Quark", EvidenceCategory.BEHAVIORAL_DYNAMIC, EvidenceCluster.UI_ABUSE, conf, SeverityBand.MEDIUM, CertaintyLevel.LOW, result.topThreats.map { it.rule.crime }, VisibilityLevel.FULL))
            } else emptyList()
        } catch (e: Exception) { 
            Log.e(TAG, "QuarkSmart Failed", e)
            listOf(buildErrorSignal("Quark", e.message)) 
        }
    }

    private suspend fun runQuarkDeep(pm: PackageManager, pkg: PackageInfo, expl: ConcurrentExplanation): List<EvidenceSignal> = withContext(Dispatchers.Default) {
        if (!quarkEngine.ready) return@withContext listOf(buildErrorSignal("Quark-Deep"))
        try {
            val result = quarkEngine.deepScanPackage(pm, pkg)
            val conf = (result.overallThreatLevel.numericScore / 100f).coerceIn(0f, 1f)
            val signals = mutableListOf<EvidenceSignal>()
            if (conf > 0f) {
                result.topThreats.forEach { expl.behavioral.add(it.rule.crime) }
                val cluster = if (result.topThreats.any { it.rule.crime.contains("ClassLoader", true) || it.rule.crime.contains("Dex", true) }) EvidenceCluster.DYNAMIC_STEALTH else EvidenceCluster.UI_ABUSE
                // BEHAVIORAL CALIBRATION: Deep Scan is MEDIUM certainty
                signals.add(EvidenceSignal("Quark-Deep", EvidenceCategory.BEHAVIORAL_DYNAMIC, cluster, conf, if (conf > 0.8f) SeverityBand.HIGH else SeverityBand.MEDIUM, CertaintyLevel.MEDIUM, result.topThreats.map { it.rule.crime }, VisibilityLevel.FULL))
            }
            signals
        } catch (e: Exception) { 
            Log.e(TAG, "QuarkDeep Failed", e)
            listOf(buildErrorSignal("Quark-Deep", e.message)) 
        }
    }

    private suspend fun runClamHash(pkg: PackageInfo, expl: ConcurrentExplanation): List<EvidenceSignal> = withContext(Dispatchers.IO) {
        if (!clamEngine.ready) return@withContext listOf(buildErrorSignal("ClamAV"))
        val apkPath = pkg.applicationInfo?.publicSourceDir ?: return@withContext emptyList()
        try {
            val match = clamEngine.scanFileHash(File(apkPath))
            if (match != null) {
                expl.signatures.add("${match.malwareName} (${match.matchType})")
                // SIGNATURE CALIBRATION: Exact Hash is DEFINITIVE
                listOf(EvidenceSignal("ClamAV", EvidenceCategory.SIGNATURE_EXACT, EvidenceCluster.THREAT_SIGNATURE, 1.0f, SeverityBand.CRITICAL, CertaintyLevel.DEFINITIVE, listOf(match.malwareName), VisibilityLevel.FULL))
            } else emptyList()
        } catch (e: Exception) { 
            Log.e(TAG, "ClamHash Failed", e)
            listOf(buildErrorSignal("ClamAV", e.message)) 
        }
    }

    private suspend fun runClamFull(pkg: PackageInfo, config: AdaptiveScanConfig, expl: ConcurrentExplanation): List<EvidenceSignal> = withContext(Dispatchers.IO) {
        if (!clamEngine.ready) return@withContext emptyList()
        val apkPath = pkg.applicationInfo?.publicSourceDir ?: return@withContext emptyList()
        try {
            val results = clamEngine.fullScan(File(apkPath), config.maxPatternScanBytes)
            if (results.isNotEmpty()) {
                results.forEach { expl.signatures.add("${it.malwareName} (${it.matchType})") }
                val topMatch = results.maxByOrNull { it.confidence }!!
                // SIGNATURE CALIBRATION: Heuristic Byte Pattern is MEDIUM certainty
                listOf(EvidenceSignal("ClamAV-Full", EvidenceCategory.SIGNATURE_HEURISTIC, EvidenceCluster.THREAT_SIGNATURE, topMatch.confidence, SeverityBand.HIGH, CertaintyLevel.MEDIUM, listOf(topMatch.malwareName), VisibilityLevel.FULL))
            } else emptyList()
        } catch (e: Exception) { 
            Log.e(TAG, "ClamFull Failed", e)
            emptyList() 
        }
    }

    private suspend fun runYaraScan(pkg: PackageInfo, expl: ConcurrentExplanation): List<EvidenceSignal> = withContext(Dispatchers.IO) {
        val apkPath = pkg.applicationInfo?.publicSourceDir ?: return@withContext emptyList()
        try {
            // YARA IS FULLY ASYNC SAFE NO RUNBLOCKING
            val matches = YaraScanner.scanApkInMemory(context, apkPath)
            if (matches.isNotEmpty()) {
                matches.forEach { expl.signatures.add("YARA: $it") }
                // SIGNATURE CALIBRATION: Generic YARA is MEDIUM certainty
                listOf(EvidenceSignal("YARA", EvidenceCategory.SIGNATURE_HEURISTIC, EvidenceCluster.THREAT_SIGNATURE, 0.75f, SeverityBand.MEDIUM, CertaintyLevel.MEDIUM, matches, VisibilityLevel.FULL))
            } else emptyList()
        } catch (e: Exception) { 
            Log.e(TAG, "YaraScan Failed", e)
            emptyList() 
        }
    }

    private suspend fun runAndroguardSmart(pm: PackageManager, pkg: PackageInfo, expl: ConcurrentExplanation): List<EvidenceSignal> = withContext(Dispatchers.Default) {
        try {
            val result = androEngine.smartScanPackage(pm, pkg)
            val conf = (result.overallRiskScore / 100f).coerceIn(0f, 1f)
            if (conf > 0f) {
                result.manifestFindings.dangerousPermissions.forEach { expl.permissions.add(it.substringAfterLast('.')) }
                result.criticalFindings.forEach { expl.structural.add(it.title) }
                // MANIFEST CALIBRATION: Low-Medium certainty
                listOf(EvidenceSignal("Androguard", EvidenceCategory.STRUCTURAL_ANOMALY, EvidenceCluster.STRUCTURAL_ANOMALY, conf, SeverityBand.MEDIUM, CertaintyLevel.LOW, result.criticalFindings.map { it.title }, VisibilityLevel.FULL))
            } else emptyList()
        } catch (e: Exception) { 
            Log.e(TAG, "AndroguardSmart Failed", e)
            listOf(buildErrorSignal("Androguard", e.message)) 
        }
    }

    private suspend fun runAndroguardDeep(pm: PackageManager, pkg: PackageInfo, expl: ConcurrentExplanation): List<EvidenceSignal> = withContext(Dispatchers.Default) {
        try {
            val result = androEngine.deepScanPackage(pm, pkg)
            val conf = (result.overallRiskScore / 100f).coerceIn(0f, 1f)
            val signals = mutableListOf<EvidenceSignal>()
            
            val isPacked = result.dexFindings.dynamicLoadIndicators.any { it.contains("jiagu", ignoreCase = true) || it.contains("packer", ignoreCase = true) }
            if (isPacked) {
                // PACKER CALIBRATION: Low certainty of malice, but high visibility penalty
                signals.add(EvidenceSignal("Androguard-Deep", EvidenceCategory.PACKER_OBFUSCATION, EvidenceCluster.DYNAMIC_STEALTH, 0.60f, SeverityBand.MEDIUM, CertaintyLevel.LOW, listOf("Packed/Obfuscated Executable Detected"), VisibilityLevel.OBFUSCATED))
            }
            
            if (conf > 0f) {
                result.dexFindings.suspiciousNamespaces.forEach { expl.apiRisks.add(it) }
                signals.add(EvidenceSignal("Androguard-Deep", EvidenceCategory.STRUCTURAL_ANOMALY, EvidenceCluster.STRUCTURAL_ANOMALY, conf, if (conf > 0.7f) SeverityBand.HIGH else SeverityBand.MEDIUM, CertaintyLevel.MEDIUM, result.dexFindings.suspiciousNamespaces, if (isPacked) VisibilityLevel.OBFUSCATED else VisibilityLevel.FULL))
            }
            signals
        } catch (e: Exception) { 
            Log.e(TAG, "AndroguardDeep Failed", e)
            listOf(buildErrorSignal("Androguard-Deep", e.message)) 
        }
    }

    // ═══════════════════════════════════════════
    // DETERMINISTIC EVALUATION MODULES
    // ═══════════════════════════════════════════

    /**
     * Contextual Demotion: Determines origin and applies power-user mitigation flags.
     */
    private fun evaluateOriginContext(pm: PackageManager, packageName: String, expl: ConcurrentExplanation): List<EvidenceSignal> {
        val isSystemApp = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 || 
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: Exception) { false }

        if (isSystemApp) {
            expl.mitigating.add("Verified system application.")
            return emptyList()
        }

        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { pm.getInstallSourceInfo(packageName).installingPackageName } catch (_: Exception) { null }
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
        }
        
        val safeInstallSources = listOf(
            "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia",
            "com.huawei.appmarket", "com.xiaomi.mipicks", "com.bbk.appstore", "com.heytap.market",
            "com.oppo.market", "com.vivo.appstore", "com.coloros.appmanager"
        )
        val isSideloaded = installer == null || !safeInstallSources.contains(installer)
        
        return if (isSideloaded) {
            expl.mitigating.add("Sideloaded application lacks automated vetting.")
            listOf(EvidenceSignal("OriginModule", EvidenceCategory.SYSTEM_INTEGRITY, EvidenceCluster.ORIGIN_CONTEXT, 0.30f, SeverityBand.LOW, CertaintyLevel.VERY_LOW, listOf("Sideloaded"), VisibilityLevel.FULL))
        } else {
            expl.mitigating.add("Verified installer source (Play Store / Trusted OEM).")
            emptyList()
        }
    }

    /**
     * Consensus Correlation Module: Dependency-Aware Escalation and Contradiction Handling.
     */
    private object ConsensusCorrelationModule {
        fun evaluate(signals: List<EvidenceSignal>, expl: ConcurrentExplanation): Pair<ThreatVerdict, Float> {
            val validSignals = signals.filter { it.visibility != VisibilityLevel.FAILED && it.confidence > 0f }
            val failedEngines = signals.count { it.visibility == VisibilityLevel.FAILED }
            
            // 1. DEFINITIVE HASH (Bypasses all reasoning)
            val exactMatch = validSignals.find { it.category == EvidenceCategory.SIGNATURE_EXACT && it.certainty == CertaintyLevel.DEFINITIVE }
            if (exactMatch != null) {
                expl.topReasons.add("Definitive malware hash identified.")
                return Pair(ThreatVerdict.HIGH_CONFIDENCE_MALWARE, 1.0f)
            }

            if (validSignals.isEmpty()) {
                if (failedEngines >= 2) {
                    expl.topReasons.add("$failedEngines engines failed analysis.")
                    return Pair(ThreatVerdict.LIMITED_VISIBILITY, 0.0f)
                }
                return Pair(ThreatVerdict.CLEAN, 0.0f)
            }

            // 2. DEPENDENCY SUPPRESSION (Cluster Consolidation)
            // Related signals do NOT stack linearly. We take the max confidence of the cluster 
            // and add a minor bonus for redundancy, bounded tightly.
            val clusterScores = mutableMapOf<EvidenceCluster, Float>()
            for (cluster in EvidenceCluster.values()) {
                val clusterSignals = validSignals.filter { it.cluster == cluster }
                if (clusterSignals.isNotEmpty()) {
                    val maxConf = clusterSignals.maxOf { it.confidence }
                    val redundancyBonus = (clusterSignals.size - 1) * 0.05f
                    clusterScores[cluster] = (maxConf + redundancyBonus).coerceIn(0f, 0.70f) // Cluster max is artificially capped
                }
            }

            // 3. CONTEXTUAL DEMOTION (Removed)
            // We no longer blindly trust the Play Store, as that causes False Negatives (malware bypassing detection).
            val hasPacker = validSignals.any { it.category == EvidenceCategory.PACKER_OBFUSCATION }

            // 4. BEHAVIORAL-CHAIN ESCALATION (Anti-FN)
            var chainMultiplier = 1.0f
            if (clusterScores.containsKey(EvidenceCluster.UI_ABUSE) && clusterScores.containsKey(EvidenceCluster.DYNAMIC_STEALTH)) {
                expl.topReasons.add("Advanced Evasion Chain: UI Abuse coupled with Dynamic Stealth.")
                chainMultiplier = 1.1f // Reduced from 1.6f to prevent False Positives for legitimate banking/automation apps
            }

            // 5. DETERMINISTIC INTENT vs CAPABILITY AGGREGATION
            // Fixes FPs for automation apps without relying on Play Store trust.
            var intentBelief = 0.0f
            var maxCapabilityBelief = 0.0f
            
            clusterScores.forEach { (cluster, score) ->
                when (cluster) {
                    EvidenceCluster.THREAT_SIGNATURE -> intentBelief += score * 0.95f
                    EvidenceCluster.DYNAMIC_STEALTH -> maxCapabilityBelief = maxOf(maxCapabilityBelief, score * 0.40f)
                    EvidenceCluster.UI_ABUSE -> maxCapabilityBelief = maxOf(maxCapabilityBelief, score * 0.40f)
                    EvidenceCluster.STRUCTURAL_ANOMALY -> maxCapabilityBelief = maxOf(maxCapabilityBelief, score * 0.40f)
                    else -> maxCapabilityBelief = maxOf(maxCapabilityBelief, score * 0.35f)
                }
            }

            // If an app only has capabilities (Accessibility/Permissions) but NO malicious intent (Stealth/Signatures),
            // it is fundamentally impossible to distinguish from a legitimate automation app deterministically.
            // Therefore, capabilities alone cannot exceed 0.60 (NEEDS_REVIEW).
            val totalThreatBelief = if (intentBelief > 0f) {
                intentBelief + maxCapabilityBelief
            } else {
                maxCapabilityBelief
            }

            val finalProbability = (totalThreatBelief * chainMultiplier).coerceIn(0f, 0.90f)

            // 6. VISIBILITY-AWARE CONFIDENCE CAPS
            val maxBelief = when {
                failedEngines >= 2 -> 0.40f  // Uncertainty limits panic
                hasPacker -> 0.70f           // Packers obscure true intent
                else -> 1.0f
            }

            val adjustedProb = finalProbability.coerceAtMost(maxBelief)

            // 7. VERDICT MAPPING
            val verdict = when {
                adjustedProb >= 0.85f -> ThreatVerdict.HIGH_RISK
                adjustedProb >= 0.65f -> ThreatVerdict.SUSPICIOUS
                hasPacker && adjustedProb < 0.65f -> ThreatVerdict.PACKED_APK
                adjustedProb >= 0.45f -> ThreatVerdict.NEEDS_REVIEW
                failedEngines > 0 && adjustedProb < 0.45f -> ThreatVerdict.PARTIAL_ANALYSIS
                adjustedProb >= 0.25f -> ThreatVerdict.INCONCLUSIVE
                adjustedProb >= 0.10f -> ThreatVerdict.LOW_RISK
                else -> ThreatVerdict.CLEAN
            }

            // Explanation populator
            if (verdict.score >= ThreatVerdict.SUSPICIOUS.score) {
                expl.topReasons.add("Verdict escalated due to high probability score: ${(adjustedProb*100).toInt()}%")
            } else if (verdict == ThreatVerdict.CLEAN && clusterScores.isNotEmpty()) {
                expl.topReasons.add("Anomalies detected, but mitigated by strong contextual trust.")
            }

            return Pair(verdict, adjustedProb)
        }
    }

    // ═══════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════

    private fun getAppLabel(pm: PackageManager, pkg: PackageInfo): String {
        return try { pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName } catch (e: Exception) { pkg.packageName }
    }

    private fun buildSafeVerdict(packageName: String, appLabel: String, startTime: Long) = CorrelatedVerdict(
        packageName = packageName, appLabel = appLabel,
        finalVerdict = ThreatVerdict.CLEAN, finalProbability = 0f,
        explanation = VerdictExplanation(emptyList()),
        signals = emptyList(), scanDurationMs = System.currentTimeMillis() - startTime
    )

    private fun buildErrorSignal(engine: String, msg: String? = null) = EvidenceSignal(
        engine, EvidenceCategory.SYSTEM_INTEGRITY, EvidenceCluster.STRUCTURAL_ANOMALY, 
        0f, SeverityBand.INFO, CertaintyLevel.LOW, listOf(msg ?: "Parser failure"), VisibilityLevel.FAILED
    )

    /** Coroutine-safe concurrent explanation builder */
    private class ConcurrentExplanation {
        val topReasons = CopyOnWriteArrayList<String>()
        val permissions = CopyOnWriteArrayList<String>()
        val apiRisks = CopyOnWriteArrayList<String>()
        val signatures = CopyOnWriteArrayList<String>()
        val behavioral = CopyOnWriteArrayList<String>()
        val structural = CopyOnWriteArrayList<String>()
        val certificates = CopyOnWriteArrayList<String>()
        val mitigating = CopyOnWriteArrayList<String>()

        fun build() = VerdictExplanation(
            topReasons = topReasons.distinct().take(3),
            permissionRisks = permissions.distinct().take(10),
            apiRisks = apiRisks.distinct().take(10),
            signatureMatches = signatures.distinct(),
            behavioralIndicators = behavioral.distinct().take(10),
            structuralAnomalies = structural.distinct().take(10),
            certificateIssues = certificates.distinct().take(5),
            mitigatingFactors = mitigating.distinct()
        )
    }

    companion object {
        private const val TAG = "ThreatCorrelation"
        @Volatile private var instance: ThreatCorrelationEngine? = null
        fun getInstance(context: Context): ThreatCorrelationEngine {
            return instance ?: synchronized(this) {
                instance ?: ThreatCorrelationEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
