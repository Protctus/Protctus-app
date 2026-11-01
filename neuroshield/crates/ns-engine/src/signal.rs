//! # NeuroShield Engine — Signal Bundle Construction
//!
//! Builder-pattern API for constructing a [`SignalBundle`] from parsed
//! packet data. Isolates the "assemble the bundle" step from the
//! "parse the raw bytes" step.

use crate::types::{DnsResponse, SignalBundle, TlsClientHello};

/// Incremental builder for [`SignalBundle`].
///
/// Usage:
/// ```
/// let bundle = SignalBundleBuilder::new()
///     .domain("evil-paytm-login.com")
///     .ip("142.250.190.46")
///     .port(443)
///     .tcp()
///     .tls(&hello)
///     .dns(&dns)
///     .timestamp(1_234_567)
///     .build();
/// ```
#[derive(Debug)]
pub struct SignalBundleBuilder {
    domain: String,
    ip: String,
    port: u16,
    protocol: u8,
    sni: Option<String>,
    tls_version: Option<u16>,
    ja3_fingerprint: Option<String>,
    cert_chain: Option<Vec<crate::types::CertMetadata>>,
    dns_response: Option<DnsResponse>,
    timestamp: u64,
    packet_sizes: Vec<usize>,
}

impl SignalBundleBuilder {
    pub fn new() -> Self {
        Self {
            domain: String::new(),
            ip: String::new(),
            port: 0,
            protocol: 0,
            sni: None,
            tls_version: None,
            ja3_fingerprint: None,
            cert_chain: None,
            dns_response: None,
            timestamp: 0,
            packet_sizes: Vec::new(),
        }
    }

    pub fn domain(mut self, domain: impl Into<String>) -> Self {
        self.domain = domain.into();
        self
    }

    pub fn ip(mut self, ip: impl Into<String>) -> Self {
        self.ip = ip.into();
        self
    }

    pub fn port(mut self, port: u16) -> Self {
        self.port = port;
        self
    }

    pub fn tcp(mut self) -> Self {
        self.protocol = crate::types::protocol::IPPROTO_TCP;
        self
    }

    pub fn udp(mut self) -> Self {
        self.protocol = crate::types::protocol::IPPROTO_UDP;
        self
    }

    /// Populate TLS-derived fields from a parsed ClientHello.
    pub fn tls(mut self, hello: &TlsClientHello) -> Self {
        self.sni = hello.sni.clone();
        self.tls_version = Some(hello.record_version);
        self.ja3_fingerprint = hello.ja3_fingerprint.clone();

        if !self.domain.is_empty() {
            // Domain was already set (e.g. from DNS). Prefer SNI
            // when available as it's the *actual* destination.
            if let Some(ref sni) = self.sni {
                self.domain = sni.clone();
            }
        } else if let Some(ref sni) = self.sni {
            self.domain = sni.clone();
        }

        self
    }

    pub fn dns(mut self, dns: &DnsResponse) -> Self {
        self.dns_response = Some(dns.clone());
        self
    }

    pub fn timestamp(mut self, ts: u64) -> Self {
        self.timestamp = ts;
        self
    }

    pub fn packet_sizes(mut self, sizes: Vec<usize>) -> Self {
        self.packet_sizes = sizes;
        self
    }

    pub fn build(self) -> SignalBundle {
        SignalBundle {
            domain: self.domain,
            ip: self.ip,
            port: self.port,
            protocol: self.protocol,
            sni: self.sni,
            dns_response: self.dns_response,
            tls_version: self.tls_version,
            ja3_fingerprint: self.ja3_fingerprint,
            cert_chain: self.cert_chain,
            timestamp: self.timestamp,
            packet_sizes: self.packet_sizes,
            native_packet_tag: std::ptr::null_mut(),
        }
    }
}

impl Default for SignalBundleBuilder {
    fn default() -> Self {
        Self::new()
    }
}
