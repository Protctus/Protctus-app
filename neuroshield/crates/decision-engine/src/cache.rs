//! # Verdict Cache
//!
//! LRU cache for recent domain verdicts to avoid recomputation.

use std::collections::HashMap;

/// LRU cache for domain verdicts.
#[derive(Debug, Clone)]
pub struct VerdictCache {
    entries: std::collections::HashMap<String, super::AnalysisResult>,
    order: std::collections::VecDeque<String>,
    max_size: usize,
}

impl VerdictCache {
    /// Create a new cache with the given maximum size.
    pub fn new(max_size: usize) -> Self {
        VerdictCache {
            entries: std::collections::HashMap::with_capacity(max_size),
            order: std::collections::VecDeque::with_capacity(max_size),
            max_size,
        }
    }

    /// Get a cached verdict by domain.
    pub fn get(&self, domain: &str) -> Option<super::AnalysisResult> {
        self.entries.get(domain).cloned()
    }

    /// Store a verdict in the cache.
    pub fn put(&mut self, domain: String, result: super::AnalysisResult) {
        if self.entries.contains_key(&domain) {
            // Update existing entry
            self.entries.insert(domain, result);
            return;
        }

        if self.entries.len() >= self.max_size {
            // Evict oldest entry
            if let Some(oldest) = self.order.pop_front() {
                self.entries.remove(&oldest);
            }
        }

        self.entries.insert(domain.clone(), result);
        self.order.push_back(domain);
    }

    /// Get the current number of cached entries.
    pub fn len(&self) -> usize {
        self.entries.len()
    }

    /// Check if the cache is empty.
    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    /// Clear all cached entries.
    pub fn clear(&mut self) {
        self.entries.clear();
        self.order.clear();
    }
}

impl Default for VerdictCache {
    fn default() -> Self {
        Self::new(1000)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::policy::PolicyDecision;

    #[test]
    fn test_cache_put_get() {
        let mut cache = VerdictCache::new(3);
        let result = super::AnalysisResult {
            verdict: PolicyDecision::Allow,
            confidence: 0.0,
            lgb_score: 0.0,
            onnx_score: 0.0,
            heuristic_score: 0.0,
            fused_score: 0.0,
            decision_layer: 0,
            triggered_indicators: vec![],
        };

        cache.put("example.com".to_string(), result.clone());
        assert_eq!(cache.get("example.com"), Some(result));
        assert_eq!(cache.len(), 1);
    }

    #[test]
    fn test_cache_eviction() {
        let mut cache = VerdictCache::new(2);
        let result = super::AnalysisResult {
            verdict: PolicyDecision::Allow,
            confidence: 0.0,
            lgb_score: 0.0,
            onnx_score: 0.0,
            heuristic_score: 0.0,
            fused_score: 0.0,
            decision_layer: 0,
            triggered_indicators: vec![],
        };

        cache.put("a.com".to_string(), result.clone());
        cache.put("b.com".to_string(), result.clone());
        cache.put("c.com".to_string(), result.clone());

        // "a.com" should have been evicted
        assert!(cache.get("a.com").is_none());
        assert!(cache.get("b.com").is_some());
        assert!(cache.get("c.com").is_some());
        assert_eq!(cache.len(), 2);
    }
}
