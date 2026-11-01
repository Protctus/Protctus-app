//! # NeuroShield Engine — Core Types
//!
//! FFI-safe struct definitions shared across every layer of the analysis
//! pipeline. These structs are the **contract** between the packet-parsing
//! layer (Module 0) and the detection layers (L0–L4).
//!
//! ## Safety model
//!
//! - **Safe types** (no `#[repr(C)]`): Used internally within Rust.
//!   These own `String`, `Vec`, `Option`, etc., for idiomatic, ergonomic code.
//! - **FFI types** (`#[repr(C)]`): Exposed to C/JNI. **Never** contain
//!   `String`, `Vec`, or `Option`. Heap data is referenced via `*const c_char`
//!   or packed into fixed-size arrays. Conversion is explicit and safe.

// Re-export safe types for internal use.
pub use safe_types::*;

// ────────────────────────────────────────────────────────────
//  Constants
// ────────────────────────────────────────────────────────────

pub const MAX_A_RECORDS: usize = 8;
pub const MAX_PACKET_SIZES: usize = 16;
pub const MAX_CIPHER_SUITES: usize = 32;
pub const MAX_EXTENSION_TYPES: usize = 32;
pub const MAX_SUPPORTED_GROUPS: usize = 16;
pub const MAX_EC_POINT_FORMATS: usize = 8;
pub const MAX_CERT_CHAIN: usize = 4;

// ────────────────────────────────────────────────────────────
//  Protocol Constants
// ────────────────────────────────────────────────────────────

/// IANA protocol numbers used in the IP header's `protocol` field.
pub mod protocol {
    pub const IPPROTO_ICMP: u8 = 1;
    pub const IPPROTO_TCP: u8 = 6;
    pub const IPPROTO_UDP: u8 = 17;
    pub const IPPROTO_ICMPV6: u8 = 58;

    /// QUIC uses UDP as the transport layer; this constant is used
    /// by the pipeline to identify QUIC flows by destination port.
    pub const QUIC_DEFAULT_PORT: u16 = 443;
}

/// TLS extension type IDs referenced by the engine.
pub mod tls_ext {
    pub const SERVER_NAME: u16 = 0x0000;
    pub const SUPPORTED_GROUPS: u16 = 0x000A;
    pub const EC_POINT_FORMATS: u16 = 0x000B;
    pub const ALPN: u16 = 0x0010;
    pub const RENEGOTIATION_INFO: u16 = 0xFF01;
    pub const ECH_MIN: u16 = 0xFD00;
    pub const ECH_MAX: u16 = 0xFDFF;
}

// ────────────────────────────────────────────────────────────
//  Verdict
// ────────────────────────────────────────────────────────────

/// Final decision from the analysis pipeline.
///
/// The discriminant values are stable across FFI:
/// - 0: Allow — no threat detected, forward the packet.
/// - 1: Block — confirmed phishing/malicious, drop the packet.
/// - 2: Escalate — inconclusive, push to higher layer.
/// - -1: Error — analysis failed; the packet should still be forwarded
///   (fail-open) to avoid breaking the user's connectivity.
#[repr(i32)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Verdict {
    Allow = 0,
    Block = 1,
    Escalate = 2,
    Error = -1,
}

impl Verdict {
    /// Convert from the i32 used at the JNI/C boundary.
    ///
    /// Returns `None` for unrecognized values instead of panicking.
    pub fn from_i32(v: i32) -> Option<Self> {
        match v {
            0 => Some(Verdict::Allow),
            1 => Some(Verdict::Block),
            2 => Some(Verdict::Escalate),
            -1 => Some(Verdict::Error),
            _ => None,
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Safe Types (internal Rust use only — not FFI-safe)
// ────────────────────────────────────────────────────────────

mod safe_types {
    use super::{Verdict, MAX_A_RECORDS};
    use std::ffi::c_void;

    // ── DnsResponse ──

    /// Extracted metadata from a DNS response packet.
    ///
    /// Only the fields visible **without** decrypting DoH are populated.
    #[derive(Debug, Clone, Default)]
    pub struct DnsResponse {
        /// Time-to-live from the first A record answer (seconds).
        pub ttl: u32,

        /// Number of A records in the answer section.
        pub a_record_count: u8,

        /// Number of CNAME records in the answer section.
        pub cname_count: u8,

        /// Depth of the CNAME chain (resolves up to 8 hops).
        pub cname_depth: u8,

        /// DNS response code (0 = NOERROR, 3 = NXDOMAIN, etc.).
        pub rcode: u8,

        /// Number of name server records.
        pub ns_count: u8,

        /// Fast-flux indicator: many A records with low TTL.
        pub fast_flux: bool,

        /// Resolved A records (up to `MAX_A_RECORDS`; zero-padded).
        pub a_records: [[u8; 4]; MAX_A_RECORDS],
    }

    // ── TlsClientHello ──

    /// Extracted metadata from a TLS ClientHello record.
    ///
    /// Populated by the TLS parser from the unencrypted ClientHello bytes
    /// visible before the handshake completes.
    #[derive(Debug, Clone, Default)]
    pub struct TlsClientHello {
        /// TLS record version (e.g., 0x0303 for TLS 1.2).
        pub record_version: u16,

        /// Handshake version from the ClientHello body.
        pub handshake_version: u16,

        /// SNI hostname, if the extension was present.
        pub sni: Option<String>,

        /// JA3 MD5 fingerprint as a 32-character hex string.
        pub ja3_fingerprint: Option<String>,

        /// Cipher suites offered by the client.
        pub cipher_suites: Vec<u16>,

        /// TLS extension types in order of appearance.
        pub extension_types: Vec<u16>,

        /// Supported groups (elliptic curves).
        pub supported_groups: Vec<u16>,

        /// EC point formats.
        pub ec_point_formats: Vec<u8>,

        /// ALPN protocols (e.g., "h2", "http/1.1").
        pub alpn: Vec<String>,

        /// Whether ECH (Encrypted ClientHello) is present.
        pub has_ech: bool,
    }

    // ── CertMetadata ──

    /// Metadata extracted from a TLS certificate (without decrypting the
    /// session, certificates are visible in the ServerHello / Certificate
    /// handshake messages).
    #[derive(Debug, Clone, Default)]
    pub struct CertMetadata {
        /// SHA-256 fingerprint of the certificate (hex string).
        pub fingerprint: String,

        /// Issuer Common Name (e.g., "Let's Encrypt R3").
        pub issuer_cn: String,

        /// Subject Common Name (e.g., "example.com").
        pub subject_cn: String,

        /// Not-before timestamp (Unix epoch seconds).
        pub not_before: u64,

        /// Not-after timestamp (Unix epoch seconds).
        pub not_after: u64,

        /// Subject Alternative Names (up to `MAX_CERT_CHAIN` are stored).
        pub sans: Vec<String>,

        /// True if the issuer is Let's Encrypt (low-cost CA, often used
        /// by newly-registered phishing domains).
        pub is_lets_encrypt: bool,

        /// True if the certificate is self-signed.
        pub is_self_signed: bool,
    }

    // ── SignalBundle ──

    /// The full input bundle for the analysis pipeline (L0–L4).
    ///
    /// Assembled by [`crate::signal::SignalBundleBuilder`] from parsed
    /// packet data and passed through the detection layers.
    #[derive(Debug, Clone, Default)]
    pub struct SignalBundle {
        /// Destination domain (from SNI or DNS query).
        pub domain: String,

        /// Destination IP address (as a string, e.g., "142.250.190.46").
        pub ip: String,

        /// Destination port.
        pub port: u16,

        /// Transport protocol (`IPPROTO_TCP` / `IPPROTO_UDP`).
        pub protocol: u8,

        /// SNI hostname from TLS ClientHello, if TLS was observed.
        pub sni: Option<String>,

        /// Parsed DNS response metadata, if a DNS query was observed.
        pub dns_response: Option<DnsResponse>,

        /// TLS record version, if a ClientHello was observed.
        pub tls_version: Option<u16>,

        /// JA3 fingerprint, if a ClientHello was observed.
        pub ja3_fingerprint: Option<String>,

        /// Certificate chain metadata, if a Certificate was observed.
        pub cert_chain: Option<Vec<CertMetadata>>,

        /// Timestamp of the observation (monotonic, microseconds).
        pub timestamp: u64,

        /// Recent packet sizes for traffic-pattern analysis
        /// (up to `MAX_PACKET_SIZES` entries).
        pub packet_sizes: Vec<usize>,

        /// Opaque pointer to the native packet handle (owned by the
        /// VPN layer, not by this crate). Used for pass-through decisions.
        pub native_packet_tag: *mut c_void,
    }

    // ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
    //  Safety: SignalBundle contains a raw pointer.
    // ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄

    // The `*mut c_void` field makes SignalBundle !Send and !Sync, which
    // is intentional: the native_packet_tag is valid only on the thread
    // that owns the packet. We manually impl Send where it's needed.
    //
    // The Default impl sets native_packet_tag to null_mut, which is safe
    // (no pointer ownership). Cloning copies the raw pointer without
    // duplicating ownership, which is safe as long as the clone is not
    // used to free the packet.
    //
    // SAFETY: We assert that SignalBundle can be Sent across threads
    // despite containing a raw pointer, because the pointer is opaque
    // and the VPN layer maintains sole ownership of the underlying memory.
    // No code in this crate dereferences native_packet_tag.
    unsafe impl Send for SignalBundle {}

    // ── PipelineResult ──

    /// Result of running the analysis pipeline on a [`SignalBundle`].
    #[derive(Debug, Clone)]
    pub struct PipelineResult {
        /// Final verdict.
        pub verdict: Verdict,
        /// Layer that made the decision (0–4).
        pub deciding_layer: u8,
        /// Confidence score in [0.0, 1.0].
        pub confidence: f32,
        /// Human-readable reason for the verdict (for logging/debugging).
        pub reason: String,
    }

    impl Default for PipelineResult {
        fn default() -> Self {
            Self {
                verdict: Verdict::Error,
                deciding_layer: 0,
                confidence: 0.0,
                reason: String::new(),
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  FFI-Safe Types (for JNI / C interop)
// ────────────────────────────────────────────────────────────
//
// These types are `#[repr(C)]` and contain only fixed-size arrays and
// primitive types. They are used at the FFI boundary and converted to
// from the safe types above.

/// FFI-safe version of [`Verdict`].
///
/// At the JNI boundary, the verdict is represented as `i32`.
pub type FfiVerdict = i32;

/// FFI-safe version of [`DnsResponse`].
///
/// All heap-allocated fields are replaced with fixed-size arrays.
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct FfiDnsResponse {
    pub ttl: u32,
    pub a_record_count: u8,
    pub cname_count: u8,
    pub cname_depth: u8,
    pub rcode: u8,
    pub ns_count: u8,
    pub fast_flux: u8, // 0 = false, 1 = true
    pub a_records: [[u8; 4]; MAX_A_RECORDS],
}

impl From<&DnsResponse> for FfiDnsResponse {
    fn from(d: &DnsResponse) -> Self {
        Self {
            ttl: d.ttl,
            a_record_count: d.a_record_count,
            cname_count: d.cname_count,
            cname_depth: d.cname_depth,
            rcode: d.rcode,
            ns_count: d.ns_count,
            fast_flux: d.fast_flux as u8,
            a_records: d.a_records,
        }
    }
}

/// FFI-safe version of [`PipelineResult`].
#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct FfiPipelineResult {
    pub verdict: FfiVerdict,
    pub deciding_layer: u8,
    pub confidence: f32,
}

impl From<&PipelineResult> for FfiPipelineResult {
    fn from(r: &PipelineResult) -> Self {
        Self {
            verdict: r.verdict as i32,
            deciding_layer: r.deciding_layer,
            confidence: r.confidence,
        }
    }
}
