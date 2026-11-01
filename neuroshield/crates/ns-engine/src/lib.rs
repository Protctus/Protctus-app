//! # NeuroShield — On-Device Phishing Detection Engine
//!
//! Privacy-first network metadata analysis for Android VpnService.
//! No TLS interception. No sensitive permissions. 95%+ local decisions.
//!
//! ## Architecture
//!
//! ```text
//!  C++ VPN ──SPSC──▶ SignalBundle ──▶ L0 ─▶ L1 ─▶ L2 ─▶ L3 ─▶ L4
//!                     (this crate)     │     │     │     │     │
//!                                    0.1ms 0.5ms 5ms  20ms  async
//! ```
//!
//! ## Crate layout
//!
//! - `types`  — FFI-safe structs: `SignalBundle`, `Verdict`, `DnsResponse`, etc.
//! - `signal` — `SignalBundleBuilder` for constructing bundles from parsed packets.
//! - `packet` — Zero-copy parsers: IP, DNS, TLS ClientHello, QUIC.
//! - `error`  — Unified `PhishingError` enum.

pub mod types;
pub mod signal;
pub mod packet;
pub mod error;

// ────────────────────────────────────────────────────────────
//  Re-exports: Safe types (for internal Rust use)
// ────────────────────────────────────────────────────────────

pub use types::{
    CertMetadata, DnsResponse, FfiDnsResponse, FfiPipelineResult, FfiVerdict,
    PipelineResult, SignalBundle, TlsClientHello, Verdict,
};
pub use error::PhishingError;
pub use signal::SignalBundleBuilder;

// ────────────────────────────────────────────────────────────
//  Re-exports: Packet parser types
// ────────────────────────────────────────────────────────────

pub use packet::ip::{IpHeader, Ipv4Header, Ipv6Header, TransportProtocol};
pub use packet::dns::{DnsAnswer, DnsPacket};
pub use packet::tls::{ClientHelloInfo, TlsExtension};
pub use packet::transport::{TransportHeader, TcpHeader, UdpHeader};
pub use packet::quic::QuicLongHeader;
