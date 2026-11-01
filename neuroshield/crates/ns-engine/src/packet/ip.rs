//! IPv4 / IPv6 header parsing.
//!
//! Zero-copy parsers for IP header fields relevant to phishing detection:
//! version, protocol, src/dst addresses, header length, fragmentation flags.

use crate::error::PhishingError;

// ────────────────────────────────────────────────────────────
//  Types
// ────────────────────────────────────────────────────────────

/// Parsed IP header with fields the engine actually needs.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IpHeader {
    V4(Ipv4Header),
    V6(Ipv6Header),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Ipv4Header {
    pub version: u8,
    /// Total length of the IPv4 packet (header + payload) in bytes.
    pub total_length: u16,
    /// Protocol number (TCP = 6, UDP = 17, etc.).
    pub protocol: u8,
    pub src: [u8; 4],
    pub dst: [u8; 4],
    /// Header length in bytes (IHL * 4).
    pub header_length: u8,
    /// Fragmentation info.
    pub more_fragments: bool,
    pub fragment_offset: u16,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Ipv6Header {
    pub version: u8,
    /// Payload length following the fixed 40-byte header.
    pub payload_length: u16,
    /// Next-header protocol (TCP = 6, UDP = 17, etc.).
    pub next_header: u8,
    pub src: [u8; 16],
    pub dst: [u8; 16],
}

/// Transport protocol extracted from the IP header.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransportProtocol {
    Tcp,
    Udp,
    Icmp,
    Other(u8),
}

impl From<u8> for TransportProtocol {
    fn from(value: u8) -> Self {
        match value {
            6 => TransportProtocol::Tcp,
            17 => TransportProtocol::Udp,
            1 | 58 => TransportProtocol::Icmp,
            v => TransportProtocol::Other(v),
        }
    }
}

// ────────────────────────────────────────────────────────────
//  IP Header Parser
// ────────────────────────────────────────────────────────────

/// Parse an IPv4 or IPv6 header from a raw packet buffer.
///
/// # Errors
/// Returns `PhishingError::IpTruncated` if the buffer is too short
/// to contain a valid IP header, or `PhishingError::IpUnsupportedVersion`
/// if the version nibble is neither 4 nor 6.
///
/// # Returns
/// `(IpHeader, payload_bytes)` where payload starts after the IP header.
pub fn parse_ip_header(data: &[u8]) -> Result<(IpHeader, &[u8]), PhishingError> {
    if data.is_empty() {
        return Err(PhishingError::IpTruncated { needed: 1, got: 0 });
    }
    let version = data[0] >> 4;
    match version {
        4 => {
            let (hdr, payload) = parse_ipv4(data)?;
            Ok((IpHeader::V4(hdr), payload))
        }
        6 => {
            let (hdr, payload) = parse_ipv6(data)?;
            Ok((IpHeader::V6(hdr), payload))
        }
        v => Err(PhishingError::IpUnsupportedVersion(v)),
    }
}

// ── IPv4 ──

fn parse_ipv4(data: &[u8]) -> Result<(Ipv4Header, &[u8]), PhishingError> {
    const MIN_IPV4_HDR: usize = 20;
    if data.len() < MIN_IPV4_HDR {
        return Err(PhishingError::IpTruncated {
            needed: MIN_IPV4_HDR,
            got: data.len(),
        });
    }

    let ihl = (data[0] & 0x0F) as usize;
    let header_length = (ihl * 4) as u8;
    if data.len() < header_length as usize {
        return Err(PhishingError::IpTruncated {
            needed: header_length as usize,
            got: data.len(),
        });
    }

    let total_length = u16::from_be_bytes([data[2], data[3]]);
    let flags_and_offset = u16::from_be_bytes([data[6], data[7]]);
    let more_fragments = (flags_and_offset & 0x2000) != 0;
    let fragment_offset = flags_and_offset & 0x1FFF;

    let protocol = data[9];
    let src = [data[12], data[13], data[14], data[15]];
    let dst = [data[16], data[17], data[18], data[19]];

    // Optional: verify checksum. For performance in the hot path
    // we skip it; the VPN layer will drop corrupted packets before
    // they reach us. If needed, `verify_ipv4_checksum` below can be
    // called explicitly.

    let payload = &data[header_length as usize..];

    Ok((
        Ipv4Header {
            version: 4,
            total_length,
            protocol,
            src,
            dst,
            header_length,
            more_fragments,
            fragment_offset,
        },
        payload,
    ))
}

// ── IPv6 ──

fn parse_ipv6(data: &[u8]) -> Result<(Ipv6Header, &[u8]), PhishingError> {
    const IPV6_HDR_LEN: usize = 40;
    if data.len() < IPV6_HDR_LEN {
        return Err(PhishingError::IpTruncated {
            needed: IPV6_HDR_LEN,
            got: data.len(),
        });
    }

    let payload_length = u16::from_be_bytes([data[4], data[5]]);
    let next_header = data[6];

    let mut src = [0u8; 16];
    let mut dst = [0u8; 16];
    src.copy_from_slice(&data[8..24]);
    dst.copy_from_slice(&data[24..40]);

    let payload = &data[IPV6_HDR_LEN..];

    Ok((
        Ipv6Header {
            version: 6,
            payload_length,
            next_header,
            src,
            dst,
        },
        payload,
    ))
}

// ────────────────────────────────────────────────────────────
//  Optional: IPv4 Header Checksum Verification
// ────────────────────────────────────────────────────────────

/// Verify the IPv4 header checksum.
///
/// Returns `true` if the checksum is correct, `false` otherwise.
/// This is NOT called by `parse_ipv4` for performance; call it
/// explicitly if checksum validation is required.
pub fn verify_ipv4_checksum(data: &[u8], expected: u16) -> bool {
    if data.len() < 20 {
        return false;
    }
    let mut sum: u32 = 0;
    for chunk in data[..20].chunks_exact(2) {
        sum += u16::from_be_bytes([chunk[0], chunk[1]]) as u32;
    }
    while sum >> 16 != 0 {
        sum = (sum >> 16) + (sum & 0xFFFF);
    }
    let computed = !(sum as u16);
    computed == expected
}
