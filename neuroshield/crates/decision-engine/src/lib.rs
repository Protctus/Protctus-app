//! # Decision Engine — Multi-Layer Phishing Detection
//!
//! Decision pipeline:
//!   L0: Bloom filter (trusted bypass)
//!   L1: Heuristics (deterministic short-circuit)
//!   L2: LightGBM ONNX model (ML)
//!   L4: Cloud escalation (deferred domains only)
//!
//! Positive class = phishing.

use bloom_filter::BloomFilter;
use decision_policy::PolicyDecision;
use feature_extractor::extract_features;
use heuristics::analyze as heuristic_analyze;
use ml_inference::{infer as ml_infer, LightGBMModel};

mod decision_policy;
mod deferral;
mod verdict_cache;

pub use decision_policy::PolicyDecision;
pub use deferral::should_defer_to_cloud;
pub use verdict_cache::VerdictCache;

/// Complete analysis result from the detection engine.
#[derive(Debug, Clone)]
pub struct AnalysisResult {
    pub verdict: PolicyDecision,
    pub confidence: f32,
    pub lgb_score: f32,
    pub heuristic_score: f32,
    pub fused_score: f32,
    pub decision_layer: u8,
    pub triggered_indicators: Vec<String>,
}

/// Main detection engine.
pub struct DetectionEngine {
    bloom_filter: BloomFilter,
    lgb_model: Option<LightGBMModel>,
    verdict_cache: VerdictCache,
}

impl DetectionEngine {
    pub fn new(bloom_filter: BloomFilter, lgb_model: Option<LightGBMModel>) -> Self {
        DetectionEngine {
            bloom_filter,
            lgb_model,
            verdict_cache: VerdictCache::new(1000),
        }
    }

    /// Analyze a domain and produce a verdict.
    pub fn analyze(&mut self, domain: &[u8]) -> AnalysisResult {
        // Check cache first
        let domain_str = std::str::from_utf8(domain).unwrap_or("");
        if let Some(cached) = self.verdict_cache.get(domain_str) {
            return cached;
        }

        // Extract features once
        let features = extract_features(domain);

        // Layer 0: Bloom filter allowlist
        if self.bloom_filter.might_contain(domain) {
            let result = AnalysisResult {
                verdict: PolicyDecision::Allow,
                confidence: 1.0,
                lgb_score: 0.0,
                heuristic_score: 0.0,
                fused_score: 0.0,
                decision_layer: 0,
                triggered_indicators: vec!["VVIP allowlist hit".to_string()],
            };
            self.verdict_cache.put(domain.to_vec(), result.clone());
            return result;
        }

        // Layer 1: Heuristic analysis
        let heur_result = heuristic_analyze(domain);
        let heuristic_score = heur_result.score;
        let mut indicators: Vec<String> = heur_result.indicators;

        // Layer 2: LightGBM ONNX inference
        let lgb_score = if let Some(ref model) = self.lgb_model {
            ml_infer(model, &features)
        } else {
            0.0
        };

        // ── Score Fusion ──
        let (fused_score, evidence_present) = fuse_scores(lgb_score, heuristic_score);

        // ── Verdict Decision ──
        let (verdict, decision_layer) = decide(fused_score, evidence_present);

        // Add ML-specific indicators
        if lgb_score > 0.5 {
            indicators.push(format!("LightGBM ML score: {:.2}", lgb_score));
        }
        if heuristic_score > 0.5 {
            indicators.push(format!("Heuristic score: {:.2}", heuristic_score));
        }

        let result = AnalysisResult {
            verdict,
            confidence: fused_score,
            lgb_score,
            heuristic_score,
            fused_score,
            decision_layer,
            triggered_indicators: indicators,
        };

        self.verdict_cache.put(domain.to_vec(), result.clone());
        result
    }

    /// Get engine operational status.
    pub fn status(&self) -> EngineStatus {
        EngineStatus {
            bloom_filter_size: self.bloom_filter.serialized_size(),
            bloom_item_count: self.bloom_filter.len(),
            lgb_available: self.lgb_model.is_some(),
            cache_size: self.verdict_cache.len(),
        }
    }
}

/// Engine operational status.
#[derive(Debug, Clone)]
pub struct EngineStatus {
    pub bloom_filter_size: usize,
    pub bloom_item_count: u32,
    pub lgb_available: bool,
    pub cache_size: usize,
}

/// Fuse scores from ONNX model and heuristics using weighted average.
fn fuse_scores(lgb_score: f32, heuristic_score: f32) -> (f32, bool) {
    const WEIGHT_LGB: f32 = 0.70;
    const WEIGHT_HEURISTIC: f32 = 0.30;
    const SIGNAL_ABSENT: f32 = 0.01;

    let mut total_weight = 0.0f32;
    let mut weighted_sum = 0.0f32;

    if lgb_score > SIGNAL_ABSENT {
        weighted_sum += lgb_score * WEIGHT_LGB;
        total_weight += WEIGHT_LGB;
    }
    if heuristic_score > SIGNAL_ABSENT {
        weighted_sum += heuristic_score * WEIGHT_HEURISTIC;
        total_weight += WEIGHT_HEURISTIC;
    }

    if total_weight > 0.0 {
        (weighted_sum / total_weight, true)
    } else {
        (0.0, false)
    }
}

/// Classify fused score into a verdict.
fn decide(fused_score: f32, evidence_present: bool) -> (PolicyDecision, u8) {
    if !evidence_present {
        return (PolicyDecision::RequiresNetworkVerification, 0);
    }

    if fused_score >= 0.65 {
        (PolicyDecision::Phishing, 2)
    } else if fused_score >= 0.30 {
        (PolicyDecision::Suspicious, 2)
    } else {
        (PolicyDecision::Allow, 2)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fuse_scores_all_signals() {
        let (score, present) = fuse_scores(0.8, 0.4);
        assert!(present);
        assert!((score - 0.8 * 0.7 - 0.4 * 0.3).abs() < 1e-6);
    }

    #[test]
    fn test_fuse_scores_no_signals() {
        let (score, present) = fuse_scores(0.0, 0.0);
        assert!(!present);
        assert_eq!(score, 0.0);
    }

    #[test]
    fn test_decide_phishing() {
        let (verdict, layer) = decide(0.8, true);
        assert_eq!(verdict, PolicyDecision::Phishing);
        assert_eq!(layer, 2);
    }

    #[test]
    fn test_decide_suspicious() {
        let (verdict, layer) = decide(0.5, true);
        assert_eq!(verdict, PolicyDecision::Suspicious);
        assert_eq!(layer, 2);
    }

    #[test]
    fn test_decide_safe() {
        let (verdict, layer) = decide(0.1, true);
        assert_eq!(verdict, PolicyDecision::Allow);
        assert_eq!(layer, 2);
    }

    #[test]
    fn test_decide_no_evidence() {
        let (verdict, layer) = decide(0.0, false);
        assert_eq!(verdict, PolicyDecision::RequiresNetworkVerification);
        assert_eq!(layer, 0);
    }
}
