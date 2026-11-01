//! # Decision Policy
//!
//! Defines verdict classification levels and fusion thresholds.
//! All thresholds are tunable via configuration.

/// Final threat classification levels.
///
/// Discriminant values are stable for FFI serialization:
/// - 0: Allow — no threat detected
/// - 1: Suspicious — some indicators, warn user
/// - 2: Phishing — high-confidence threat, block
/// - 3: RequiresNetworkVerification — inconclusive, defer to cloud
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PolicyDecision {
    Allow = 0,
    Suspicious = 1,
    Phishing = 2,
    RequiresNetworkVerification = 3,
}

/// Decision policy configuration.
#[derive(Debug, Clone)]
pub struct PolicyConfig {
    /// Fused score above this → PHISHING verdict (block).
    pub threshold_phishing: f32,
    /// Fused score above this → SUSPICIOUS verdict (warn).
    pub threshold_suspicious: f32,
    /// Below this, a signal is treated as absent / not meaningful.
    pub signal_absent: f32,
    /// Weight of LightGBM model in fused score.
    pub weight_lgb: f32,
    /// Weight of heuristic score in fused score.
    pub weight_heuristic: f32,
}

impl Default for PolicyConfig {
    fn default() -> Self {
        PolicyConfig {
            threshold_phishing: 0.65,
            threshold_suspicious: 0.30,
            signal_absent: 0.01,
            weight_lgb: 0.70,
            weight_heuristic: 0.30,
        }
    }
}

/// Fuse scores from ONNX model and heuristics using weighted average.
pub fn fuse_scores(
    lgb_score: f32,
    heuristic_score: f32,
    config: &PolicyConfig,
) -> (f32, bool) {
    let mut total_weight = 0.0f32;
    let mut weighted_sum = 0.0f32;

    if lgb_score > config.signal_absent {
        weighted_sum += lgb_score * config.weight_lgb;
        total_weight += config.weight_lgb;
    }
    if heuristic_score > config.signal_absent {
        weighted_sum += heuristic_score * config.weight_heuristic;
        total_weight += config.weight_heuristic;
    }

    if total_weight > 0.0 {
        (weighted_sum / total_weight, true)
    } else {
        (0.0, false)
    }
}

/// Classify a fused score into a verdict.
pub fn classify(
    fused_score: f32,
    evidence_present: bool,
    config: &PolicyConfig,
) -> PolicyDecision {
    if !evidence_present {
        return PolicyDecision::RequiresNetworkVerification;
    }

    if fused_score >= config.threshold_phishing {
        PolicyDecision::Phishing
    } else if fused_score >= config.threshold_suspicious {
        PolicyDecision::Suspicious
    } else {
        PolicyDecision::Allow
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_policy_config_default() {
        let config = PolicyConfig::default();
        assert_eq!(config.threshold_phishing, 0.65);
        assert_eq!(config.threshold_suspicious, 0.30);
        assert_eq!(config.signal_absent, 0.01);
        assert_eq!(config.weight_lgb, 0.70);
        assert_eq!(config.weight_heuristic, 0.30);
    }

    #[test]
    fn test_fuse_scores_all_signals() {
        let config = PolicyConfig::default();
        let (score, present) = fuse_scores(0.8, 0.4, &config);
        assert!(present);
        assert!((score - 0.8 * 0.7 - 0.4 * 0.3).abs() < 1e-6);
    }

    #[test]
    fn test_fuse_scores_no_signals() {
        let config = PolicyConfig::default();
        let (score, present) = fuse_scores(0.0, 0.0, &config);
        assert!(!present);
        assert_eq!(score, 0.0);
    }

    #[test]
    fn test_classify_phishing() {
        let config = PolicyConfig::default();
        assert_eq!(
            classify(0.8, true, &config),
            PolicyDecision::Phishing
        );
    }

    #[test]
    fn test_classify_suspicious() {
        let config = PolicyConfig::default();
        assert_eq!(
            classify(0.5, true, &config),
            PolicyDecision::Suspicious
        );
    }

    #[test]
    fn test_classify_safe() {
        let config = PolicyConfig::default();
        assert_eq!(classify(0.1, true, &config), PolicyDecision::Allow);
    }

    #[test]
    fn test_classify_no_evidence() {
        let config = PolicyConfig::default();
        assert_eq!(
            classify(0.0, false, &config),
            PolicyDecision::RequiresNetworkVerification
        );
    }
}
