package com.protctus.vpncore

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Isolated VpnService wrapper that loads the extracted NetGuard native engine.
 * Contains the JNI methods for starting/stopping the C++ TUN loop.
 */
class VpnCoreService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var dnsCallback: DnsInterceptorCallback? = null
    private var packetCallback: PacketListenerCallback? = null

    companion object {
        private const val TAG = "VpnCoreService"

        init {
            try {
                System.loadLibrary("vpn-core")
                Log.i(TAG, "Successfully loaded native vpn-core library")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load vpn-core native library", e)
            }
        }
    }

    /**
     * Initializes the VPN interface with split tunneling capabilities.
     * 
     * @param disallowedApps List of package names to bypass the VPN entirely (saving battery/CPU).
     * @param dnsInterceptor Callback to evaluate DNS requests natively.
     * @param packetListener Callback to receive telemetry for Threat Fusion.
     */
    fun startVpn(
        disallowedApps: List<String>,
        dnsInterceptor: DnsInterceptorCallback,
        packetListener: PacketListenerCallback
    ) {
        this.dnsCallback = dnsInterceptor
        this.packetCallback = packetListener

        val builder = Builder()
            .setSession("Protctus Threat Engine")
            .addAddress("10.1.10.1", 32)
            .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("0::0", 0)
            .setMtu(1500)

        // Split Tunneling - Bypass trusted/heavy apps to save battery
        for (app in disallowedApps) {
            try {
                builder.addDisallowedApplication(app)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to bypass app: $app", e)
            }
        }

        vpnInterface = builder.establish()

        vpnInterface?.let { fd ->
            val fdInt = fd.detachFd() // CRITICAL: prevent GC from closing the tunnel
            Log.i(TAG, "Starting native VPN engine on FD: $fdInt")
            // Start the native C++ loop
            startNativeVpn(fdInt)
        } ?: Log.e(TAG, "Failed to establish VpnService interface")
    }

    fun stopVpn() {
        Log.i(TAG, "Stopping native VPN engine")
        stopNativeVpn()
        
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
    }

    // --- JNI Callbacks accessed from C++ (jni.c) --- //

    /**
     * Called synchronously from `dns.c` via JNI when a UDP Port 53 packet is parsed.
     */
    @Suppress("unused")
    private fun jniIsDomainMalicious(domain: String): Boolean {
        return dnsCallback?.isDomainMalicious(domain) ?: false
    }

    /**
     * Called asynchronously from `pcap.c` via JNI when a connection is routed.
     */
    @Suppress("unused")
    private fun jniOnPacketRouted(
        protocol: Int,
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        uid: Int
    ) {
        packetCallback?.onPacketRouted(protocol, sourceIp, sourcePort, destIp, destPort, uid)
    }

    // --- Native External Functions --- //

    private external fun startNativeVpn(fd: Int)
    private external fun stopNativeVpn()
}
