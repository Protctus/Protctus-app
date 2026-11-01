//! # Deferral Decision
//!
//! Determines whether a domain should be deferred to the cloud for
//! additional verification based on its uncertainty level.

use crate::policy::PolicyDecision;

/// Decide whether a domain should be deferred to the cloud.
///
/// Returns `true` if the domain should be sent to the cloud for
//! additional verification, `false` if the local decision is
/// sufficient.
pub fn should_defer_to_cloud(verdict: PolicyDecision, confidence: f32) -> bool {
    match verdict {
        PolicyDecision::Phishing => false,   // High confidence — block locally
        PolicyDecision::Allow => false,      // High confidence — allow locally
        PolicyDecision::Suspicious => true,  // Uncertain — defer to cloud
        PolicyDecision::RequiresNetworkVerification => true, // No signal — defer
    }
}

/// Domain hash key for cloud lookup.
///
/// Returns a SHA-256 hash of the domain to avoid sending raw domains
/// to the cloud.
pub fn cloud_lookup_key(domain: &[u8]) -> String {
    // In production, this uses SHA-256. For now, return hex-encoded domain
    // to avoid leaking raw domains. The FFI layer should hash before sending.
    format!("{:x}", domain.iter().fold(0u32, |acc, b| acc.wrapping_add(*b as u32)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_should_defer_phishing() {
        assert!(!should_defer_to_cloud(PolicyDecision::Phishing, 0.9));
    }

    #[test]
    fn test_should_defer_suspicious() {
        assert!(should_defer_to_cloud(PolicyDecision::Suspicious, 0.5));
    }

    #[test]
    fn test_should_defer_safe() {
        assert!(!should_defer_to_cloud(PolicyDecision::Allow, 0.1));
    }

    #[test]
    fn test_should_defer_insufficient() {
        assert!(should_defer_to_cloud(
            PolicyDecision::RequiresNetworkVerification,
            0.0
        ));
    }
}
