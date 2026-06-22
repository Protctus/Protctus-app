package com.protctus.app.engine.clam

/**
 * Represents a single malware signature for the ClamAV-compatible signature engine.
 *
 * Signature types supported (derived from ClamAV's readdb.c and matcher.h):
 *   - HASH: File hash match (MD5, SHA1, SHA256) — from .hdb/.hsb format
 *   - BYTE_PATTERN: Hex byte sequence match — from .ndb format
 *   - LOGICAL: Boolean combination of sub-signatures — from .ldb format
 */
data class ClamSignature(
    val id: String,
    val malwareName: String,
    val type: SignatureType,
    val hashValue: String? = null,
    val hashAlgorithm: HashAlgorithm? = null,
    val bytePattern: ByteArray? = null,
    val patternOffset: PatternOffset = PatternOffset.ANY,
    val targetType: TargetType = TargetType.ANY,
    val fileSize: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClamSignature) return false
        return id == other.id && malwareName == other.malwareName
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Signature types — maps to ClamAV's matcher-*.c implementations.
 */
enum class SignatureType {
    /** Hash-based matching (matcher-hash.c) */
    HASH,
    /** Byte pattern matching (matcher-bm.c / matcher-ac.c) */
    BYTE_PATTERN,
    /** Logical combination of sub-signatures */
    LOGICAL
}

/**
 * Hash algorithms supported — maps to ClamAV's CLI_HASH_* constants.
 */
enum class HashAlgorithm(val digestName: String, val hexLength: Int) {
    MD5("MD5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64)
}

/**
 * Pattern offset specification — from ClamAV's CLI_OFF_* constants in matcher.h.
 */
enum class PatternOffset {
    /** Match anywhere in the file */
    ANY,
    /** Match at the beginning of the file */
    ABSOLUTE_START,
    /** Match relative to entry point (PE/ELF) */
    ENTRY_POINT,
    /** Match relative to end of file */
    EOF
}

/**
 * Target file types — from ClamAV's cli_target_t enum in matcher.h.
 */
enum class TargetType(val clamId: Int) {
    ANY(0),
    PE(1),
    OLE2(2),
    HTML(3),
    MAIL(4),
    GRAPHICS(5),
    ELF(6),
    ASCII(7),
    MACHO(9),
    PDF(10),
    FLASH(11),
    JAVA(12);

    companion object {
        fun fromClamId(id: Int): TargetType =
            entries.find { it.clamId == id } ?: ANY
    }
}

/**
 * Result of a ClamAV signature match.
 */
data class ClamMatchResult(
    val malwareName: String,
    val matchType: String,
    val signatureId: String,
    val confidence: Float,
    val fileHash: String?,
    val scanDurationMs: Long
)
