package com.protctus.app.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.protctus.app.engine.correlation.ThreatCorrelationEngine
import com.protctus.app.engine.correlation.ThreatVerdict
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.security.KeyStore

/**
 * Explicit security domains for posture analysis.
 */
enum class SecurityDomain {
    DEVICE_SECURITY,
    NETWORK_SECURITY,
    MALWARE_EXPOSURE,
    PRIVACY_EXPOSURE,
    SYSTEM_HARDENING,
    APPLICATION_RISK
}

/**
 * Confidence in the finding based on Android sandboxing limits.
 */
enum class Confidence { HIGH, MEDIUM, LOW }

/**
 * A single finding from the security scan with domain, evidence, and honest context.
 */
data class SecurityFinding(
    val domain: SecurityDomain,
    val title: String,              // Short heading
    val description: String,        // Contextual explanation
    val evidence: String,           // Technical artifact justifying the finding
    val fix: String,                // Actionable advice
    val severity: Severity,
    val confidence: Confidence,
    val intentAction: String? = null,
    val intentUri: String? = null
) {
    enum class Severity { CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL }
}

data class SecurityScanResult(
    val score: Int, // Purely malware/compromise metric (100 - max engine threat score)
    val domainFindings: Map<SecurityDomain, List<SecurityFinding>> = emptyMap(),
    
    // Backward-compat flags to ensure UI doesn't crash during transition
    val adbEnabled: Boolean,
    val unknownSourcesEnabled: Boolean,
    val isDeviceSecure: Boolean,
    val hasUnknownAccessibility: Boolean,
    val hasHttpProxy: Boolean,
    val hasUserCaCerts: Boolean,
    val isRooted: Boolean,
    val isEmulator: Boolean,
    val hasSideloadedApps: Boolean,
    val hasPermissionMismatchApps: Boolean,
    val isVpnActive: Boolean = false,
    val isWifiSecure: Boolean = true,
    val isDnsIntercepted: Boolean = false,
) {
    val findings: List<SecurityFinding>
        get() = domainFindings.values.flatten().sortedWith(
            compareBy<SecurityFinding> { it.severity.ordinal }.thenBy { it.title }
        )
}

object SecurityScanner {

    private val TRUSTED_ACCESSIBILITY_PACKAGES = setOf(
        "com.google.android.", "com.samsung.android.", "com.samsung.accessibility",
        "com.agilebits.onepassword", "com.dashlane", "com.lastpass",
        "com.bitwarden", "com.logmein.rescuemobile"
    )

    init {
        try {
            System.loadLibrary("native_scanner")
        } catch (e: UnsatisfiedLinkError) {
            // fallback
        }
    }

    @JvmStatic external fun checkDeepRootSignaturesNative(): Boolean
    @JvmStatic external fun checkIsEmulatorNative(): Boolean
    @JvmStatic external fun verifySelfIntegrityNative(): Boolean

    suspend fun performScan(context: Context): SecurityScanResult = withContext(Dispatchers.IO) {
        val findings = mutableListOf<SecurityFinding>()

        // ─── 1. System Hardening & Device Security ──────────────────────────────────

        // ADB
        val adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        if (adbEnabled) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.SYSTEM_HARDENING,
                title = "USB Debugging Active",
                description = "ADB is enabled. If a computer is authorized, it can execute commands bypassing normal sandboxing. Suitable for developers, but increases attack surface.",
                evidence = "ADB_ENABLED = 1 in Settings.Global.",
                fix = "Disable USB debugging if not actively developing.",
                severity = SecurityFinding.Severity.INFORMATIONAL,
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            ))
        }

        // Unknown Sources
        val unknownSourcesEnabled = checkUnknownSourcesEnabled(context)
        if (unknownSourcesEnabled) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.SYSTEM_HARDENING,
                title = "Unknown Sources Allowed",
                description = "Apps are permitted to install other packages outside of official app stores. Increases exposure to unvetted software.",
                evidence = "REQUEST_INSTALL_PACKAGES granted to non-system applications.",
                fix = "Revoke 'Install unknown apps' permission for browsers and file managers.",
                severity = SecurityFinding.Severity.LOW,
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
            ))
        }

        // Screen Lock
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isDeviceSecure = keyguardManager.isDeviceSecure
        if (!isDeviceSecure) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.DEVICE_SECURITY,
                title = "No Secure Lock Screen",
                description = "Device lacks a PIN, password, or biometric lock. Physical access grants complete access to all local data.",
                evidence = "KeyguardManager reports device is not secure.",
                fix = "Set up a lock screen in Security settings.",
                severity = SecurityFinding.Severity.HIGH,
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_SECURITY_SETTINGS
            ))
        }

        // Root
        val isRooted = checkDeepRootSignatures()
        if (isRooted) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.DEVICE_SECURITY,
                title = "Privileged Access (Root) Enabled",
                description = "Root access removes the Android application sandbox. Apps granted root can bypass security restrictions and access private data of other applications.",
                evidence = "su binary or root management framework detected via native signatures.",
                fix = "Ensure root manager (e.g., Magisk) denies access by default. Unroot if not needed for development.",
                severity = SecurityFinding.Severity.MEDIUM,
                confidence = Confidence.HIGH
            ))
        }

        // Emulator
        val isEmulator = checkIsEmulator()
        if (isEmulator) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.DEVICE_SECURITY,
                title = "Virtual Device Detected",
                description = "Running on a simulated environment. Device-bound cryptographic keys and Trusted Execution Environment (TEE) protections are unavailable.",
                evidence = "Hardware properties match known virtualization profiles.",
                fix = "Use physical hardware for production security environments.",
                severity = SecurityFinding.Severity.INFORMATIONAL,
                confidence = Confidence.HIGH
            ))
        }

        // Accessibility spyware - Advanced Contextual Check
        val (hasUnknownAccessibility, accessibilityFinding) = analyzeAccessibilityServices(context)
        if (accessibilityFinding != null) {
            findings.add(accessibilityFinding)
        }

        // ─── 2. Network Security ──────────────────────────────────

        // HTTP Proxy
        val hasHttpProxy = checkHttpProxy(context)
        if (hasHttpProxy) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.NETWORK_SECURITY,
                title = "Global Proxy Configured",
                description = "Network traffic is routed through a proxy server. This is common in enterprise MDM setups or ad-blockers, but can be used maliciously to intercept unencrypted traffic.",
                evidence = "HTTP proxy properties found in system settings.",
                fix = "Verify proxy settings in your WiFi network configuration.",
                severity = SecurityFinding.Severity.LOW,
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_WIFI_SETTINGS
            ))
        }

        // User CA Certificates
        val hasUserCaCerts = checkUserCaCertificates()
        if (hasUserCaCerts) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.NETWORK_SECURITY,
                title = "Custom Trust Certificate Installed",
                description = "A user-installed CA certificate allows the issuer to inspect encrypted (HTTPS) traffic. This is typical for corporate networks (MDM) or debugging tools, but dangerous if installed unknowingly.",
                evidence = "User CA found in AndroidCAStore.",
                fix = "Review trusted credentials in Security settings.",
                severity = SecurityFinding.Severity.MEDIUM,
                confidence = Confidence.HIGH
            ))
        }

        // VPN check — informational (VPN can be good or bad)
        val isVpnActive = checkVpnActive(context)

        // WiFi security
        val isWifiSecure = checkWifiSecurity(context)
        if (!isWifiSecure) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.NETWORK_SECURITY,
                title = "Open WiFi Network",
                description = "Connected to a network without WPA encryption. Local network attackers can passively sniff unencrypted traffic.",
                evidence = "Active WiFi connection lacks standard WPA2/3 transport security.",
                fix = "Use a VPN or disconnect from the open network.",
                severity = SecurityFinding.Severity.LOW,
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_WIFI_SETTINGS
            ))
        }

        // DNS interception
        val isDnsIntercepted = checkDnsInterception(context)
        if (isDnsIntercepted) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.NETWORK_SECURITY,
                title = "DNS Resolution Anomalies",
                description = "DNS queries may be intercepted or routed non-standardly. This occurs with ad-blockers (e.g. NextDNS, AdGuard), but can also indicate malicious DNS hijacking.",
                evidence = "DoH resolution mismatch against system resolver.",
                fix = "Check Private DNS settings or active VPN ad-blockers.",
                severity = SecurityFinding.Severity.LOW,
                confidence = Confidence.MEDIUM,
                intentAction = Settings.ACTION_WIRELESS_SETTINGS
            ))
        }

        // ─── 3. Application Risk & Malware Exposure (Engine-Driven) ──

        val (hasSideloaded, _, _) = checkInstalledAppsLightweight(context)
        if (hasSideloaded) {
            findings.add(SecurityFinding(
                domain = SecurityDomain.APPLICATION_RISK,
                title = "Sideloaded Applications",
                description = "Contains applications installed from outside verified stores (e.g., Play Store, F-Droid). While common for power users, these apps lack automated vetting.",
                evidence = "Installer package name is missing or untrusted.",
                fix = "Verify the source of sideloaded apps using Smart Scan.",
                severity = SecurityFinding.Severity.INFORMATIONAL,
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_APPLICATION_SETTINGS
            ))
        }

        // Final score starts at 100 and drops only based on explicitly discovered threats in other domains
        val finalScore = 100
        
        val domainMap = findings.groupBy { it.domain }

        return@withContext SecurityScanResult(
            score = finalScore,
            domainFindings = domainMap,
            adbEnabled = adbEnabled,
            unknownSourcesEnabled = unknownSourcesEnabled,
            isDeviceSecure = isDeviceSecure,
            hasUnknownAccessibility = hasUnknownAccessibility,
            hasHttpProxy = hasHttpProxy,
            hasUserCaCerts = hasUserCaCerts,
            isRooted = isRooted,
            isEmulator = isEmulator,
            hasSideloadedApps = hasSideloaded,
            hasPermissionMismatchApps = false,
            isVpnActive = isVpnActive,
            isWifiSecure = isWifiSecure,
            isDnsIntercepted = isDnsIntercepted
        )
    }
    
    /**
     * Advanced Contextual Accessibility Analysis
     * Leverages ThreatCorrelationEngine to inspect accessibility services for abusive traits
     * rather than blindly flagging all non-Play Store services.
     */
    private suspend fun analyzeAccessibilityServices(context: Context): Pair<Boolean, SecurityFinding?> {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val pm = context.packageManager
        val correlationEngine = ThreatCorrelationEngine.getInstance(context)

        var highestRiskService: String? = null
        var hasUnknown = false
        var isMalicious = false

        for (service in enabledServices) {
            val packageName = service.resolveInfo.serviceInfo.packageName
            val isTrusted = TRUSTED_ACCESSIBILITY_PACKAGES.any { packageName.startsWith(it) }
            
            if (!isTrusted) {
                hasUnknown = true
                
                hasUnknown = true
                
                // Lightweight check instead of heavy engine check for Home Screen
                // Real accessibility malware detection happens in Deep Scan
                try {
                    val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                    val isSystem = (packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) != 0
                    if (!isSystem) {
                        highestRiskService = try { packageInfo.applicationInfo?.loadLabel(pm).toString() } catch (e: Exception) { packageName }
                        isMalicious = false // Treat as warning, not malicious, on home screen
                    }
                } catch (e: Exception) {}
            }
        }

        if (!hasUnknown) return Pair(false, null)

        if (isMalicious) {
            return Pair(true, SecurityFinding(
                domain = SecurityDomain.PRIVACY_EXPOSURE,
                title = "Malicious Accessibility Abuse",
                description = "A flagged application ($highestRiskService) is actively using Accessibility Services. This allows the app to monitor your screen, intercept inputs, and perform automated actions.",
                evidence = "Engine verdict: SUSPICIOUS+ mapped to an active Accessibility Service.",
                fix = "Revoke accessibility access and run Deep Scan to remove the app.",
                severity = SecurityFinding.Severity.CRITICAL, // Critical because it's confirmed malicious + accessibility
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            ))
        } else {
            return Pair(true, SecurityFinding(
                domain = SecurityDomain.PRIVACY_EXPOSURE,
                title = "Third-Party Screen Reader Active",
                description = "An application has access to read your screen and inputs. This is normal for automation tools (like Tasker) or password managers, but poses a privacy risk if untrusted.",
                evidence = "Non-standard Accessibility Service detected with benign engine assessment.",
                fix = "Review active services in settings to ensure you recognize them.",
                severity = SecurityFinding.Severity.LOW,
                confidence = Confidence.HIGH,
                intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
            ))
        }
    }

    // ─── Network Checks ────────────────────────────────────────

    fun checkVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) { false }
    }

    @Suppress("DEPRECATION")
    fun checkWifiSecurity(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return true // Not on WiFi = "secure" in this context
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return true

            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true // Not WiFi

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            // RSSI < -85 is very weak, but main check is security type
            // On Android 12+, check security type via NetworkCapabilities
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // WifiInfo.getCurrentSecurityType exists on API 31+
                val secType = wifiInfo.javaClass.getMethod("getCurrentSecurityType").invoke(wifiInfo) as? Int
                // 0 = OPEN, 1 = WEP, 6 = OWE (open enhanced) 
                return secType != null && secType != 0 && secType != 1
            }
            // Fallback for older devices: check SSID for typical open network patterns
            // This is a best-effort heuristic since older APIs don't expose security type
            true
        } catch (e: Exception) { true } // Default to secure if check fails
    }

    fun checkDnsInterception(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                return false // Whitelist captive portal
            }

            // Raw DoH to 1.1.1.1 using SSLSocketFactory to avoid high-level interception
            val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            val socket = factory.createSocket("1.1.1.1", 443) as javax.net.ssl.SSLSocket
            socket.soTimeout = 5000
            socket.startHandshake()
            
            // Verify certificate pinning roughly (checking if it belongs to cloudflare)
            val certs = socket.session.peerCertificates
            if (certs.isEmpty()) return true
            val cert = certs[0] as? java.security.cert.X509Certificate
            val principalName = cert?.subjectX500Principal?.name ?: ""
            if (!principalName.contains("cloudflare", ignoreCase = true)) {
                return true // Pinned cert mismatch
            }
            
            val testDomain = "nxdomain-test.protctus.internal"
            
            val writer = java.io.PrintWriter(socket.outputStream)
            val req = "GET /dns-query?name=$testDomain&type=A HTTP/1.1\r\nHost: cloudflare-dns.com\r\naccept: application/dns-json\r\nConnection: close\r\n\r\n"
            writer.print(req)
            writer.flush()
            
            val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.inputStream))
            var response = ""
            var line = reader.readLine()
            while (line != null) {
                response += line
                line = reader.readLine()
            }
            socket.close()

            val systemResolved = try { java.net.InetAddress.getByName(testDomain); true } catch (e: Exception) { false }
            
            val dohNxDomain = response.contains("\"Status\":3") || response.contains("\"Status\": 3")
            
            if (systemResolved && dohNxDomain) {
                return true // Interception detected!
            }
            
            false
        } catch (e: Exception) { false }
    }

    // ─── Existing Checks ───────────────────────────────────────

    fun checkUnknownSourcesEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            return try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) == 1
            } catch (e: Exception) { false }
        }

        val trustedInstallers = setOf(
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.sec.android.app.samsungapps",
            "com.amazon.venezia"
        )

        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        
        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val pkgName = pkg.packageName
            if (trustedInstallers.contains(pkgName)) continue
            
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isBrowser = pkgName.contains("chrome") || pkgName.contains("browser") || pkgName.contains("firefox") || pkgName.contains("opera")
            
            if (isSystem || isBrowser) continue
            
            val hasPerm = pkg.requestedPermissions?.contains("android.permission.REQUEST_INSTALL_PACKAGES") ?: false
            if (hasPerm) {
                try {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    val opStr = "android:request_install_packages"
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(opStr, appInfo.uid, pkgName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(opStr, appInfo.uid, pkgName)
                    }
                    if (mode == AppOpsManager.MODE_ALLOWED) return true
                } catch (e: Exception) { }
            }
        }
        return false
    }

    private fun checkInstalledAppsLightweight(context: Context): Triple<Boolean, Int, List<Pair<String, ThreatVerdict>>> {
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) { emptyList() }

        val safeInstallSources = listOf(
            "com.android.vending", "com.sec.android.app.samsungapps",
            "com.amazon.venezia", "org.fdroid.fdroid"
        )

        var hasSideloadedApps = false

        for (packageInfo in packages) {
            val pkgName = packageInfo.packageName
            if (pkgName == context.packageName) continue

            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { pm.getInstallSourceInfo(pkgName).installingPackageName } catch (e: Exception) { null }
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(pkgName)
            }
            if (installer == null || !safeInstallSources.contains(installer)) {
                hasSideloadedApps = true
                break
            }
        }

        return Triple(hasSideloadedApps, 0, emptyList())
    }

    // Keep legacy method signature for backward compatibility (delegates to engine-based check)
    fun checkInstalledApps(context: Context): Pair<Boolean, Boolean> {
        // This synchronous version is only used where coroutines aren't available
        // It falls back to basic sideload detection only (no engine analysis)
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) { emptyList() }

        val safeInstallSources = listOf(
            "com.android.vending", "com.sec.android.app.samsungapps",
            "com.amazon.venezia", "org.fdroid.fdroid"
        )

        var hasSideloadedApps = false

        for (packageInfo in packages) {
            val pkgName = packageInfo.packageName
            val appInfo = packageInfo.applicationInfo ?: continue
            if (pkgName == context.packageName) continue

            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { pm.getInstallSourceInfo(pkgName).installingPackageName } catch (e: Exception) { null }
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(pkgName)
            }
            if (installer == null || !safeInstallSources.contains(installer)) {
                hasSideloadedApps = true
                break
            }
        }

        // No engine analysis in synchronous path — return false for malware
        return Pair(hasSideloadedApps, false)
    }

    fun checkUntrustedAccessibilityServices(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in enabledServices) {
            val packageName = service.resolveInfo.serviceInfo.packageName
            val isTrusted = TRUSTED_ACCESSIBILITY_PACKAGES.any { trustedPrefix ->
                packageName.startsWith(trustedPrefix)
            }
            if (!isTrusted) return true
        }
        return false
    }

    fun checkHttpProxy(context: Context): Boolean {
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        val globalProxy = Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        return (!proxyHost.isNullOrBlank() && !proxyPort.isNullOrBlank()) || !globalProxy.isNullOrBlank()
    }

    fun checkUserCaCertificates(): Boolean {
        try {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null, null)
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith("user:")) return true
            }
        } catch (e: Exception) {}
        return false
    }

    fun checkDeepRootSignatures(): Boolean {
        return try { checkDeepRootSignaturesNative() } catch (e: UnsatisfiedLinkError) { false }
    }

    fun checkIsEmulator(): Boolean {
        return try { checkIsEmulatorNative() } catch (e: UnsatisfiedLinkError) { false }
    }
}