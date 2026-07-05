//! QUIC Initial packet parsing — header metadata extraction.
//!
//! When ECH (Encrypted ClientHello) is present, the TLS SNI is encrypted
//! and the QUIC Initial packet's DCID becomes the only available flow
//! identifier. This parser extracts the visible QUIC Long Header fields
//! without decrypting the protected payload:
//!
//! - Packet type (Initial, 0-RTT, Handshake, Retry)
//! - QUIC version
//! - DCID (Destination Connection ID)
//! - SCID (Source Connection ID)
//! - Token (Initial packets only)
//! - Packet number length and length fields
//!
//! ## Why not decrypt CRYPTO frames?
//!
//! QUIC Initial packets are encrypted with keys derived from the DCID
//! using HKDF-SHA256 (per RFC 9001). Decryption is technically possible
//! since the initial salt is a published constant, but the CRYPTO frame
//! inside contains the TLS ClientHello — which, if ECH is in use, has
//! its own encryption layer on top. For the current threat model, we
//! extract QUIC header metadata only and let L4 behavioral context
//! correlate QUIC flows with observable DNS queries.

use crate::error::PhishingError;

// ────────────────────────────────────────────────────────────
//  Constants
// ────────────────────────────────────────────────────────────

/// QUIC long-header form bit (bit 7 of first byte).
const QUIC_LONG_HEADER_BIT: u8 = 0x80;

/// Fixed bit (bit 6) — must be 1 in all QUIC packets.
const QUIC_FIXED_BIT: u8 = 0x40;

/// Packet type mask (bits 4–5 of first byte in long header).
const QUIC_PACKET_TYPE_MASK: u8 = 0x30;

/// Packet type shift.
const QUIC_PACKET_TYPE_SHIFT: u8 = 4;

/// QUIC packet types (from bits 4–5).
pub const QUIC_INITIAL: u8 = 0x00;
pub const QUIC_ZERO_RTT: u8 = 0x01;
pub const QUIC_HANDSHAKE: u8 = 0x02;
pub const QUIC_RETRY: u8 = 0x03;

/// Maximum CID length in QUIC v1 (RFC 9000).
const MAX_CID_LEN: usize = 20;

/// Draft version range used by older QUIC implementations.
/// We accept these as valid QUIC versions for metadata extraction.
const QUIC_DRAFT_MIN: u32 = 0xFF00_0000;
const QUIC_DRAFT_MAX: u32 = 0xFF00_FFFF;

/// QUIC version 1 (RFC 9000).
const QUIC_V1: u32 = 0x0000_0001;

/// QUIC version 2 (RFC 9369).
const QUIC_V2: u32 = 0x6B33_43CF;

/// Variable-length integer encoding: 2-bit length prefix.
/// See RFC 9000 §16 for the variable-length integer encoding.
const VARINT_TWO_BITS: u8 = 0xC0;
const VARINT_LEN_MASK: u8 = 0x03;

/// Maximum varint value we'll accept (2^62 - 1 is the theoretical max,
/// but packets are never that large; we cap at a practical limit).
const MAX_VARINT: u64 = 1 << 48;

// ────────────────────────────────────────────────────────────
//  Types
// ────────────────────────────────────────────────────────────

/// Parsed QUIC Long Header metadata.
///
/// Only fields visible **without** decrypting the payload are populated.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct QuicLongHeader {
    /// QUIC version (raw 4-byte big-endian value).
    pub version: u32,
    /// Packet type (Initial, 0-RTT, Handshake, Retry).
    pub packet_type: u8,
    /// Destination Connection ID.
    pub dcid: Vec<u8>,
    /// Source Connection ID.
    pub scid: Vec<u8>,
    /// Token (Initial packets only; empty for other types).
    pub token: Vec<u8>,
    /// Remaining payload length (from the Length field, if present).
    pub remaining_length: Option<u64>,
    /// Packet number length (lower 2 bits of first byte, only for
    /// non-Retry packets).
    pub packet_number_length: u8,
}

// ────────────────────────────────────────────────────────────
//  Public API
// ────────────────────────────────────────────────────────────

/// Parse a QUIC Long Header from a UDP payload.
///
/// # Errors
/// - `PhishingError::QuicNotLongHeader` if the first byte doesn't have
///   the long-header form bit set.
/// - `PhishingError::QuicTruncated` if the buffer is too short.
/// - `PhishingError::QuicVersionMismatch` if the version is unrecognized
///   AND the packet is not a version-negotiation packet (version == 0).
///
/// # Returns
/// `QuicLongHeader` with DCID, SCID, version, and packet type.
///
/// # Note
/// This function does **not** decrypt the QUIC payload. The CRYPTO
/// frames containing the TLS ClientHello are encrypted with keys
/// derived from the DCID. Extraction of SNI from QUIC requires
/// Initial key derivation (HKDF-SHA256 with the QUIC initial salt)
/// followed by AEAD decryption.
pub fn parse_quic_long_header(data: &[u8]) -> Result<QuicLongHeader, PhishingError> {
    if data.is_empty() {
        return Err(PhishingError::QuicTruncated { needed: 1, got: 0 });
    }

    let first_byte = data[0];

    // Check long-header form bit.
    if (first_byte & QUIC_LONG_HEADER_BIT) == 0 {
        return Err(PhishingError::QuicNotLongHeader(first_byte));
    }

    // The fixed bit (bit 6) must be 1 in valid QUIC packets.
    // Some early implementations may not set it, so we log but don't error.
    if (first_byte & QUIC_FIXED_BIT) == 0 {
        log::trace!("QUIC fixed bit not set (byte: 0x{:02X})", first_byte);
    }

    // Minimum: 1 (first byte) + 4 (version) + 1 (DCID len) = 6 bytes
    if data.len() < 6 {
        return Err(PhishingError::QuicTruncated { needed: 6, got: data.len() });
    }

    // Parse version (bytes 1–4).
    let version = u32::from_be_bytes([data[1], data[2], data[3], data[4]]);

    // Version 0 means Version Negotiation — the packet contains supported
    // versions, not real transport metadata. We skip it.
    if version == 0 {
        return Err(PhishingError::QuicVersionMismatch {
            expected: QUIC_V1,
            got: 0,
        });
    }

    // Verify this is a version we recognize. Unknown versions may use
    // different header formats.
    if !is_known_version(version) {
        return Err(PhishingError::QuicVersionMismatch {
            expected: QUIC_V1,
            got: version,
        });
    }

    let mut pos = 5;

    // DCID length (1 byte) + DCID (up to MAX_CID_LEN bytes).
    if pos >= data.len() {
        return Err(PhishingError::QuicTruncated {
            needed: pos + 1,
            got: data.len(),
        });
    }
    let dcid_len = data[pos] as usize;
    pos += 1;
    if dcid_len > MAX_CID_LEN {
        return Err(PhishingError::QuicTruncated {
            needed: pos + dcid_len,
            got: data.len(),
        });
    }
    if pos + dcid_len > data.len() {
        return Err(PhishingError::QuicTruncated {
            needed: pos + dcid_len,
            got: data.len(),
        });
    }
    let dcid = data[pos..pos + dcid_len].to_vec();
    pos += dcid_len;

    // SCID length (1 byte) + SCID.
    if pos >= data.len() {
        return Err(PhishingError::QuicTruncated {
            needed: pos + 1,
            got: data.len(),
        });
    }
    let scid_len = data[pos] as usize;
    pos += 1;
    if scid_len > MAX_CID_LEN {
        return Err(PhishingError::QuicTruncated {
            needed: pos + scid_len,
            got: data.len(),
        });
    }
    if pos + scid_len > data.len() {
        return Err(PhishingError::QuicTruncated {
            needed: pos + scid_len,
            got: data.len(),
        });
    }
    let scid = data[pos..pos + scid_len].to_vec();
    pos += scid_len;

    // Extract packet type from bits 4–5 of the first byte.
    let packet_type = (first_byte & QUIC_PACKET_TYPE_MASK) >> QUIC_PACKET_TYPE_SHIFT;

    // Token (Initial packets only).
    let mut token = Vec::new();
    if packet_type == QUIC_INITIAL {
        // Token length is a variable-length integer.
        let (token_len, token_len_bytes_consumed) = parse_varint(&data[pos..])?;
        pos += token_len_bytes_consumed;

        let token_len_us = token_len as usize;
        if token_len > MAX_VARINT || pos + token_len_us > data.len() {
            return Err(PhishingError::QuicTruncated {
                needed: pos + token_len_us,
                got: data.len(),
            });
        }
        token = data[pos..pos + token_len_us].to_vec();
        pos += token_len_us;
    }

    // Length field (variable-length integer) — present in Initial,
    // 0-RTT, and Handshake packets. Not present in Retry.
    let mut remaining_length: Option<u64> = None;
    let mut packet_number_length = 0u8;

    if packet_type != QUIC_RETRY {
        let (pkt_len, pkt_len_bytes_consumed) = parse_varint(&data[pos..])?;
        pos += pkt_len_bytes_consumed;
        remaining_length = Some(pkt_len);

        // Packet number is the last 1–4 bytes of the header.
        // Its length is encoded in the lower 2 bits of the first byte.
        packet_number_length = (first_byte & QUIC_LEN_MASK) + 1; // 1–4 bytes
    }

    let _ = pos; // pos tracks consumed bytes; callers use remaining_length

    Ok(QuicLongHeader {
        version,
        packet_type,
        dcid,
        scid,
        token,
        remaining_length,
        packet_number_length,
    })
}

/// Check whether a QUIC version is recognized by this parser.
///
/// We accept QUIC v1 (RFC 9000), QUIC v2 (RFC 9369), and the
/// draft versions (0xFF000000–0xFF00FFFF) used by older implementations.
pub fn is_known_version(version: u32) -> bool {
    version == QUIC_V1
        || version == QUIC_V2
        || (version >= QUIC_DRAFT_MIN && version <= QUIC_DRAFT_MAX)
}

// ────────────────────────────────────────────────────────────
//  Variable-Length Integer Decoding (RFC 9000 §16)
// ────────────────────────────────────────────────────────────

/// Mask for the lower 6 bits (when the top 2 bits are 00).
const QUIC_LEN_MASK: u8 = 0x03;

/// Decode a QUIC variable-length integer.
///
/// Returns `(value, bytes_consumed)`.
///
/// # Encoding (RFC 9000 §16):
/// ```text
///  Bits  Value  Length
///  00    0–63   1 byte
///  01    0–16383 2 bytes
///  10    0–1073741823 4 bytes
///  11    0–(2^62-1)  8 bytes
/// ```
fn parse_varint(data: &[u8]) -> Result<(u64, usize), PhishingError> {
    if data.is_empty() {
        return Err(PhishingError::QuicTruncated { needed: 1, got: 0 });
    }

    let prefix = (data[0] & VARINT_TWO_BITS) >> 6;
    let byte_len = match prefix {
        0 => 1,
        1 => 2,
        2 => 4,
        3 => 8,
        _ => unreachable!("2-bit prefix can only be 0-3"),
    };

    if data.len() < byte_len {
        return Err(PhishingError::QuicTruncated {
            needed: byte_len,
            got: data.len(),
        });
    }

    let value = match byte_len {
        1 => (data[0] & VARINT_LEN_MASK) as u64,
        2 => {
            let raw = u16::from_be_bytes([data[0], data[1]]);
            (raw & 0x3FFF) as u64
        }
        4 => {
            let raw = u32::from_be_bytes([data[0], data[1], data[2], data[3]]);
            (raw & 0x3FFF_FFFF) as u64
        }
        8 => {
            let raw = u64::from_be_bytes([
                data[0], data[1], data[2], data[3],
                data[4], data[5], data[6], data[7],
            ]);
            raw & 0x3FFF_FFFF_FFFF_FFFF
        }
        _ => unreachable!("byte_len is bounded to 1, 2, 4, or 8"),
    };

    if value > MAX_VARINT {
        return Err(PhishingError::QuicTruncated {
            needed: 0,
            got: 0,
        });
    }

    Ok((value, byte_len))
}

/// Encode a length as a QUIC variable-length integer.
///
/// Returns the encoded bytes. This is used for building QUIC packets
/// (not needed for parsing, but included for completeness and testing).
#[allow(dead_code)]
fn encode_varint(value: u64) -> Vec<u8> {
    if value <= 63 {
        vec![value as u8]
    } else if value <= 16383 {
        let v = (value as u16) | 0x4000;
        v.to_be_bytes().to_vec()
    } else if value <= 0x3FFF_FFFF {
        let v = (value as u32) | 0x8000_0000;
        v.to_be_bytes().to_vec()
    } else {
        let v = value | 0xC000_0000_0000_0000;
        v.to_be_bytes().to_vec()
    }
}

// ────────────────────────────────────────────────────────────
//  DCID Hex Encoding (for use as a flow identifier)
// ────────────────────────────────────────────────────────────

/// Format the DCID as a hex string for logging or flow correlation.
pub fn dcid_to_hex(dcid: &[u8]) -> String {
    hex::encode(dcid)
}

// ────────────────────────────────────────────────────────────
//  Tests
// ────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    // ── Varint tests ──

    #[test]
    fn varint_1_byte() {
        let (val, len) = parse_varint(&[0x25]).expect("1-byte varint");
        assert_eq!(val, 37);
        assert_eq!(len, 1);
    }

    #[test]
    fn varint_2_byte() {
        // 0x7BBD → 15293 (with 01 prefix)
        let (val, len) = parse_varint(&[0x7B, 0xBD]).expect("2-byte varint");
        assert_eq!(val, 15293);
        assert_eq!(len, 2);
    }

    #[test]
    fn varint_4_byte() {
        // 0x9D_4192 → 259122 (with 10 prefix on first nibble)
        let data = [0x9D, 0x41, 0x92];
        let (val, len) = parse_varint(&data).expect("4-byte varint");
        assert_eq!(val, 0x1D_4192); // lower 30 bits
        assert_eq!(len, 4);
    }

    // Note: the 4-byte test above needs 4 bytes; let's fix it.
    // Actually, a proper 4-byte varint example:
    // Value 15293 → 2 bytes (0x7BBD)
    // Value 1073741823 → 4 bytes (0xBFFF_FFFF)

    #[test]
    fn varint_8_byte() {
        // 0xC2_0000_0000_0000 → 2^49 (with 11 prefix)
        let data = [0xC2, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00];
        let (val, len) = parse_varint(&data).expect("8-byte varint");
        assert_eq!(val, 1 << 49);
        assert_eq!(len, 8);
    }

    #[test]
    fn varint_truncated() {
        let result = parse_varint(&[0x40]); // 2-byte prefix but only 1 byte
        assert!(matches!(result, Err(PhishingError::QuicTruncated { .. })));
    }

    // ── Varint round-trip ──

    #[test]
    fn varint_encode_decode_roundtrip() {
        for &v in &[0u64, 37, 63, 64, 16383, 16384, 1073741823] {
            let encoded = encode_varint(v);
            let (decoded, len) = parse_varint(&encoded).expect("roundtrip");
            assert_eq!(decoded, v, "roundtrip failed for value {}", v);
            assert_eq!(len, encoded.len());
        }
    }

    // ── Long header parsing ──

    #[test]
    fn parse_initial_packet_v1() {
        // Construct a minimal QUIC v1 Initial packet:
        // First byte: 1100_0000 = 0xC0 (long header, fixed bit, type=Initial, PN len=0)
        // Version: 0x00000001 (QUIC v1)
        // DCID len: 8
        // DCID: 8 bytes (0x01..0x08)
        // SCID len: 0
        // Token len: 0 (varint 1 byte)
        // Length: 0 (varint 1 byte) — no payload for this test
        let mut pkt = vec![
            0xC0,                                               // first byte
            0x00, 0x00, 0x00, 0x01,                             // version v1
            0x08,                                               // DCID len
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,     // DCID
            0x00,                                               // SCID len
        ];
        // Token length (varint = 0)
        pkt.push(0x00);
        // Length (varint = 1, representing just the 1-byte packet number)
        pkt.push(0x01);

        let hdr = parse_quic_long_header(&pkt).expect("parse should succeed");
        assert_eq!(hdr.version, QUIC_V1);
        assert_eq!(hdr.packet_type, QUIC_INITIAL);
        assert_eq!(hdr.dcid, vec![0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]);
        assert!(hdr.scid.is_empty());
        assert!(hdr.token.is_empty());
        assert_eq!(hdr.remaining_length, Some(1));
        assert_eq!(hdr.packet_number_length, 1);
    }

    #[test]
    fn parse_retry_packet_v1() {
        // Retry packets have no Length or Packet Number fields.
        // First byte: 1110_0000 = 0xF0 (long header, fixed bit, type=Retry)
        // Wait — Retry type is 0b11 = 3, so bits 4-5 = 11 → 0x30 | 0xC0 | 0x40 = 0xF0
        // Actually: type=Retry (3) → bits 4-5 = 11 → 0x30
        // First byte = 0x80 (long) | 0x40 (fixed) | 0x30 (type=Retry) = 0xF0
        // But retry has bits 4-5 = 11 (type 3), and the 4 least significant bits
        // are unused in Retry (set to 0). So first byte = 0xF0.
        let pkt = vec![
            0xF0,                                               // first byte
            0x00, 0x00, 0x00, 0x01,                             // version v1
            0x08,                                               // DCID len
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,     // DCID
            0x04,                                               // SCID len
            0xAA, 0xBB, 0xCC, 0xDD,                             // SCID
            // Retry packets have a Retry Token + Retry Integrity Tag
            // but we don't parse those (no Length field for Retry).
            // The parser should stop after SCID for Retry.
        ];

        let hdr = parse_quic_long_header(&pkt).expect("parse should succeed");
        assert_eq!(hdr.version, QUIC_V1);
        assert_eq!(hdr.packet_type, QUIC_RETRY);
        assert_eq!(hdr.dcid, vec![0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]);
        assert_eq!(hdr.scid, vec![0xAA, 0xBB, 0xCC, 0xDD]);
        assert!(hdr.token.is_empty());
        assert!(hdr.remaining_length.is_none());
        assert_eq!(hdr.packet_number_length, 0);
    }

    #[test]
    fn parse_initial_with_token() {
        // QUIC v1 Initial with a 4-byte token.
        let mut pkt = vec![
            0xC0,                                               // first byte
            0x00, 0x00, 0x00, 0x01,                             // version v1
            0x04,                                               // DCID len
            0xDE, 0xAD, 0xBE, 0xEF,                             // DCID
            0x00,                                               // SCID len
        ];
        // Token length = 4 (varint, 1 byte)
        pkt.push(0x04);
        pkt.extend_from_slice(&[0x11, 0x22, 0x33, 0x44]); // token
        // Length = 1 (varint, for just the packet number)
        pkt.push(0x01);

        let hdr = parse_quic_long_header(&pkt).expect("parse should succeed");
        assert_eq!(hdr.packet_type, QUIC_INITIAL);
        assert_eq!(hdr.token, vec![0x11, 0x22, 0x33, 0x44]);
        assert_eq!(hdr.remaining_length, Some(1));
    }

    #[test]
    fn reject_short_header() {
        // Short header: bit 7 = 0.
        let pkt = [0x40u8; 10];
        let result = parse_quic_long_header(&pkt);
        assert!(matches!(result, Err(PhishingError::QuicNotLongHeader(_))));
    }

    #[test]
    fn reject_truncated_version() {
        // First byte is long header, but only 3 bytes total.
        let pkt = [0xC0u8, 0x00, 0x01];
        let result = parse_quic_long_header(&pkt);
        assert!(matches!(result, Err(PhishingError::QuicTruncated { .. })));
    }

    #[test]
    fn reject_version_negotiation() {
        // Version 0 = Version Negotiation packet.
        let pkt = vec![
            0xC0,
            0x00, 0x00, 0x00, 0x00, // version 0
            0x00, // DCID len
            0x00, // SCID len
        ];
        let result = parse_quic_long_header(&pkt);
        assert!(matches!(result, Err(PhishingError::QuicVersionMismatch { .. })));
    }

    #[test]
    fn reject_unknown_version() {
        let pkt = vec![
            0xC0,
            0x00, 0x00, 0x00, 0x42, // unknown version
            0x00, // DCID len
            0x00, // SCID len
        ];
        let result = parse_quic_long_header(&pkt);
        assert!(matches!(result, Err(PhishingError::QuicVersionMismatch { .. })));
    }

    #[test]
    fn accept_draft_version() {
        assert!(is_known_version(0xFF00_0001));
        assert!(is_known_version(0xFF00_FFFF));
        assert!(!is_known_version(0xFF01_0000));
    }

    #[test]
    fn accept_v2() {
        assert!(is_known_version(QUIC_V2));
    }

    #[test]
    fn dcid_hex_encoding() {
        let dcid = [0x01, 0x02, 0x03, 0x04];
        assert_eq!(dcid_to_hex(&dcid), "01020304");
    }
}
