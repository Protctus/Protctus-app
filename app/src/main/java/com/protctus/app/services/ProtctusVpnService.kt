package com.protctus.app.services

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.content.pm.PackageManager

/**
 * Protctus DNS-filtering VPN Service.
 * 
 * All packet processing is handled in native C++ (edge_phishing_pipeline.cpp)
 * for zero battery drain when idle. The native thread uses blocking read()
 * and a pre-protected UDP socket for upstream DNS forwarding.
 * 
 * This Kotlin side only handles:
 * - VPN tunnel establishment via Android VpnService.Builder
 * - Socket protection callback (jniProtectSocket) called from native init
 * - Lifecycle management (start/stop)
 */
class ProtctusVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        init {
            try {
                System.loadLibrary("vpn-core")
            } catch (e: UnsatisfiedLinkError) {
                // Native library not available
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startNativeVpnFlow()
        return START_STICKY
    }

    private fun startNativeVpnFlow() {
        val builder = Builder()
            .setSession("Protctus Threat Engine")
            .addAddress("10.1.10.1", 32)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            // Only route DNS traffic through the tunnel, not ALL traffic.
            // This prevents breaking internet connectivity.
            .addRoute("8.8.8.8", 32)
            .addRoute("8.8.4.4", 32)
            .setMtu(1500)
            .setBlocking(true)

        val heavyApps = listOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.spotify.music"
        )
        for (app in heavyApps) {
            try {
                builder.addDisallowedApplication(app)
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        vpnInterface = builder.establish()

        vpnInterface?.let { fd ->
            val fdInt = fd.detachFd() // Prevent GC from closing the tunnel
            startNativeEngine(fdInt)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNativeEngine()
        vpnInterface?.close()
    }

    /**
     * Called from native C++ code (ONCE during init, not in the hot loop)
     * to protect the upstream DNS socket from being routed back through the VPN.
     * This prevents the infinite routing loop.
     */
    @androidx.annotation.Keep
    @Suppress("unused")
    fun jniProtectSocket(socketFd: Int): Boolean {
        return protect(socketFd)
    }

    @androidx.annotation.Keep
    private external fun startNativeEngine(tunFd: Int)
    @androidx.annotation.Keep
    private external fun stopNativeEngine()
}
