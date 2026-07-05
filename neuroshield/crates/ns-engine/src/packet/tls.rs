//! TLS ClientHello parsing.
//!
//! Zero-copy parser for TLS 1.2/1.3 ClientHello records. Extracts:
//! - SNI hostname (Server Name Indication)
//! - JA3 fingerprint (MD5 of version + cipher suites + extensions)
//! - Cipher suites, supported groups, EC point formats
//! - TLS extension types (for ECH detection, ALPN, etc.)
//!
//! Does NOT decrypt or parse any encrypted data.

use crate::error::PhishingError;
use md5::{Digest, Md5};

// ────────────────────────────────────────────────────────────
//  Types
// ────────────────────────────────────────────────────────────

/// Parsed TLS ClientHello metadata.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ClientHelloInfo {
    /// TLS record version (e.g., 0x0301 for TLS 1.0, 0x0303 for TLS 1.2).
    pub record_version: u16,
    /// Handshake version from the ClientHello (e.g., 0x0303 for TLS 1.2).
    pub handshake_version: u16,
    /// Random bytes (32 bytes).
    pub random: [u8; 32],
    /// Session ID (may be empty for TLS 1.3).
    pub session_id: Vec<u8>,
    /// List of cipher suites.
    pub cipher_suites: Vec<u16>,
    /// List of compression methods.
    pub compression_methods: Vec<u8>,
    /// Supported TLS extensions.
    pub extensions: Vec<TlsExtension>,
    /// SNI hostname, if present.
    pub sni: Option<String>,
    /// JA3 MD5 fingerprint (32 hex chars).
    pub ja3_md5: String,
    /// ALPN protocols, if present.
    pub alpn: Vec<String>,
    /// Supported groups (elliptic curves), if present.
    pub supported_groups: Vec<u16>,
    /// EC point formats, if present.
    pub ec_point_formats: Vec<u8>,
    /// Whether ECH (Encrypted ClientHello) extension is present.
    pub has_ech: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TlsExtension {
    pub extension_type: u16,
    pub data: Vec<u8>,
}

// ────────────────────────────────────────────────────────────
//  Public API
// ────────────────────────────────────────────────────────────

/// Parse a TLS ClientHello from a raw TCP payload.
///
/// # Errors
/// - `PhishingError::TlsTruncated` if the buffer is too short.
/// - `PhishingError::TlsUnexpectedContentType` if not a handshake record.
/// - `PhishingError::TlsNotClientHello` if the handshake type is wrong.
pub fn parse_tls_client_hello(data: &[u8]) -> Result<ClientHelloInfo, PhishingError> {
    if data.len() < 5 {
        return Err(PhishingError::TlsTruncated {
            needed: 5,
            got: data.len(),
        });
    }

    // TLS record header:
    // [0] content_type (0x16 = handshake)
    // [1-2] version
    // [3-4] length
    let content_type = data[0];
    if content_type != 0x16 {
        return Err(PhishingError::TlsUnexpectedContentType(content_type));
    }

    let record_version = u16::from_be_bytes([data[1], data[2]]);
    let record_length = u16::from_be_bytes([data[3], data[4]]) as usize;

    if data.len() < 5 + record_length {
        return Err(PhishingError::TlsTruncated {
            needed: 5 + record_length,
            got: data.len(),
        });
    }

    let handshake_start = 5;
    let handshake_type = data[handshake_start];
    if handshake_type != 0x01 {
        return Err(PhishingError::TlsNotClientHello(handshake_type));
    }

    let handshake_length = u24_to_usize(&data[handshake_start + 1..handshake_start + 4]);
    let clienthello_end = handshake_start + 4 + handshake_length;
    if data.len() < clienthello_end {
        return Err(PhishingError::TlsTruncated {
            needed: clienthello_end,
            got: data.len(),
        });
    }

    let mut pos = handshake_start + 4;

    // ClientHello fields:
    // [pos..pos+2] version
    // [pos+2..pos+34] random
    // [pos+34] session_id_len
    // session_id
    // cipher_suites_len (2 bytes)
    // cipher_suites
    // compression_methods_len (1 byte)
    // compression_methods
    // extensions_len (2 bytes)
    // extensions

    let handshake_version = u16::from_be_bytes([data[pos], data[pos + 1]]);
    pos += 2;

    let mut random = [0u8; 32];
    random.copy_from_slice(&data[pos..pos + 32]);
    pos += 32;

    let session_id_len = data[pos] as usize;
    pos += 1;
    let session_id = data[pos..pos + session_id_len].to_vec();
    pos += session_id_len;

    let cipher_suites_len = u16::from_be_bytes([data[pos], data[pos + 1]]) as usize;
    pos += 2;
    let mut cipher_suites = Vec::with_capacity(cipher_suites_len / 2);
    for i in (0..cipher_suites_len).step_by(2) {
        if pos + i + 1 < data.len() {
            let cs = u16::from_be_bytes([data[pos + i], data[pos + i + 1]]);
            cipher_suites.push(cs);
        }
    }
    pos += cipher_suites_len;

    let compression_methods_len = data[pos] as usize;
    pos += 1;
    let compression_methods = data[pos..pos + compression_methods_len].to_vec();
    pos += compression_methods_len;

    // Extensions.
    let mut extensions = Vec::new();
    let mut sni: Option<String> = None;
    let mut alpn: Vec<String> = Vec::new();
    let mut supported_groups: Vec<u16> = Vec::new();
    let mut ec_point_formats: Vec<u8> = Vec::new();
    let mut has_ech = false;

    if pos + 2 <= data.len() {
        let extensions_len = u16::from_be_bytes([data[pos], data[pos + 1]]) as usize;
        pos += 2;
        let extensions_end = pos + extensions_len;
        if extensions_end > data.len() {
            return Err(PhishingError::TlsExtensionTruncated {
                offset: pos,
                needed: extensions_end,
                got: data.len(),
            });
        }

        while pos + 4 <= extensions_end {
            let ext_type = u16::from_be_bytes([data[pos], data[pos + 1]]);
            let ext_len = u16::from_be_bytes([data[pos + 2], data[pos + 3]]) as usize;
            pos += 4;
            if pos + ext_len > extensions_end {
                return Err(PhishingError::TlsExtensionTruncated {
                    offset: pos,
                    needed: pos + ext_len,
                    got: extensions_end,
                });
            }
            let ext_data = data[pos..pos + ext_len].to_vec();
            pos += ext_len;

            // Parse known extensions.
            match ext_type {
                0x0000 => {
                    // SNI
                    if let Ok(hostname) = parse_sni_extension(&ext_data) {
                        sni = Some(hostname);
                    }
                }
                0x0010 => {
                    // ALPN
                    alpn = parse_alpn_extension(&ext_data);
                }
                0x000A => {
                    // Supported Groups (Elliptic Curves)
                    supported_groups = parse_supported_groups(&ext_data);
                }
                0x000B => {
                    // EC Point Formats
                    ec_point_formats = parse_ec_point_formats(&ext_data);
                }
                0xFF01 | 0xFD00..=0xFDFF => {
                    // ECH (draft, experimental range)
                    has_ech = true;
                }
                _ => {}
            }

            extensions.push(TlsExtension {
                extension_type: ext_type,
                data: ext_data,
            });
        }
    }

    // Compute JA3 fingerprint.
    let ja3_md5 = compute_ja3(
        handshake_version,
        &cipher_suites,
        &extensions,
        &supported_groups,
        &ec_point_formats,
    );

    Ok(ClientHelloInfo {
        record_version,
        handshake_version,
        random,
        session_id,
        cipher_suites,
        compression_methods,
        extensions,
        sni,
        ja3_md5,
        alpn,
        supported_groups,
        ec_point_formats,
        has_ech,
    })
}

// ────────────────────────────────────────────────────────────
//  JA3 Computation
// ────────────────────────────────────────────────────────────

fn compute_ja3(
    version: u16,
    cipher_suites: &[u16],
    extensions: &[TlsExtension],
    supported_groups: &[u16],
    ec_point_formats: &[u8],
) -> String {
    // JA3 format:
    // TLSVersion,Ciphers,Extensions,EllipticCurves,EllipticCurvePointFormats
    let mut ja3 = String::new();

    // Version
    ja3.push_str(&version.to_string());
    ja3.push(',');

    // Cipher suites
    let cs_str: Vec<String> = cipher_suites.iter().map(|cs| cs.to_string()).collect();
    ja3.push_str(&cs_str.join("-"));
    ja3.push(',');

    // Extensions (only the types, not data)
    let ext_types: Vec<u16> = extensions.iter().map(|e| e.extension_type).collect();
    let ext_str: Vec<String> = ext_types.iter().map(|e| e.to_string()).collect();
    ja3.push_str(&ext_str.join("-"));
    ja3.push(',');

    // Elliptic curves
    let sg_str: Vec<String> = supported_groups.iter().map(|sg| sg.to_string()).collect();
    ja3.push_str(&sg_str.join("-"));
    ja3.push(',');

    // EC point formats
    let epf_str: Vec<String> = ec_point_formats.iter().map(|epf| epf.to_string()).collect();
    ja3.push_str(&epf_str.join("-"));

    // MD5 hash
    let mut hasher = Md5::new();
    hasher.update(ja3.as_bytes());
    let result = hasher.finalize();
    hex::encode(result)
}

// ────────────────────────────────────────────────────────────
//  Extension Parsers
// ────────────────────────────────────────────────────────────

fn parse_sni_extension(data: &[u8]) -> Result<String, PhishingError> {
    if data.len() < 5 {
        return Err(PhishingError::TlsSniInvalidUtf8);
    }
    let list_len = u16::from_be_bytes([data[0], data[1]]) as usize;
    if list_len == 0 || data.len() < 2 + list_len {
        return Err(PhishingError::TlsSniInvalidUtf8);
    }
    // SNI entry: [0] name_type (0 = hostname), [1-2] name_len, [3..] name
    let name_type = data[2];
    let name_len = u16::from_be_bytes([data[3], data[4]]) as usize;
    if name_type != 0 || 5 + name_len > data.len() {
        return Err(PhishingError::TlsSniInvalidUtf8);
    }
    let name = &data[5..5 + name_len];
    std::str::from_utf8(name)
        .map(|s| s.to_string())
        .map_err(|_| PhishingError::TlsSniInvalidUtf8)
}

fn parse_alpn_extension(data: &[u8]) -> Vec<String> {
    let mut alpns = Vec::new();
    if data.len() < 2 {
        return alpns;
    }
    let list_len = u16::from_be_bytes([data[0], data[1]]) as usize;
    if list_len == 0 || data.len() < 2 + list_len {
        return alpns;
    }
    let mut pos = 2;
    while pos < data.len() {
        let len = data[pos] as usize;
        pos += 1;
        if pos + len > data.len() {
            break;
        }
        let proto = &data[pos..pos + len];
        if let Ok(s) = std::str::from_utf8(proto) {
            alpns.push(s.to_string());
        }
        pos += len;
    }
    alpns
}

fn parse_supported_groups(data: &[u8]) -> Vec<u16> {
    let mut groups = Vec::new();
    if data.len() < 2 {
        return groups;
    }
    let len = u16::from_be_bytes([data[0], data[1]]) as usize;
    if data.len() < 2 + len {
        return groups;
    }
    for i in (0..len).step_by(2) {
        if 2 + i + 1 < data.len() {
            let g = u16::from_be_bytes([data[2 + i], data[2 + i + 1]]);
            groups.push(g);
        }
    }
    groups
}

fn parse_ec_point_formats(data: &[u8]) -> Vec<u8> {
    let mut formats = Vec::new();
    if data.is_empty() {
        return formats;
    }
    let len = data[0] as usize;
    let end = 1 + len;
    if data.len() < end {
        return formats;
    }
    formats.extend_from_slice(&data[1..end]);
    formats
}

// ────────────────────────────────────────────────────────────
//  Helpers
// ────────────────────────────────────────────────────────────

fn u24_to_usize(bytes: &[u8]) -> usize {
    if bytes.len() < 3 {
        return 0;
    }
    ((bytes[0] as usize) << 16) | ((bytes[1] as usize) << 8) | (bytes[2] as usize)
}

// ────────────────────────────────────────────────────────────
//  Conversion to engine types
// ────────────────────────────────────────────────────────────

impl From<&ClientHelloInfo> for crate::types::TlsClientHello {
    fn from(info: &ClientHelloInfo) -> Self {
        crate::types::TlsClientHello {
            record_version: info.record_version,
            handshake_version: info.handshake_version,
            sni: info.sni.clone(),
            ja3_fingerprint: Some(info.ja3_md5.clone()),
            cipher_suites: info.cipher_suites.clone(),
            extension_types: info.extensions.iter().map(|e| e.extension_type).collect(),
            supported_groups: info.supported_groups.clone(),
            ec_point_formats: info.ec_point_formats.clone(),
            alpn: info.alpn.clone(),
            has_ech: info.has_ech,
        }
    }
}
