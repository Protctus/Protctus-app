//! Tree model representation for LightGBM inference.
//!
//! Each tree is a binary decision tree where:
//! - Internal nodes split on a feature threshold
//! - Leaf nodes hold a value that contributes to the final score

/// A single decision tree from a LightGBM model.
#[derive(Debug, Clone)]
pub struct TreeModel {
    pub num_leaves: u32,
    pub split_features: Vec<i32>,
    pub thresholds: Vec<f32>,
    pub decision_types: Vec<i32>,
    pub left_children: Vec<i32>,
    pub right_children: Vec<i32>,
    pub leaf_values: Vec<f32>,
    pub leaf_weights: Vec<f32>,
    pub leaf_counts: Vec<i32>,
}

impl TreeModel {
    /// Run inference on a feature vector.
    ///
    * Traverses from root (node 0) to a leaf, then returns the leaf value.
    pub fn infer(&self, features: &[f32]) -> f32 {
        if self.split_features.is_empty() {
            return 0.0;
        }

        let mut node_id = 0usize;
        loop {
            let decision_type = self.decision_types[node_id];

            if decision_type == 2 {
                // Numerical split: feature <= threshold → go left
                let feature_idx = self.split_features[node_id] as usize;
                let threshold = self.thresholds[node_id];

                if feature_idx >= features.len() {
                    // Feature out of bounds - go left (conservative)
                    let left = self.left_children[node_id];
                    if left < 0 {
                        // Leaf node
                        return self.leaf_values[(-left - 1) as usize];
                    }
                    node_id = left as usize;
                    continue;
                }

                let feature_value = features[feature_idx];
                if feature_value <= threshold {
                    let left = self.left_children[node_id];
                    if left < 0 {
                        return self.leaf_values[(-left - 1) as usize];
                    }
                    node_id = left as usize;
                } else {
                    let right = self.right_children[node_id];
                    if right < 0 {
                        return self.leaf_values[(-right - 1) as usize];
                    }
                    node_id = right as usize;
                }
            } else {
                // Unknown decision type - treat as leaf
                return 0.0;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simple_tree() {
        // Simple tree:
        //   Node 0: feature 0 <= 0.5 → left (node 1), right (node 2)
        //   Node 1: leaf, value = 1.0
        //   Node 2: leaf, value = -1.0
        let tree = TreeModel {
            num_leaves: 2,
            split_features: vec![0],
            thresholds: vec![0.5],
            decision_types: vec![2],
            left_children: vec![-1, 0, 0],
            right_children: vec![-2, 0, 0],
            leaf_values: vec![1.0, -1.0],
            leaf_weights: vec![1.0, -1.0],
            leaf_counts: vec![1, 1],
        };

        // feature[0] = 0.3 <= 0.5 → left → leaf 0 → value 1.0
        assert!((tree.infer(&[0.3]) - 1.0).abs() < 1e-6);

        // feature[0] = 0.7 > 0.5 → right → leaf 1 → value -1.0
        assert!((tree.infer(&[0.7]) - (-1.0)).abs() < 1e-6);
    }

    #[test]
    fn test_out_of_bounds_feature() {
        let tree = TreeModel {
            num_leaves: 1,
            split_features: vec![0],
            thresholds: vec![0.5],
            decision_types: vec![2],
            left_children: vec![-1],
            right_children: vec![-2],
            leaf_values: vec![1.0, -1.0],
            leaf_weights: vec![1.0, -1.0],
            leaf_counts: vec![1, 1],
        };

        // Feature index 5 out of bounds → go left → leaf 0
        assert!((tree.infer(&[0.3]) - 1.0).abs() < 1e-6);
    }
}
