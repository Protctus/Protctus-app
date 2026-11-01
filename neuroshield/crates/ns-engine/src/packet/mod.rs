//! # NeuroShield Engine — Packet Parsing
//!
//! Zero-allocation parsers for the network layers visible inside the VPN
//! tunnel:
//!
//! - `ip`  — IPv4 and IPv6 header parsing
//! - `dns` — DNS query and response parsing
//! - `tls` — TLS ClientHello parsing (SNI, JA3, extensions)
//! - `quic`— QUIC Initial packet parsing (fallback for ECH)
//!
//! All parsers use `nom` combinator primitives for zero-copy matching and
//! return `Result<Output, PhishingError>` (not the raw `nom::IResult`).

pub mod ip;
pub mod dns;
pub mod tls;
pub mod quic;
pub mod transport;
