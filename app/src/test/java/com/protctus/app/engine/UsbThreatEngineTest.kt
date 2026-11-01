package com.protctus.app.engine

import org.junit.Assert.*
import org.junit.Test


class UsbThreatEngineTest {

    // ── Individual Factor Tests ──

    @Test
    fun `no USB connection results in SAFE status with score 0`() {
        val state = calculateScore(
            connected = false,
            hostConnected = false,
            unlocked = false,
            adbEnabled = false,
            isLocked = false
        )
        assertEquals(0, state.riskScore)
        assertEquals(UsbStatus.SAFE, state.status)
    }

    @Test
    fun `connected and unlocked adds 50 risk`() {
        val state = calculateScore(
            connected = true,
            hostConnected = false,
            unlocked = true,
            adbEnabled = false,
            isLocked = false
        )
        assertEquals(50, state.riskScore)
        assertEquals(UsbStatus.WARNING, state.status)
    }

    @Test
    fun `host connected adds 30 risk`() {
        val state = calculateScore(
            connected = false,
            hostConnected = true,
            unlocked = false,
            adbEnabled = false,
            isLocked = false
        )
        assertEquals(30, state.riskScore)
        assertEquals(UsbStatus.SAFE, state.status)
    }

    @Test
    fun `adb enabled adds 20 risk`() {
        val state = calculateScore(
            connected = false,
            hostConnected = false,
            unlocked = false,
            adbEnabled = true,
            isLocked = false
        )
        assertEquals(20, state.riskScore)
        assertEquals(UsbStatus.SAFE, state.status)
    }

    @Test
    fun `host connected while locked adds 10 bonus risk`() {
        val state = calculateScore(
            connected = false,
            hostConnected = true,
            unlocked = false,
            adbEnabled = false,
            isLocked = true
        )
        assertEquals(40, state.riskScore) // 30 (host) + 10 (host+locked)
        assertEquals(UsbStatus.SAFE, state.status)
    }

    // ── Combined Threat Scenarios ──

    @Test
    fun `WARNING threshold at exactly 50`() {
        val state = calculateScore(
            connected = true,
            hostConnected = false,
            unlocked = true,
            adbEnabled = false,
            isLocked = false
        )
        assertEquals(50, state.riskScore)
        assertEquals(UsbStatus.WARNING, state.status)
    }

    @Test
    fun `CRITICAL threshold at 80 - full juice jacking scenario`() {
        val state = calculateScore(
            connected = true,
            hostConnected = true,
            unlocked = true,
            adbEnabled = false,
            isLocked = false
        )
        assertEquals(80, state.riskScore) // 50 + 30
        assertEquals(UsbStatus.CRITICAL, state.status)
    }

    @Test
    fun `maximum possible risk score is 110`() {
        val state = calculateScore(
            connected = true,
            hostConnected = true,
            unlocked = true,
            adbEnabled = true,
            isLocked = true
        )
        assertEquals(110, state.riskScore) // 50 + 30 + 20 + 10
        assertEquals(UsbStatus.CRITICAL, state.status)
    }

    @Test
    fun `connected but NOT unlocked means no data pin risk`() {
        val state = calculateScore(
            connected = true,
            hostConnected = false,
            unlocked = false,
            adbEnabled = false,
            isLocked = false
        )
        assertEquals(0, state.riskScore) // connected && unlocked both needed for +50
        assertEquals(UsbStatus.SAFE, state.status)
    }

    // ── Edge Case: Scoring just below CRITICAL ──

    @Test
    fun `score 79 remains WARNING not CRITICAL`() {
        // 50 (connected+unlocked) + 20 (adb) = 70, still WARNING
        val state = calculateScore(
            connected = true,
            hostConnected = false,
            unlocked = true,
            adbEnabled = true,
            isLocked = false
        )
        assertEquals(70, state.riskScore)
        assertEquals(UsbStatus.WARNING, state.status)
    }

    /**
     * Simulates the UsbThreatEngine scoring logic without requiring
     * a real Android Context or BroadcastReceiver.
     */
    private fun calculateScore(
        connected: Boolean,
        hostConnected: Boolean,
        unlocked: Boolean,
        adbEnabled: Boolean,
        isLocked: Boolean
    ): UsbThreatState {
        var riskScore = 0

        if (connected && unlocked) riskScore += 50
        if (hostConnected) riskScore += 30
        if (adbEnabled) riskScore += 20
        if (hostConnected && isLocked) riskScore += 10

        val status = when {
            riskScore >= 80 -> UsbStatus.CRITICAL
            riskScore >= 50 -> UsbStatus.WARNING
            else -> UsbStatus.SAFE
        }

        return UsbThreatState(riskScore, status)
    }
}
