package com.protctus.app.engine.androguard

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

/**
 * Certificate and signing analysis engine.
 *
 * Implements certificate inspection concepts from Androguard's
 * core/apk/__init__.py (get_certificates, get_signature_name methods).
 *
 * Analyzes:
 *   - Signer identity (CN, O, OU)
 *   - Self-signed status
 *   - Debug key usage
 *   - Certificate validity period
 *   - Signature algorithm strength
 *   - Certificate anomalies
 */
class CertificateAnalyzer {

    /**
     * Analyze the signing certificates of an installed package.
     */
    fun analyze(pm: PackageManager, packageInfo: PackageInfo): CertificateFindings {
        val findings = mutableListOf<AnalysisFinding>()
        var signerCount = 0
        var isDebug = false
        var isSelfSigned = false
        var issuerDN: String? = null
        var subjectDN: String? = null
        var serial: String? = null
        var validFrom: Long? = null
        var validTo: Long? = null
        var sigAlgorithm: String? = null

        try {
            val signingInfo = getSignatures(pm, packageInfo)
            signerCount = signingInfo.size

            if (signerCount == 0) {
                findings.add(AnalysisFinding(
                    FindingCategory.CERTIFICATE, Severity.CRITICAL,
                    "No Valid Signature",
                    "APK has no valid signing certificate. This should never happen for installed apps.",
                    null
                ))
                return buildResult(findings, 0, false, false, null, null, null, null, null, null)
            }

            val certFactory = CertificateFactory.getInstance("X.509")

            for (sig in signingInfo) {
                try {
                    val stream = ByteArrayInputStream(sig.toByteArray())
                    val cert = certFactory.generateCertificate(stream) as? X509Certificate ?: continue

                    subjectDN = cert.subjectDN?.name
                    issuerDN = cert.issuerDN?.name
                    serial = cert.serialNumber?.toString(16)
                    validFrom = cert.notBefore?.time
                    validTo = cert.notAfter?.time
                    sigAlgorithm = cert.sigAlgName

                    // ── Debug certificate detection ──
                    if (subjectDN?.contains("CN=Android Debug", ignoreCase = true) == true) {
                        isDebug = true
                        findings.add(AnalysisFinding(
                            FindingCategory.CERTIFICATE, Severity.HIGH,
                            "Debug Certificate",
                            "App is signed with Android debug key. This indicates a development build " +
                            "that should never appear on production devices.",
                            "Subject: $subjectDN"
                        ))
                    }

                    // ── Self-signed detection ──
                    if (subjectDN == issuerDN) {
                        isSelfSigned = true
                        // Self-signed is normal for Android but note it
                    }

                    // ── Certificate validity analysis ──
                    val now = System.currentTimeMillis()
                    if (validTo != null && validTo!! < now) {
                        findings.add(AnalysisFinding(
                            FindingCategory.CERTIFICATE, Severity.MEDIUM,
                            "Expired Certificate",
                            "Signing certificate expired on ${Date(validTo!!)}.",
                            "Serial: $serial"
                        ))
                    }

                    // Very short validity period (< 1 year) is suspicious
                    if (validFrom != null && validTo != null) {
                        val validityDays = (validTo!! - validFrom!!) / (1000 * 60 * 60 * 24)
                        if (validityDays < 365) {
                            findings.add(AnalysisFinding(
                                FindingCategory.CERTIFICATE, Severity.MEDIUM,
                                "Short Certificate Validity",
                                "Certificate is valid for only $validityDays days. " +
                                "Legitimate apps typically use 25+ year certificates.",
                                "Valid: ${Date(validFrom!!)} to ${Date(validTo!!)}"
                            ))
                        }
                    }

                    // ── Weak signature algorithm ──
                    if (sigAlgorithm != null) {
                        val weakAlgs = listOf("MD5", "SHA1withRSA", "MD5withRSA")
                        if (weakAlgs.any { sigAlgorithm!!.contains(it, ignoreCase = true) }) {
                            findings.add(AnalysisFinding(
                                FindingCategory.CERTIFICATE, Severity.MEDIUM,
                                "Weak Signature Algorithm",
                                "Certificate uses $sigAlgorithm which is cryptographically weak.",
                                "Consider SHA256withRSA or SHA256withECDSA"
                            ))
                        }
                    }

                    // ── Suspicious issuer patterns ──
                    val suspiciousIssuers = listOf("test", "debug", "sample", "example", "unknown")
                    if (issuerDN != null && suspiciousIssuers.any { issuerDN!!.contains(it, ignoreCase = true) }) {
                        findings.add(AnalysisFinding(
                            FindingCategory.CERTIFICATE, Severity.MEDIUM,
                            "Suspicious Certificate Issuer",
                            "Certificate issuer contains suspicious keywords: $issuerDN",
                            null
                        ))
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse certificate: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Certificate analysis failed: ${e.message}")
        }

        return buildResult(findings, signerCount, isDebug, isSelfSigned,
            issuerDN, subjectDN, serial, validFrom, validTo, sigAlgorithm)
    }

    private fun getSignatures(pm: PackageManager, packageInfo: PackageInfo): List<android.content.pm.Signature> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val flags = PackageManager.GET_SIGNING_CERTIFICATES
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageInfo.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION") pm.getPackageInfo(packageInfo.packageName, flags)
                }
                val signingInfo = info.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners?.toList() ?: emptyList()
                    } else {
                        signingInfo.signingCertificateHistory?.toList() ?: emptyList()
                    }
                } else emptyList()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageInfo.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.toList() ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildResult(
        findings: List<AnalysisFinding>, signerCount: Int,
        isDebug: Boolean, isSelfSigned: Boolean,
        issuerDN: String?, subjectDN: String?, serial: String?,
        validFrom: Long?, validTo: Long?, sigAlgorithm: String?
    ): CertificateFindings {
        return CertificateFindings(
            signerCount = signerCount,
            isDebugSigned = isDebug,
            isSelfSigned = isSelfSigned,
            issuerDN = issuerDN,
            subjectDN = subjectDN,
            serialNumber = serial,
            validFrom = validFrom,
            validTo = validTo,
            signatureAlgorithm = sigAlgorithm,
            findings = findings
        )
    }

    companion object {
        private const val TAG = "CertAnalyzer"
    }
}
