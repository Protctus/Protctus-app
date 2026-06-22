package com.protctus.app.engine.androguard

/**
 * Complete result of Androguard-style APK analysis.
 *
 * This contains all findings from manifest analysis, DEX inspection,
 * certificate analysis, and component risk assessment.
 */
data class ApkAnalysisResult(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val manifestFindings: ManifestFindings,
    val dexFindings: DexFindings,
    val certificateFindings: CertificateFindings,
    val structureFindings: ApkStructureFindings,
    val overallRiskScore: Int,
    val overallRiskLevel: RiskLevel,
    val scanDurationMs: Long,
    val engineVersion: String = "androguard-protctus-1.0"
) {
    /** All findings flattened into a single list for UI display */
    val allFindings: List<AnalysisFinding>
        get() = manifestFindings.findings + dexFindings.findings +
                certificateFindings.findings + structureFindings.findings

    /** Only high/critical findings */
    val criticalFindings: List<AnalysisFinding>
        get() = allFindings.filter { it.severity >= Severity.HIGH }

    val summary: String
        get() {
            val total = allFindings.size
            val critical = criticalFindings.size
            return when {
                critical > 0 -> "$critical critical issue(s) found out of $total total findings."
                total > 0 -> "$total low/medium finding(s). No critical issues."
                else -> "No security issues detected."
            }
        }
}

/**
 * Findings from AndroidManifest.xml analysis.
 */
data class ManifestFindings(
    val dangerousPermissions: List<String>,
    val allPermissions: List<String>,
    val exportedActivities: List<ExportedComponent>,
    val exportedServices: List<ExportedComponent>,
    val exportedReceivers: List<ExportedComponent>,
    val exportedProviders: List<ExportedComponent>,
    val suspiciousIntentFilters: List<SuspiciousIntentFilter>,
    val hasAccessibilityService: Boolean,
    val hasDeviceAdmin: Boolean,
    val hasOverlayPermission: Boolean,
    val hasBootReceiver: Boolean,
    val hasHighPriorityReceiver: Boolean,
    val hasBackupEnabled: Boolean,
    val isDebuggable: Boolean,
    val allowsBackup: Boolean,
    val findings: List<AnalysisFinding>
)

/**
 * Findings from DEX metadata inspection.
 */
data class DexFindings(
    val dexFileCount: Int,
    val totalClassCount: Int,
    val totalMethodCount: Int,
    val suspiciousNamespaces: List<String>,
    val reflectionIndicators: List<String>,
    val dynamicLoadIndicators: List<String>,
    val nativeMethodIndicators: List<String>,
    val cryptoIndicators: List<String>,
    val networkIndicators: List<String>,
    val obfuscationScore: Int,
    val findings: List<AnalysisFinding>
)

/**
 * Findings from certificate/signing analysis.
 */
data class CertificateFindings(
    val signerCount: Int,
    val isDebugSigned: Boolean,
    val isSelfSigned: Boolean,
    val issuerDN: String?,
    val subjectDN: String?,
    val serialNumber: String?,
    val validFrom: Long?,
    val validTo: Long?,
    val signatureAlgorithm: String?,
    val findings: List<AnalysisFinding>
)

/**
 * Findings from APK structure analysis.
 */
data class ApkStructureFindings(
    val totalEntryCount: Int,
    val dexEntries: List<String>,
    val nativeLibraries: List<String>,
    val supportedAbis: List<String>,
    val hasHiddenDex: Boolean,
    val hasHiddenApk: Boolean,
    val hasSuspiciousAssets: Boolean,
    val apkSizeBytes: Long,
    val findings: List<AnalysisFinding>
)

/**
 * A single analysis finding/detection.
 */
data class AnalysisFinding(
    val category: FindingCategory,
    val severity: Severity,
    val title: String,
    val description: String,
    val technicalDetail: String? = null
)

/**
 * An exported component with its risk assessment.
 */
data class ExportedComponent(
    val name: String,
    val type: ComponentType,
    val intentFilters: List<String>,
    val permissionRequired: String?,
    val riskLevel: RiskLevel
)

/**
 * A suspicious intent filter found in the manifest.
 */
data class SuspiciousIntentFilter(
    val componentName: String,
    val action: String,
    val reason: String
)

enum class ComponentType { ACTIVITY, SERVICE, RECEIVER, PROVIDER }

enum class FindingCategory {
    PERMISSION, COMPONENT, INTENT_FILTER, CERTIFICATE,
    DEX_STRUCTURE, NATIVE_CODE, OBFUSCATION, APK_STRUCTURE,
    DYNAMIC_LOADING, REFLECTION, CRYPTO, NETWORK
}

enum class Severity(val score: Int) {
    INFO(0), LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    operator fun compareTo(other: Int): Int = this.score.compareTo(other)
}

enum class RiskLevel(val score: Int) {
    NONE(0), LOW(15), MEDIUM(40), HIGH(70), CRITICAL(90);

    companion object {
        fun fromScore(score: Int): RiskLevel = when {
            score >= 80 -> CRITICAL
            score >= 60 -> HIGH
            score >= 35 -> MEDIUM
            score >= 10 -> LOW
            else -> NONE
        }
    }
}
