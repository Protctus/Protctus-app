package com.protctus.app.security

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class IntegrityShieldTest {

    @Before
    fun setup() {
        // Reset tampered state before each test via reflection
        val field = IntegrityShield::class.java.getDeclaredField("_isAppTampered")
        field.isAccessible = true
        val flow = field.get(null) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        flow.value = false
    }

    @Test
    fun `initial state is NOT tampered`() = runTest {
        val isTampered = IntegrityShield.isAppTampered.first()
        assertFalse("App should not be marked as tampered initially", isTampered)
    }

    @Test
    fun `integrity token is null initially`() = runTest {
        val token = IntegrityShield.integrityToken.first()
        assertNull("Token should be null before a check runs", token)
    }

    @Test
    fun `generateNonce produces unique values`() {
        val method = IntegrityShield::class.java.getDeclaredMethod("generateNonce")
        method.isAccessible = true

        val nonces = mutableSetOf<String>()
        repeat(100) {
            val nonce = method.invoke(null) as String
            nonces.add(nonce)
        }

        assertEquals("100 nonce calls should produce 100 unique values", 100, nonces.size)
    }

    @Test
    fun `nonce is valid SHA-256 hex string`() {
        val method = IntegrityShield::class.java.getDeclaredMethod("generateNonce")
        method.isAccessible = true

        val nonce = method.invoke(null) as String
        assertEquals("SHA-256 hex should be 64 characters", 64, nonce.length)
        assertTrue("Nonce should be lowercase hex", nonce.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `tampered state flow can be observed reactively`() = runTest {
        // Simulate tampered state
        val field = IntegrityShield::class.java.getDeclaredField("_isAppTampered")
        field.isAccessible = true
        val flow = field.get(null) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>

        assertFalse(IntegrityShield.isAppTampered.first())

        flow.value = true
        assertTrue(IntegrityShield.isAppTampered.first())

        flow.value = false
        assertFalse(IntegrityShield.isAppTampered.first())
    }
}
