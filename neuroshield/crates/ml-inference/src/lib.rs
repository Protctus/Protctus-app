//! # ML Inference — ONNX Runtime Wrapper for LightGBM Model
//!
//! Loads a LightGBM model exported to ONNX format and provides thread-safe
//! inference. Model input is exactly 53 lexical features.
//!
//! If ONNX Runtime is not available in the build environment, a stub is
//! provided that returns 0.0 (fail-open: heuristics decide alone).

use std::sync::Arc;

use std::sync::Mutex;

/// Result of ML inference.
#[derive(Debug, Clone, Copy)]
pub struct MlInferenceResult {
    /// Phishing probability in [0.0, 1.0].
    pub phishing_probability: f32,
    /// Whether the model was available (true) or fallback (false).
    pub model_available: bool,
}

/// ONNX Runtime session wrapper for LightGBM model.
pub struct OnnxModel {
    session: Mutex<Option<onnxruntime::OrtSession>>,
    input_name: String,
    output_name: String,
    feature_count: usize,
}

impl OnnxModel {
    /// Load a LightGBM model from ONNX file path.
    pub fn load(model_path: &str) -> Result<Self, String> {
        let env = onnxruntime::OrtEnvironment::builder()
            .build()
            .map_err(|e| format!("Failed to create ONNX environment: {}", e))?;

        let session_builder = env.new_session_builder().map_err(|e| {
            format!("Failed to create ONNX session builder: {}", e)
        })?;

        let session = session_builder
            .with_model_from_file(model_path)
            .map_err(|e| format!("Failed to load ONNX model: {}", e))?;

        let input_name = "float_input".to_string();
        let output_name = "output_label".to_string();

        Ok(Self {
            session: Mutex::new(Some(session)),
            input_name,
            output_name,
            feature_count: 53,
        })
    }

    /// Run inference on a 53-feature vector.
    pub fn infer(&self, features: &[f32]) -> f32 {
        let session = self.session.lock().unwrap();
        let session = match session.as_ref() {
            Some(s) => s,
            None => return 0.0,
        };

        // Create input tensor
        let input_array = onnxruntime::ArrayD::<f32>::from_vec(
            vec![1, self.feature_count],
            features.to_vec(),
        )
        .map_err(|e| {
            eprintln!("Failed to create input array: {}", e);
            0.0f32
        })
        .unwrap_or(0.0f32);

        if input_array == 0.0f32 {
            return 0.0;
        }

        let input_tensor = onnxruntime::Value::from_array(session, input_array)
            .map_err(|e| {
                eprintln!("Failed to create input tensor: {}", e);
                0.0f32
            })
            .unwrap_or_else(|_| 0.0f32);

        if input_session.is_none() {
            return 0.0;
        }

        let outputs = session.run(vec![input_tensor]).ok();
        match outputs {
            Some(mut outputs) => {
                if let Some(output) = outputs.next() {
                    if let Some(val) = output.as_array() {
                        if let Some(val) = val.as_array_view_d::<f32>() {
                            return val.first().copied().unwrap_or(0.0);
                        }
                    }
                }
            }
            0.0
        }
    }
}

/// Fallback inference when ONNX Runtime is not available.
pub struct NoOpModel;

impl NoOpModel {
    pub fn new() -> Self {
        Self
    }

    pub fn infer(&self, _features: &[f32]) -> f32 {
        0.0
    }
}

/// Unified inference interface.
pub enum ModelEngine {
    Onnx(OnnxModel),
    NoOp(NoOpModel),
}

impl ModelEngine {
    pub fn load_from_path(model_path: &str) -> Result<Self, String> {
        match OnnxModel::load(model_path) {
            Ok(m) => Ok(ModelEngine::Onnx(m)),
            Err(e) => {
                eprintln!("Failed to load ONNX model: {}", e);
                Ok(ModelEngine::NoOp(NoOpModel::new()))
            }
        }
    }

    pub fn infer(&self, features: &[f32]) -> f32 {
        match self {
            ModelEngine::Onnx(m) => m.infer(features),
            ModelEngine::NoOp(m) => m.infer(features),
        }
    }
}

/// Convenience function: load model and infer.
pub fn infer_with_model(
    model_path: &str,
    features: &[f32],
) -> Result<f32, String> {
    let model = OnnxModel::load(model_path)?;
    Ok(model.infer(features))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_infer_zeros() {
        let model = OnnxModel::load("nonexistent.onnx");
        // Should fail gracefully, not panic
        assert!(model.is_err());
    }
}
