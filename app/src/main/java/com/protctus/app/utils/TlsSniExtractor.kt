package com.protctus.app.utils

/**
 * High-performance, pure Kotlin utility to extract the Server Name Indication (SNI) 
 * from a raw TCP payload containing a TLS ClientHello packet.
 * 
 * Strict bounds checking is enforced to prevent IndexOutOfBoundsException on malformed packets.
 */
object TlsSniExtractor {

    /**
     * Attempts to extract the SNI hostname from a raw TCP payload.
     * Returns null if the payload is not a valid TLS ClientHello or does not contain an SNI.
     */
    fun extractSni(payload: ByteArray): String? {
        try {
            var position = 0

            // 1. Verify TLS Record Type (0x16 = Handshake)
            if (!hasRemaining(payload, position, 5)) return null
            if (payload[position].toInt() != 0x16) return null
            
            // Skip TLS version (2 bytes)
            position += 3
            
            // Read Record Length
            val recordLength = readUint16(payload, position)
            position += 2
            
            // Anti-Fragmentation & Buffer Overflow Check
            // Reject if the payload doesn't contain the full TLS record yet
            if (!hasRemaining(payload, position, recordLength)) return null

            // 2. Verify Handshake Type (0x01 = ClientHello)
            if (!hasRemaining(payload, position, 4)) return null
            if (payload[position].toInt() != 0x01) return null
            
            // Skip Handshake Length (3 bytes) and Client Version (2 bytes)
            position += 6
            
            // Skip Random (32 bytes)
            position += 32
            if (!hasRemaining(payload, position, 1)) return null

            // Skip Session ID
            val sessionIdLength = payload[position].toInt() and 0xFF
            position += 1 + sessionIdLength
            if (!hasRemaining(payload, position, 2)) return null

            // Skip Cipher Suites
            val cipherSuitesLength = readUint16(payload, position)
            position += 2 + cipherSuitesLength
            if (!hasRemaining(payload, position, 1)) return null

            // Skip Compression Methods
            val compressionMethodsLength = payload[position].toInt() and 0xFF
            position += 1 + compressionMethodsLength
            if (!hasRemaining(payload, position, 2)) return null

            // Read Extensions Length
            val extensionsLength = readUint16(payload, position)
            position += 2

            // Ensure we don't read past the available extensions or payload length
            val extensionsEnd = position + extensionsLength
            if (extensionsEnd > payload.size) return null

            // Iterate over extensions
            while (position + 4 <= extensionsEnd) {
                val extensionType = readUint16(payload, position)
                val extensionLength = readUint16(payload, position + 2)
                position += 4

                // Check bounds for this specific extension data
                if (position + extensionLength > extensionsEnd) return null

                // Extension Type 0x0000 = Server Name Indication
                if (extensionType == 0x0000) {
                    return parseSniExtension(payload, position, extensionLength)
                }

                // Skip to the next extension
                position += extensionLength
            }
        } catch (e: Exception) {
            // Failsafe for any unexpected bitwise or structural parsing errors
            return null
        }
        
        return null
    }

    private fun parseSniExtension(payload: ByteArray, position: Int, extensionLength: Int): String? {
        var currentPos = position
        
        // Ensure enough bytes for Server Name List Length (2 bytes)
        if (extensionLength < 2) return null
        val listLength = readUint16(payload, currentPos)
        currentPos += 2
        
        // Ensure the list length matches our available extension data length
        if (listLength > extensionLength - 2) return null

        val listEnd = currentPos + listLength
        
        while (currentPos + 3 <= listEnd) {
            // Read Server Name Type (0x00 = Hostname)
            val nameType = payload[currentPos].toInt() and 0xFF
            currentPos += 1

            // Read Server Name Length
            val nameLength = readUint16(payload, currentPos)
            currentPos += 2

            if (currentPos + nameLength > listEnd) return null

            if (nameType == 0x00) {
                // Decode the hostname as UTF-8
                return String(payload, currentPos, nameLength, Charsets.UTF_8)
            }

            // Skip to next server name in the list (if any)
            currentPos += nameLength
        }
        
        return null
    }

    /**
     * Utility to read an unsigned 16-bit integer (Big-Endian) safely.
     */
    private fun readUint16(array: ByteArray, offset: Int): Int {
        val byte1 = array[offset].toInt() and 0xFF
        val byte2 = array[offset + 1].toInt() and 0xFF
        return (byte1 shl 8) or byte2
    }

    /**
     * Checks if the required number of bytes are available from the current offset.
     * Uses Long math to strictly prevent Integer Overflow bypasses.
     */
    private fun hasRemaining(array: ByteArray, offset: Int, length: Int): Boolean {
        if (offset < 0 || length < 0) return false
        val endPos = offset.toLong() + length.toLong()
        return endPos <= array.size.toLong()
    }
}
