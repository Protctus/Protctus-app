package com.protctus.vpncore

/**
 * Callback triggered natively when a new TCP/UDP connection is detected on the TUN interface.
 * Used exclusively for telemetry and Threat Fusion correlation (zero payload capture).
 */
interface PacketListenerCallback {
    
    /**
     * Called asynchronously from the native engine when a packet is routed.
     * 
     * @param protocol The protocol number (e.g., 6 for TCP, 17 for UDP).
     * @param sourceIp The source IPv4/IPv6 address.
     * @param sourcePort The source port.
     * @param destIp The destination IPv4/IPv6 address.
     * @param destPort The destination port.
     * @param uid The UID of the application that sent the packet (resolved via Android 10+ APIs later, or natively if available).
     */
    fun onPacketRouted(
        protocol: Int,
        sourceIp: String,
        sourcePort: Int,
        destIp: String,
        destPort: Int,
        uid: Int
    )
}
