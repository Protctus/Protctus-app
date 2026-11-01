//! DNS query and response parsing.
//!
//! Zero-copy parser for DNS packet metadata. Handles label compression
//! (pointers), CNAME chains, and basic record extraction needed for
//! L1 deterministic rules (TTL drops, fast-flux, etc.).

use crate::error::PhishingError;
use crate::types::DnsResponse;

// ────────────────────────────────────────────────────────────
//  Types
// ────────────────────────────────────────────────────────────

/// Parsed DNS packet with extracted metadata.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DnsPacket {
    /// Transaction ID.
    pub tx_id: u16,
    /// Flags field (e.g. QR, OPCODE, AA, TC, RD, RA, RCODE).
    pub flags: u16,
    /// Number of questions.
    pub qdcount: u16,
    /// Number of answers.
    pub ancount: u16,
    /// Number of authority records.
    pub nscount: u16,
    /// Number of additional records.
    pub arcount: u16,
    /// Parsed answers.
    pub answers: Vec<DnsAnswer>,
    /// Parsed CNAME chains.
    pub cname_chain: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DnsAnswer {
    A { name: String, ttl: u32, ip: [u8; 4] },
    Cname { name: String, ttl: u32, target: String },
    Ns { name: String, ttl: u32, ns: String },
    Other { name: String, rtype: u16, ttl: u32, rdata: Vec<u8> },
}

// ────────────────────────────────────────────────────────────
//  Public API
// ────────────────────────────────────────────────────────────

/// Parse a raw DNS packet (query or response).
///
/// # Errors
/// `PhishingError::DnsTruncated` if the buffer is too short.
/// `PhishingError::DnsPointerOutOfBounds` for invalid compression pointers.
/// `PhishingError::DnsPointerLoop` for circular compression pointers.
pub fn parse_dns_packet(data: &[u8]) -> Result<DnsPacket, PhishingError> {
    if data.len() < 12 {
        return Err(PhishingError::DnsTruncated {
            needed: 12,
            got: data.len(),
        });
    }

    let tx_id = u16::from_be_bytes([data[0], data[1]]);
    let flags = u16::from_be_bytes([data[2], data[3]]);
    let qdcount = u16::from_be_bytes([data[4], data[5]]);
    let ancount = u16::from_be_bytes([data[6], data[7]]);
    let nscount = u16::from_be_bytes([data[8], data[9]]);
    let arcount = u16::from_be_bytes([data[10], data[11]]);

    let mut offset = 12usize;

    // Skip questions — we don't need them for metadata extraction.
    for _ in 0..qdcount {
        skip_name(data, &mut offset)?;
        if data.len() < offset + 4 {
            return Err(PhishingError::DnsTruncated {
                needed: offset + 4,
                got: data.len(),
            });
        }
        offset += 4; // QTYPE + QCLASS
    }

    // Parse answers.
    let mut answers = Vec::with_capacity(ancount as usize);
    let mut cname_chain = Vec::new();
    for _ in 0..ancount {
        let (name, name_end) = read_name(data, offset)?;
        offset = name_end;
        if data.len() < offset + 10 {
            return Err(PhishingError::DnsTruncated {
                needed: offset + 10,
                got: data.len(),
            });
        }
        let rtype = u16::from_be_bytes([data[offset], data[offset + 1]]);
        let rclass = u16::from_be_bytes([data[offset + 2], data[offset + 3]]);
        let ttl = u32::from_be_bytes([data[offset + 4], data[offset + 5], data[offset + 6], data[offset + 7]]);
        let rdlength = u16::from_be_bytes([data[offset + 8], data[offset + 9]]) as usize;
        offset += 10;

        if data.len() < offset + rdlength {
            return Err(PhishingError::DnsTruncated {
                needed: offset + rdlength,
                got: data.len(),
            });
        }

        match rtype {
            1 if rdlength == 4 && rclass == 1 => {
                // A record
                let ip = [data[offset], data[offset + 1], data[offset + 2], data[offset + 3]];
                answers.push(DnsAnswer::A { name, ttl, ip });
            }
            5 if rclass == 1 => {
                // CNAME record
                let (target, _) = read_name(data, offset)?;
                cname_chain.push(target.clone());
                answers.push(DnsAnswer::Cname { name, ttl, target });
            }
            2 if rclass == 1 => {
                // NS record
                let (ns, _) = read_name(data, offset)?;
                answers.push(DnsAnswer::Ns { name, ttl, ns });
            }
            _ => {
                let rdata = data[offset..offset + rdlength].to_vec();
                answers.push(DnsAnswer::Other { name, rtype, ttl, rdata });
            }
        }
        offset += rdlength;
    }

    Ok(DnsPacket {
        tx_id,
        flags,
        qdcount,
        ancount,
        nscount,
        arcount,
        answers,
        cname_chain,
    })
}

/// Convert a parsed `DnsPacket` into the FFI-safe `DnsResponse` struct.
pub fn dns_packet_to_response(pkt: &DnsPacket) -> DnsResponse {
    let a_records: Vec<[u8; 4]> = pkt
        .answers
        .iter()
        .filter_map(|a| match a {
            DnsAnswer::A { ip, .. } => Some(*ip),
            _ => None,
        })
        .collect();

    let a_record_count = a_records.len() as u8;
    let cname_count = pkt.cname_chain.len() as u8;
    let cname_depth = if cname_count > 0 { cname_count } else { 0 };

    let rcode = (pkt.flags & 0x000F) as u8;

    let ns_count = pkt
        .answers
        .iter()
        .filter(|a| matches!(a, DnsAnswer::Ns { .. }))
        .count() as u8;

    let ttl = pkt
        .answers
        .first()
        .map(|a| match a {
            DnsAnswer::A { ttl, .. } => *ttl,
            DnsAnswer::Cname { ttl, .. } => *ttl,
            DnsAnswer::Ns { ttl, .. } => *ttl,
            DnsAnswer::Other { ttl, .. } => *ttl,
        })
        .unwrap_or(0);

    // Fast-flux: many A records with low TTL.
    let fast_flux = a_record_count > 3 && ttl < 300;

    let mut a_records_arr = [[0u8; 4]; 8];
    for (i, ip) in a_records.iter().take(8).enumerate() {
        a_records_arr[i] = *ip;
    }

    DnsResponse {
        ttl,
        a_record_count,
        cname_count,
        cname_depth,
        rcode,
        ns_count,
        fast_flux,
        a_records: a_records_arr,
    }
}

// ────────────────────────────────────────────────────────────
//  Internal: Name Decompression
// ────────────────────────────────────────────────────────────

/// Read a DNS name (handling compression pointers). Returns (name, new_offset).
fn read_name(data: &[u8], offset: usize) -> Result<(String, usize), PhishingError> {
    let mut pos = offset;
    let mut name_parts = Vec::new();
    let mut jumped = false;
    let mut jump_start = offset;
    let max_jumps = data.len(); // Safety bound.
    let mut jump_count = 0;

    loop {
        if pos >= data.len() {
            return Err(PhishingError::DnsLabelOverflow {
                len: 0,
                remaining: 0,
            });
        }

        let len = data[pos];
        if len == 0 {
            pos += 1;
            break;
        }

        if len & 0xC0 == 0xC0 {
            // Compression pointer.
            if pos + 1 >= data.len() {
                return Err(PhishingError::DnsTruncated {
                    needed: pos + 2,
                    got: data.len(),
                });
            }
            let ptr = u16::from_be_bytes([data[pos], data[pos + 1]]) & 0x3FFF;
            if ptr as usize >= data.len() {
                return Err(PhishingError::DnsPointerOutOfBounds(ptr));
            }
            if !jumped {
                jump_start = pos + 2;
            }
            pos = ptr as usize;
            jumped = true;
            jump_count += 1;
            if jump_count > max_jumps {
                return Err(PhishingError::DnsPointerLoop(ptr));
            }
            continue;
        }

        let label_len = len as usize;
        if pos + 1 + label_len > data.len() {
            return Err(PhishingError::DnsLabelOverflow {
                len: label_len,
                remaining: data.len() - pos - 1,
            });
        }

        // Verify valid label characters (RFC 1035). DNS labels are 1-63 octets.
        if label_len == 0 || label_len > 63 {
            return Err(PhishingError::DnsLabelOverflow {
                len: label_len,
                remaining: data.len() - pos - 1,
            });
        }

        let label = &data[pos + 1..pos + 1 + label_len];
        match std::str::from_utf8(label) {
            Ok(s) => name_parts.push(s.to_string()),
            Err(_) => {
                // Invalid UTF-8 in label; store as lossy replacement.
                name_parts.push(String::from_utf8_lossy(label).to_string());
            }
        }
        pos += 1 + label_len;
    }

    let final_offset = if jumped { jump_start } else { pos };
    Ok((name_parts.join("."), final_offset))
}

/// Skip a DNS name (for questions we don't need to store).
fn skip_name(data: &[u8], offset: &mut usize) -> Result<(), PhishingError> {
    let start = *offset;
    let mut pos = start;
    let max_jumps = data.len();
    let mut jump_count = 0;
    let mut jumped = false;
    let mut jump_start = start;

    loop {
        if pos >= data.len() {
            return Err(PhishingError::DnsLabelOverflow {
                len: 0,
                remaining: 0,
            });
        }
        let len = data[pos];
        if len == 0 {
            pos += 1;
            break;
        }
        if len & 0xC0 == 0xC0 {
            if pos + 1 >= data.len() {
                return Err(PhishingError::DnsTruncated {
                    needed: pos + 2,
                    got: data.len(),
                });
            }
            let ptr = u16::from_be_bytes([data[pos], data[pos + 1]]) & 0x3FFF;
            if ptr as usize >= data.len() {
                return Err(PhishingError::DnsPointerOutOfBounds(ptr));
            }
            if !jumped {
                jump_start = pos + 2;
            }
            pos = ptr as usize;
            jumped = true;
            jump_count += 1;
            if jump_count > max_jumps {
                return Err(PhishingError::DnsPointerLoop(ptr));
            }
            continue;
        }
        let label_len = len as usize;
        if label_len > 63 || pos + 1 + label_len > data.len() {
            return Err(PhishingError::DnsLabelOverflow {
                len: label_len,
                remaining: data.len() - pos - 1,
            });
        }
        pos += 1 + label_len;
    }

    *offset = if jumped { jump_start } else { pos };
    Ok(())
}
