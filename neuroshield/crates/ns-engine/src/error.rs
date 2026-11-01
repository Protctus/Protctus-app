//! # NeuroShield Engine — Error Types
//!
//! Unified error type for all engine operations. Every fallible path in the
//! parsing, analysis, and pipeline layers produces a [`PhishingError`].
//!
//! ## FFI Safety
//! `PhishingError` is **not** `#[repr(C)]` — it never crosses the FFI
//! boundary directly. At the JNI layer, errors are converted to i32 status
//! codes via [`Verdict`].

use std::fmt;

// ────────────────────────────────────────────────────────────
//  PhishingError
// ────────────────────────────────────────────────────────────

/// Every fallible operation in the engine returns `Result<_, PhishingError>`.
///
/// Variants are ordered by the layer that produces them:
/// packet parsing → TLS/DNS parsing → rule engine → ML inference → pipeline.
#[derive(Debug, thiserror::Error)]
pub enum PhishingError {
    // ── Packet layer ──
    #[error("IP packet too short: need {needed} bytes, got {got}")]
    IpTruncated { needed: usize, got: usize },

    #[error("unsupported IP version: {0}")]
    IpUnsupportedVersion(u8),

    #[error("IPv4 header checksum mismatch: expected 0x{expected:04X}, got 0x{computed:04X}")]
    IpV4Checksum { expected: u16, computed: u16 },

    // ── Transport layer ──
    #[error("TCP header truncated: need {needed} bytes, got {got}")]
    TcpTruncated { needed: usize, got: usize },

    #[error("UDP header truncated: need {needed} bytes, got {got}")]
    UdpTruncated { needed: usize, got: usize },

    // ── DNS layer ──
    #[error("DNS packet truncated: need {needed} bytes, got {got}")]
    DnsTruncated { needed: usize, got: usize },

    #[error("DNS label length {len} exceeds remaining buffer ({remaining})")]
    DnsLabelOverflow { len: usize, remaining: usize },

    #[error("DNS pointer offset {0} is outside packet bounds")]
    DnsPointerOutOfBounds(u16),

    #[error("DNS pointer loop detected at offset {0}")]
    DnsPointerLoop(u16),

    #[error("DNS response code: {rcode}")]
    DnsResponseError { rcode: u8 },

    // ── TLS layer ──
    #[error("TLS record truncated: need {needed} bytes, got {got}")]
    TlsTruncated { needed: usize, got: usize },

    #[error("unexpected TLS content type: 0x{0:02X}")]
    TlsUnexpectedContentType(u8),

    #[error("unexpected TLS record version: 0x{0:04X}")]
    TlsUnexpectedVersion(u16),

    #[error("TLS handshake type is not ClientHello (got 0x{0:02X})")]
    TlsNotClientHello(u8),

    #[error("TLS extension truncated at offset {offset}: need {needed} bytes, got {got}")]
    TlsExtensionTruncated {
        offset: usize,
        needed: usize,
        got: usize,
    },

    #[error("SNI hostname is not valid UTF-8")]
    TlsSniInvalidUtf8,

    #[error("JA3 computation failed: {0}")]
    TlsJa3Failed(String),

    // ── QUIC layer ──
    #[error("QUIC packet truncated: need {needed} bytes, got {got}")]
    QuicTruncated { needed: usize, got: usize },

    #[error("QUIC long-header bit not set (first byte: 0x{0:02X})")]
    QuicNotLongHeader(u8),

    #[error("QUIC version mismatch: expected 0x{expected:08X}, got 0x{got:08X}")]
    QuicVersionMismatch { expected: u32, got: u32 },

    #[error("QUIC Initial packet decryption failed: {0}")]
    QuicDecryptionFailed(String),

    // ── Pipeline ──
    #[error("layer {layer} exceeded latency budget: {elapsed_us}μs > {budget_us}μs")]
    LayerTimeout {
        layer: u8,
        elapsed_us: u64,
        budget_us: u64,
    },

    #[error("asset load failed: {path} — {reason}")]
    AssetLoadFailed { path: String, reason: String },

    #[error("manifest version mismatch: engine {engine}, asset {asset}")]
    ManifestVersionMismatch { engine: u32, asset: u32 },

    #[error("asset integrity check failed: {path}")]
    AssetIntegrityFailed { path: String },

    // ── Catch-all ──
    #[error("nom parser incomplete: need more data")]
    Incomplete,

    #[error("{0}")]
    Other(String),
}

// ────────────────────────────────────────────────────────────
//  ParseError — lightweight nom adapter
// ────────────────────────────────────────────────────────────

/// Converts `nom::Err<Incomplete>` into `PhishingError::Incomplete` and
/// `nom::Err::Error` / `nom::Err::Failure` into the appropriate variant.
///
/// This is a convenience trait so parser functions can return
/// `Result<Output, PhishingError>` directly instead of leaking nom types
/// through the crate's public API.
pub trait NomResultExt<O> {
    fn map_err_phishing(self) -> Result<O, PhishingError>;
}

impl<O, E: fmt::Debug> NomResultExt<O> for nom::IResult<&[u8], O, E> {
    fn map_err_phishing(self) -> Result<O, PhishingError> {
        match self {
            Ok((_remaining, output)) => Ok(output),
            Err(nom::Err::Incomplete(_)) => Err(PhishingError::Incomplete),
            Err(nom::Err::Error(e)) | Err(nom::Err::Failure(e)) => {
                // nom error kinds are too granular to map 1:1; callers
                // should use the typed parse functions which produce
                // specific PhishingError variants instead.
                log::trace!("nom parse failed: {:?}", e);
                Err(PhishingError::Incomplete)
            }
        }
    }
}
