//! TCP / UDP transport header parsing.
//!
//! Zero-copy parsers for the transport layer. After `parse_ip_header`
//! yields the IP payload, these parsers extract ports and the TCP
//! data offset / flags needed for flow correlation and port-based
//! protocol identification (e.g., port 443 → TLS or QUIC).

use crate::error::PhishingError;

// ────────────────────────────────────────────────────────────
//  Types
// ────────────────────────────────────────────────────────────

/// Parsed transport-layer header.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TransportHeader {
    Tcp(TcpHeader),
    Udp(UdpHeader),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TcpHeader {
    pub src_port: u16,
    pub dst_port: u16,
    /// Sequence number (raw, not relative).
    pub seq: u32,
    /// Acknowledgment number (raw, not relative).
    pub ack: u32,
    /// Data offset in 32-bit words → header length = (data_offset * 4) bytes.
    pub data_offset: u8,
    /// TCP flags (CWR|ECE|URG|ACK|PSH|RST|SYN|FIN).
    pub flags: u8,
    /// Window size.
    pub window: u16,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UdpHeader {
    pub src_port: u16,
    pub dst_port: u16,
    /// Length of UDP header + payload (bytes).
    pub length: u16,
}

// ────────────────────────────────────────────────────────────
//  Flag bitmasks
// ────────────────────────────────────────────────────────────

pub const TCP_FIN: u8 = 0x01;
pub const TCP_SYN: u8 = 0x02;
pub const TCP_RST: u8 = 0x04;
pub const TCP_PSH: u8 = 0x08;
pub const TCP_ACK: u8 = 0x10;
pub const TCP_URG: u8 = 0x20;
pub const TCP_ECE: u8 = 0x40;
pub const TCP_CWR: u8 = 0x80;

// ────────────────────────────────────────────────────────────
//  Public API
// ────────────────────────────────────────────────────────────

/// Parse a TCP or UDP header from the IP-layer payload.
///
/// `protocol` is the value from the IP header (`protocol` field for IPv4,
/// `next_header` for IPv6). Use [`TransportProtocol::from`] to convert.
///
/// # Errors
/// - `PhishingError::TcpTruncated` / `PhishingError::UdpTruncated` if the
///   buffer is too short.
/// - `PhishingError::Other` for unsupported transport protocols.
///
/// # Returns
/// `(TransportHeader, payload_bytes)` where payload starts after the
/// transport header.
pub fn parse_transport_header(
    protocol: u8,
    data: &[u8],
) -> Result<(TransportHeader, &[u8]), PhishingError> {
    match protocol {
        crate::types::protocol::IPPROTO_TCP => {
            let (hdr, payload) = parse_tcp(data)?;
            Ok((TransportHeader::Tcp(hdr), payload))
        }
        crate::types::protocol::IPPROTO_UDP => {
            let (hdr, payload) = parse_udp(data)?;
            Ok((TransportHeader::Udp(hdr), payload))
        }
        _ => Err(PhishingError::Other(format!(
            "unsupported transport protocol: {}",
            protocol
        ))),
    }
}

// ── TCP ──

const TCP_MIN_HDR: usize = 20;

fn parse_tcp(data: &[u8]) -> Result<(TcpHeader, &[u8]), PhishingError> {
    if data.len() < TCP_MIN_HDR {
        return Err(PhishingError::TcpTruncated {
            needed: TCP_MIN_HDR,
            got: data.len(),
        });
    }

    let src_port = u16::from_be_bytes([data[0], data[1]]);
    let dst_port = u16::from_be_bytes([data[2], data[3]]);
    let seq = u32::from_be_bytes([data[4], data[5], data[6], data[7]]);
    let ack = u32::from_be_bytes([data[8], data[9], data[10], data[11]]);
    let data_offset = (data[12] >> 4) & 0x0F;
    let header_length = (data_offset as usize) * 4;

    // Flags occupy the lower 8 bits of byte 13.
    let flags = data[13];
    let window = u16::from_be_bytes([data[14], data[15]]);

    if header_length < TCP_MIN_HDR || data.len() < header_length {
        return Err(PhishingError::TcpTruncated {
            needed: if header_length < TCP_MIN_HDR {
                TCP_MIN_HDR
            } else {
                header_length
            },
            got: data.len(),
        });
    }

    let payload = &data[header_length..];

    Ok((
        TcpHeader {
            src_port,
            dst_port,
            seq,
            ack,
            data_offset,
            flags,
            window,
        },
        payload,
    ))
}

// ── UDP ──

const UDP_HDR_LEN: usize = 8;

fn parse_udp(data: &[u8]) -> Result<(UdpHeader, &[u8]), PhishingError> {
    if data.len() < UDP_HDR_LEN {
        return Err(PhishingError::UdpTruncated {
            needed: UDP_HDR_LEN,
            got: data.len(),
        });
    }

    let src_port = u16::from_be_bytes([data[0], data[1]]);
    let dst_port = u16::from_be_bytes([data[2], data[3]]);
    let length = u16::from_be_bytes([data[4], data[5]]);

    // The UDP length field includes the 8-byte header. If it's zero or
    // exceeds the buffer, fall back to the buffer length (some NAT/VPN
    // stacks rewrite length incorrectly).
    let header_length = if length == 0 || (length as usize) > data.len() {
        data.len()
    } else {
        length as usize
    };

    let payload = &data[UDP_HDR_LEN.min(header_length)..];

    Ok((UdpHeader { src_port, dst_port, length }, payload))
}

// ────────────────────────────────────────────────────────────
//  Helper: identify TLS/QUIC by port
// ────────────────────────────────────────────────────────────

/// Returns `true` if the port is typically used for TLS (443) or a
/// common HTTPS alternate (8443, 4433).
pub fn is_tls_port(port: u16) -> bool {
    matches!(port, 443 | 4433 | 8443)
}

/// Returns `true` if the port is typically used for QUIC (443).
pub fn is_quic_port(port: u16) -> bool {
    port == 443
}

// ────────────────────────────────────────────────────────────
//  Tests
// ────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_valid_tcp_syn() {
        // Minimal TCP SYN packet (20 bytes, data_offset=5)
        let pkt: [u8; 20] = [
            0x12, 0x34, // src port 0x1234
            0x00, 0x50, // dst port 80
            0xDE, 0xAD, 0xBE, 0xEF, // seq
            0x00, 0x00, 0x00, 0x00, // ack
            0x50, // data offset = 5 (20 bytes)
            0x02, // SYN
            0xFF, 0xFF, // window
            0x00, 0x00, // checksum
            0x00, 0x00, // urgent ptr
        ];

        let (hdr, payload) = parse_tcp(&pkt).expect("parse should succeed");
        assert_eq!(hdr.src_port, 0x1234);
        assert_eq!(hdr.dst_port, 80);
        assert_eq!(hdr.seq, 0xDEADBEEF);
        assert_eq!(hdr.data_offset, 5);
        assert_eq!(hdr.flags, TCP_SYN);
        assert!(payload.is_empty());
    }

    #[test]
    fn parse_truncated_tcp() {
        let pkt = [0u8; 10];
        let result = parse_tcp(&pkt);
        assert!(matches!(result, Err(PhishingError::TcpTruncated { .. })));
    }

    #[test]
    fn parse_valid_udp() {
        let pkt: [u8; 16] = [
            0x00, 0x35, // src port 53
            0x80, 0x01, // dst port 32769
            0x00, 0x10, // length 16 (header + payload)
            0x00, 0x00, // checksum
            0xAA, 0xBB, 0xCC, 0xDD, // payload
            0xEE, 0xFF, 0x00, 0x11,
        ];

        let (hdr, payload) = parse_udp(&pkt).expect("parse should succeed");
        assert_eq!(hdr.src_port, 53);
        assert_eq!(hdr.dst_port, 32769);
        assert_eq!(hdr.length, 16);
        assert_eq!(payload.len(), 8);
        assert_eq!(payload, &[0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x00, 0x11]);
    }

    #[test]
    fn parse_truncated_udp() {
        let pkt = [0u8; 4];
        let result = parse_udp(&pkt);
        assert!(matches!(result, Err(PhishingError::UdpTruncated { .. })));
    }

    #[test]
    fn parse_udp_zero_length_uses_buffer() {
        let mut pkt = [0u8; 12];
        pkt[0..2].copy_from_slice(&0u16.to_be_bytes()); // src
        pkt[2..4].copy_from_slice(&443u16.to_be_bytes()); // dst
        pkt[4..6].copy_from_slice(&0u16.to_be_bytes()); // length = 0
        let (hdr, _payload) = parse_udp(&pkt).expect("parse should succeed");
        assert_eq!(hdr.dst_port, 443);
        assert_eq!(hdr.length, 0);
    }

    #[test]
    fn route_tcp_by_protocol_number() {
        let pkt: [u8; 20] = [
            0xAB, 0xCD, 0x00, 0x50, 0, 0, 0, 1, 0, 0, 0, 0, 0x50, 0x10, 0xFF, 0xFF, 0, 0, 0, 0,
        ];
        let (hdr, _) = parse_transport_header(crate::types::protocol::IPPROTO_TCP, &pkt)
            .expect("should parse as TCP");
        assert!(matches!(hdr, TransportHeader::Tcp(_)));
    }

    #[test]
    fn route_udp_by_protocol_number() {
        let pkt: [u8; 8] = [0, 53, 0, 443, 0, 8, 0, 0];
        let (hdr, _) = parse_transport_header(crate::types::protocol::IPPROTO_UDP, &pkt)
            .expect("should parse as UDP");
        assert!(matches!(hdr, TransportHeader::Udp(_)));
    }

    #[test]
    fn route_unsupported_protocol() {
        let pkt = [0u8; 20];
        let result = parse_transport_header(99, &pkt);
        assert!(matches!(result, Err(PhishingError::Other(_))));
    }

    #[test]
    fn tls_port_detection() {
        assert!(is_tls_port(443));
        assert!(is_tls_port(4433));
        assert!(is_tls_port(8443));
        assert!(!is_tls_port(80));
        assert!(is_quic_port(443));
        assert!(!is_quic_port(8080));
    }
}
