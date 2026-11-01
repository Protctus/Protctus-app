//! # Android JNI Bridge
//!
//! JNI bindings between the NeuroShield Rust engine and the Android app.
//! Exposed functions are called from `com.protctus.app.neuroshield.NeuroShieldEngine`.

use bloom_filter::BloomFilter;
use decision_engine::{DetectionEngine, EngineStatus};
use feature_extractor::extract_features;
use jni::JNIEnv;
use jni::objects::{JClass, JString, JValue};
use jni::sys::{jboolean, jbyteArray, jfloat, jint};

/// Global engine instance (initialized once at app startup).
static mut ENGINE: Option<DetectionEngine> = None;

/// Initialize the NeuroShield engine with a Bloom filter and optional ML model.
#[no_mangle]
pub extern "C" fn Java_com_protctus_app_neuroshield_NeuroShieldEngine_nativeInit(
    mut env: JNIEnv,
    _class: JClass,
    bloom_data: jbyteArray,
    bloom_item_count: jint,
    model_path: JString,
) -> jboolean {
    let bloom_bytes: Vec<u8> = env
        .get_byte_array(bloom_data)
        .unwrap_or_default()
        .to_vec();

    let bloom_filter = BloomFilter::from_bytes(
        bloom_bytes.try_into().unwrap_or([0u8; 524288]),
        bloom_item_count as u32,
    );

    let model_path_str: String = env.get_string(model_path).unwrap_or_default().into();

    let lgb_model = ml_inference::LightGBMModel::load(&model_path_str).ok();

    let engine = DetectionEngine::new(bloom_filter, lgb_model);

    unsafe {
        ENGINE = Some(engine);
    }

    JNI_TRUE as jboolean
}

/// Analyze a domain and return a verdict as jint (0=Allow, 1=Suspicious, 2=Phishing, 3=RequiresNetworkVerification).
#[no_mangle]
pub extern "C" fn Java_com_protctus_app_neuroshield_NeuroShieldEngine_nativeAnalyze(
    mut env: JNIEnv,
    _class: JClass,
    domain: JString,
) -> jint {
    let domain_str: String = env.get_string(domain).unwrap_or_default();
    let domain_bytes = domain_str.as_bytes();

    let engine = unsafe { ENGINE.as_mut() };
    let engine = match engine {
        Some(e) => e,
        None => return jint::from(decision_engine::PolicyDecision::RequiresNetworkVerification as i32),
    };

    let result = engine.analyze(domain_bytes);
    result.verdict as jint
}

/// Get engine status information as JSON.
#[no_mangle]
pub extern "C" fn Java_com_protctus_app_neuroshield_NeuroShieldEngine_nativeGetStatus(
    mut env: JNIEnv,
    _class: JClass,
) -> jobject {
    let engine = unsafe { ENGINE.as_ref() };
    let status = match engine {
        Some(e) => e.status(),
        None => {
            return env
                .new_string("{}".to_string())
                .unwrap()
                .into_inner();
        }
    };

    let status_json = format!(
        r#"{{
            "bloom_filter_size": {},
            "bloom_item_count": {},
            "lgb_available": {},
            "cache_size": {}
        }}"#,
        status.bloom_filter_size,
        status.bloom_item_count,
        status.lgb_available,
        status.cache_size
    );

    env.new_string(status_json)
        .unwrap()
        .into_inner()
}

/// Release the engine instance.
#[no_mangle]
pub extern "C" fn Java_com_protctus_app_neuroshield_NeuroShieldEngine_nativeRelease(
    mut _env: JNIEnv,
    _class: JClass,
) {
    unsafe {
        ENGINE = None;
    }
}
