// src/parser.rs
use std::net::{Ipv4Addr, Ipv6Addr};

/// Custom error types for packet parsing. 
/// We do not use panic-based errors.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ParseError {
    PacketTooShort,
    InvalidHeaderLength,
    UnsupportedProtocol,
    UnknownTransportProtocol,
}

/// Represents the Layer 4 protocol extracted from the IP header.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum L4Protocol {
    Tcp,
    Udp,
    Unknown(u8),
}

impl L4Protocol {
    #[inline]
    pub fn from_u8(val: u8) -> Self {
        match val {
            6 => L4Protocol::Tcp,
            17 => L4Protocol::Udp,
            _ => L4Protocol::Unknown(val),
        }
    }
}

/// Transport-layer metadata extracted from TCP or UDP headers.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TransportInfo {
    Tcp {
        src_port: u16,
        dst_port: u16,
        seq: u32,
        ack: u32,
        flags: u8, // TCP flags (SYN, ACK, RST, etc.)
    },
    Udp {
        src_port: u16,
        dst_port: u16,
    },
}

/// Network-layer metadata.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IpInfo {
    V4 {
        src: Ipv4Addr,
        dst: Ipv4Addr,
        protocol: L4Protocol,
    },
    V6 {
        src: Ipv6Addr,
        dst: Ipv6Addr,
        protocol: L4Protocol,
    },
}

/// The zero-copy parsed packet representation passed to the detection engine tiers.
pub struct ParsedPacket<'a> {
    pub ip: IpInfo,
    pub transport: TransportInfo,
    /// Pointer to the L7 payload (e.g., DNS payload, TLS Client Hello).
    /// This relies on Rust's lifetime guarantees to prevent use-after-free.
    pub l7_payload: &'a [u8],
}

/// Main entry point for packet parsing.
/// Accepts a raw byte slice as read from the Android TUN interface.
pub fn parse_packet(packet: &[u8]) -> Result<ParsedPacket, ParseError> {
    if packet.is_empty() {
        return Err(ParseError::PacketTooShort);
    }

    // IPv4 version is 4, IPv6 is 6. Located in the first 4 bits.
    let version = packet[0] >> 4;

    match version {
        4 => parse_ipv4(packet),
        6 => parse_ipv6(packet),
        _ => Err(ParseError::UnsupportedProtocol),
    }
}

fn parse_ipv4(packet: &[u8]) -> Result<ParsedPacket, ParseError> {
    // Minimum IPv4 header length is 20 bytes.
    if packet.len() < 20 {
        return Err(ParseError::PacketTooShort);
    }

    let version_ihl = packet[0];
    let ihl = (version_ihl & 0x0F) as usize;
    let header_len = ihl * 4;

    // Defense against malformed IHL fields.
    if header_len < 20 || packet.len() < header_len {
        return Err(ParseError::InvalidHeaderLength);
    }

    // The TUN interface may pad packets. Total Length specifies the actual IP packet size.
    let total_length = u16::from_be_bytes([packet[2], packet[3]]) as usize;
    let end_index = std::cmp::min(total_length, packet.len());
    
    if header_len > end_index {
        return Err(ParseError::InvalidHeaderLength);
    }

    let protocol = L4Protocol::from_u8(packet[9]);
    let src = Ipv4Addr::new(packet[12], packet[13], packet[14], packet[15]);
    let dst = Ipv4Addr::new(packet[16], packet[17], packet[18], packet[19]);

    let l3_payload = &packet[header_len..end_index];
    let (transport, l7_payload) = parse_transport(protocol, l3_payload)?;

    Ok(ParsedPacket {
        ip: IpInfo::V4 { src, dst, protocol },
        transport,
        l7_payload,
    })
}

fn parse_ipv6(packet: &[u8]) -> Result<ParsedPacket, ParseError> {
    // Minimum IPv6 header length is 40 bytes.
    if packet.len() < 40 {
        return Err(ParseError::PacketTooShort);
    }

    let next_header = packet[6];
    let protocol = L4Protocol::from_u8(next_header);

    // Zero-copy array slicing for IPv6 addresses. Bounds already checked above.
    let src = Ipv6Addr::from([
        packet[8], packet[9], packet[10], packet[11],
        packet[12], packet[13], packet[14], packet[15],
        packet[16], packet[17], packet[18], packet[19],
        packet[20], packet[21], packet[22], packet[23],
    ]);
    let dst = Ipv6Addr::from([
        packet[24], packet[25], packet[26], packet[27],
        packet[28], packet[29], packet[30], packet[31],
        packet[32], packet[33], packet[34], packet[35],
        packet[36], packet[37], packet[38], packet[39],
    ]);

    // Note: IPv6 Extension Headers (e.g., Hop-by-Hop) are skipped here.
    // For DLLs focused purely on DNS/TLS SNI, skipping to the base L4 is acceptable.
    // We rely on the actual buffer length rather than the payload length field.
    let l3_payload = if packet.len() > 40 { &packet[40..] } else { &[] };
    let (transport, l7_payload) = parse_transport(protocol, l3_payload)?;

    Ok(ParsedPacket {
        ip: IpInfo::V6 { src, dst, protocol },
        transport,
        l7_payload,
    })
}

fn parse_transport(protocol: L4Protocol, l3_payload: &[u8]) -> Result<(TransportInfo, &[u8]), ParseError> {
    match protocol {
        L4Protocol::Tcp => parse_tcp(l3_payload),
        L4Protocol::Udp => parse_udp(l3_payload),
        L4Protocol::Unknown(_) => Err(ParseError::UnknownTransportProtocol),
    }
}

fn parse_tcp(payload: &[u8]) -> Result<(TransportInfo, &[u8]), ParseError> {
    // Minimum TCP header length is 20 bytes.
    if payload.len() < 20 {
        return Err(ParseError::PacketTooShort);
    }

    let src_port = u16::from_be_bytes([payload[0], payload[1]]);
    let dst_port = u16::from_be_bytes([payload[2], payload[3]]);
    
    // Manually reading 4-byte sequences to avoid unsafe blocks or TryInto overhead,
    // guaranteeing panic-safety.
    let seq = u32::from_be_bytes([
        payload[4], payload[5], payload[6], payload[7],
    ]);
    let ack = u32::from_be_bytes([
        payload[8], payload[9], payload[10], payload[11],
    ]);

    let data_offset = ((payload[12] >> 4) as usize) * 4;
    let flags = payload[13];

    if data_offset < 20 || payload.len() < data_offset {
        return Err(ParseError::InvalidHeaderLength);
    }

    let l7_payload = &payload[data_offset..];
    
    Ok((
        TransportInfo::Tcp { src_port, dst_port, seq, ack, flags },
        l7_payload,
    ))
}

fn parse_udp(payload: &[u8]) -> Result<(TransportInfo, &[u8]), ParseError> {
    // Minimum UDP header length is 8 bytes.
    if payload.len() < 8 {
        return Err(ParseError::PacketTooShort);
    }

    let src_port = u16::from_be_bytes([payload[0], payload[1]]);
    let dst_port = u16::from_be_bytes([payload[2], payload[3]]);
    
    // Length field is at payload[4..6], but we strictly rely on actual slice bounds
    // to prevent potential memory exposure or panics from spoofed length fields.
    let l7_payload = &payload[8..];

    Ok((
        TransportInfo::Udp { src_port, dst_port },
        l7_payload,
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_ipv4_tcp_syn() {
        // Construct a minimal IPv4 TCP SYN packet
        let mut pkt = vec![0u8; 40];
        pkt[0] = 0x45; // Version 4, IHL 5 (20 bytes)
        pkt[2..4].copy_from_slice(&40u16.to_be_bytes()); // Total length 40
        pkt[9] = 6; // Protocol TCP
        pkt[12..16].copy_from_slice(&[192, 168, 1, 100]); // Src IP
        pkt[16..20].copy_from_slice(&[8, 8, 8, 8]); // Dst IP
        // TCP Header
        pkt[20..22].copy_from_slice(&12345u16.to_be_bytes()); // Src Port
        pkt[22..24].copy_from_slice(&443u16.to_be_bytes()); // Dst Port
        pkt[32] = 0x50; // Data offset 5 (20 bytes)
        pkt[33] = 0x02; // SYN flag

        let parsed = parse_packet(&pkt).unwrap();
        
        match parsed.ip {
            IpInfo::V4 { src, dst, protocol } => {
                assert_eq!(src, Ipv4Addr::new(192, 168, 1, 100));
                assert_eq!(dst, Ipv4Addr::new(8, 8, 8, 8));
                assert_eq!(protocol, L4Protocol::Tcp);
            },
            _ => panic!("Wrong IP version")
        }

        match parsed.transport {
            TransportInfo::Tcp { src_port, dst_port, flags, .. } => {
                assert_eq!(src_port, 12345);
                assert_eq!(dst_port, 443);
                assert_eq!(flags, 0x02);
            },
            _ => panic!("Wrong transport protocol")
        }
        
        assert!(parsed.l7_payload.is_empty()); // No payload in SYN
    }

    #[test]
    fn test_parse_ipv4_udp_dns() {
        let mut pkt = vec![0u8; 28 + 12]; // 20 IP + 8 UDP + 12 DNS
        pkt[0] = 0x45;
        pkt[2..4].copy_from_slice(&40u16.to_be_bytes());
        pkt[9] = 17; // Protocol UDP
        pkt[12..16].copy_from_slice(&[10, 0, 0, 1]);
        pkt[16..20].copy_from_slice(&[1, 1, 1, 1]);
        // UDP Header
        pkt[20..22].copy_from_slice(&5353u16.to_be_bytes()); // mDNS
        pkt[22..24].copy_from_slice(&53u16.to_be_bytes()); // DNS
        // Dummy DNS payload
        pkt[28..40].copy_from_slice(&[0xAA; 12]);

        let parsed = parse_packet(&pkt).unwrap();
        match parsed.transport {
            TransportInfo::Udp { src_port, dst_port } => {
                assert_eq!(src_port, 5353);
                assert_eq!(dst_port, 53);
            },
            _ => panic!("Wrong transport")
        }
        assert_eq!(parsed.l7_payload.len(), 12);
    }

    #[test]
    fn test_malformed_packet_short() {
        let pkt = vec![0u8; 15]; // Too short for IP header
        let result = parse_packet(&pkt);
        assert_eq!(result.err(), Some(ParseError::PacketTooShort));
    }

    #[test]
    fn test_malformed_ihl() {
        let mut pkt = vec![0u8; 40];
        pkt[0] = 0x4F; // IHL is 15 (60 bytes), but packet is 40 bytes
        let result = parse_packet(&pkt);
        assert_eq!(result.err(), Some(ParseError::InvalidHeaderLength));
    }
}
