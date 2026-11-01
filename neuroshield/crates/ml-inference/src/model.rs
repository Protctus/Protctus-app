//! LightGBM model loader.
//!
//! Parses the text-format LightGBM model dump into an in-memory
//! representation suitable for fast inference.

use crate::tree::TreeModel;
use std::fs;
use std::str::FromStr;

/// Loaded LightGBM model with all trees and metadata.
#[derive(Debug, Clone)]
pub struct LightGBMModel {
    pub trees: Vec<TreeModel>,
    pub num_trees: u32,
    pub num_features: u32,
    pub shrinkage: f32,
    pub baseline: f32,
}

impl LightGBMModel {
    /// Load a LightGBM model from a text file path.
    pub fn load(path: &str) -> Result<Self, String> {
        let content = fs::read_to_string(path)
            .map_err(|e| format!("Failed to read model file: {}", e))?;
        Self::parse(&content)
    }

    /// Load a LightGBM model from a string buffer.
    pub fn parse(content: &str) -> Result<Self, Self> {
        // Use a custom error type
        let result = Self::parse_inner(content);
        match result {
            Ok(m) => m,
            Err(e) => Err(Self {
                trees: Vec::new(),
                num_trees: 0,
                num_features: 0,
                shrinkage: 0.0,
                baseline: 0.0,
            }),
        }
    }

    fn parse_inner(content: &str) -> Result<Self, String> {
        let lines: Vec<&str> = content.lines().collect();
        let mut num_trees = 0u32;
        let mut num_features = 53u32; // default
        let mut shrinkage = 0.05f32;
        let mut baseline = 0.0f32;
        let mut trees = Vec::new();

        let mut i = 0;
        while i < lines.len() {
            let line = lines[i].trim();

            if line.starts_with("num_tree_per_iteration=") {
                num_trees = line.split('=').nth(1)
                    .unwrap_or("0")
                    .parse::<u32>()
                    .unwrap_or(0);
            } else if line.starts_with("max_feature_idx=") {
                num_features = line.split('=').nth(1)
                    .unwrap_or("0")
                    .parse::<u32>()
                    .unwrap_or(0) + 1;
            } else if line.starts_with("shrinkage=") {
                shrinkage = line.split('=').nth(1)
                    .unwrap_or("0.05")
                    .parse::<f32>()
                    .unwrap_or(0.05);
            } else if line.starts_with("Tree=") {
                // Parse a tree
                let tree_start = i;
                let mut tree_lines = Vec::new();
                tree_lines.push(line);
                i += 1;
                while i < lines.len() && !lines[i].trim().starts_with("Tree=") {
                    tree_lines.push(lines[i].trim());
                    i += 1;
                }
                // Don't consume the next "Tree=" line
                if i < lines.len() && lines[i].trim().starts_with("Tree=") {
                    i -= 1; // will be incremented at loop end
                }

                let tree = parse_tree(&tree_lines)?;
                trees.push(tree);
            }
            i += 1;
        }

        Ok(LightGBMModel {
            trees,
            num_trees: trees.len() as u32,
            num_features,
            shrinkage,
            baseline,
        })
    }

    /// Run inference on a feature vector.
    ///
    * Sum leaf values from all trees, apply shrinkage, then sigmoid.
    pub fn infer(&self, features: &[f32]) -> f32 {
        if self.trees.is_empty() {
            return 0.0;
        }

        let mut raw_score = 0.0f32;
        for tree in &self.trees {
            raw_score += tree.infer(features) * self.shrinkage;
        }

        // Apply sigmoid
        1.0 / (1.0 + (-raw_score).exp())
    }
}

/// Parse a single tree from its text representation.
fn parse_tree(lines: &[&str]) -> Result<TreeModel, String> {
    if lines.is_empty() || !lines[0].starts_with("Tree=") {
        return Err("Expected Tree= header".to_string());
    }

    let mut num_leaves = 0u32;
    let mut split_features = Vec::new();
    let mut thresholds = Vec::new();
    let mut decision_types = Vec::new();
    let mut left_children = Vec::new();
    let mut right_children = Vec::new();
    let mut leaf_values = Vec::new();
    let mut leaf_weights = Vec::new();
    let mut leaf_counts = Vec::new();

    for line in lines {
        let line = line.trim();
        if line.starts_with("num_leaves=") {
            num_leaves = line.split('=').nth(1)
                .unwrap_or("0")
                .parse::<u32>()
                .map_err(|e| format!("Invalid num_leaves: {}", e))?;
        } else if line.starts_with("split_feature=") {
            split_features = parse_int_list(line.split('=').nth(1).unwrap_or(""))?;
        } else if line.starts_with("threshold=") {
            thresholds = parse_float_list(line.split('=').nth(1).unwrap_or(""))?;
        } else if line.starts_with("decision_type=") {
            decision_types = parse_int_list(line.split('=').nth(1).unwrap_or(""))?;
        } else if line.starts_with("left_child=") {
            left_children = parse_int_list(line.split('=').nth(1).unwrap_or(""))?;
        } else if line.starts_with("right_child=") {
            right_children = parse_int_list(line.split('=').nth(1).unwrap_or(""))?;
        } else if line.starts_with("leaf_value=") {
            leaf_values = parse_float_list(line.split('=').nth(1).unwrap_or(""))?;
        } else if line.starts_with("leaf_weight=") {
            leaf_weights = parse_float_list(line.split('=').nth(1).unwrap_or(""))?;
        } else if line.starts_with("leaf_count=") {
            leaf_counts = parse_int_list(line.split('=').nth(1).unwrap_or(""))?;
        }
    }

    Ok(TreeModel {
        num_leaves,
        split_features,
        thresholds,
        decision_types,
        left_children,
        right_children,
        leaf_values,
        leaf_weights,
        leaf_counts,
    })
}

/// Parse a comma/space-separated list of integers.
fn parse_int_list(s: &str) -> Result<Vec<i32>, String> {
    s.split_whitespace()
        .map(|tok| tok.parse::<i32>().map_err(|e| format!("Invalid int: {} ({})", tok, e)))
        .collect()
}

/// Parse a comma/space-separated list of floats.
fn parse_float_list(s: &str) -> Result<Vec<f32>, String> {
    s.split_whitespace()
        .map(|tok| tok.parse::<f32>().map_err(|e| format!("Invalid float: {} ({})", tok, e)))
        .collect()
}
