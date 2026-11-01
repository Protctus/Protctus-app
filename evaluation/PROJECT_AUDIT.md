# Protctus Project Audit

## Executive Summary

This audit covers the Protctus Android cybersecurity app, focused on the NeuroShield phishing detection pipeline and the Malware Engine. The environment has no Android SDK, so no APK build or device testing is possible; all verification is offline (Python ML evaluation + static code review).

## Key Findings

### NeuroShield ONNX Model (Production Path)
- Asset: `app/src/main/assets/neuroshield_model.onnx` = 1,068 bytes
- VERIFIED: It IS a valid ONNX model (loads via onnxruntime; input `float_input [None,53]`, outputs `output_label` + `output_probability`).
- PROBLEM: Weak/degenerate — phishing probability is near-constant across inputs.
  - Full held-out eval (dynamic features populated): acc=0.7855, rec=0.8068, fpr=0.2362
  - Capped sample (10k/class): acc=0.9313, rec=0.9338
- Below the 0.95 accuracy / 0.95 recall targets. NOT met, disclosed honestly.

### ML Model Comparison (sklearn, same 53 features, domain-grouped split)
On a 50k/class sample:
| Model | acc | rec | prec | f1 | fpr |
|-------|-----|-----|------|----|-----|
| HistGradientBoosting | 0.9927 | 0.9963 | 0.9876 | 0.9919 | 0.0101 |
| LogisticRegression | 0.9925 | 0.9956 | 0.9876 | 0.9916 | 0.0101 |
| RandomForest | 0.9916 | 0.9950 | 0.9864 | 0.9907 | 0.0111 |
| LightGBM | 0.9911 | 0.9963 | 0.9840 | 0.9901 | 0.0131 |
| NeuroShield ONNX (weak 1KB) | 0.9313 | 0.9338 | 0.9144 | 0.9240 | 0.0707 |

**Best**: HistGradientBoosting (highest F1/recall). **Fastest/smallest**: LogisticRegression (negligible battery).

### Preprocessing Consistency (FIXED)
- Production `LexicalFeatureExtractor` zeros dynamic features 46-52; `DynamicFeatureProvider` populates them from `neuroshield_dynamic_data.json`.
- Evaluation (`feature_extractor.py`) now populates dynamic features via `--dynamic-data`, matching production.

### Privacy (FIXED)
- `CloudThreatReporter` and `MalwareTelemetryReporter` previously uploaded hashed domains/packages to remote workers.dev endpoints by default, ignoring the `dataSharingEnabled=false` setting.
- FIXED: Both now gate uploads behind `isDataSharingEnabled()` (DataStore), default OFF. No data leaves device without explicit consent. UI toggle added in PreferencesScreen.

### Build/Environment Blockers
- No Android SDK → cannot build APK or run instrumented tests. BLOCKED.
- `gradle.properties` `org.gradle.java.home=D:/jdk-21.0.5` invalid; JDK 27 at `D:\jdk-27`. Fix needed to build.
- TFLite interpreter blocked by application-control policy → `phishing_model.tflite` unevaluated.

### Files Modified (this session)
- `ml/evaluation/compare_models.py` (new): 5-model comparison (ONNX, LR, RF, HGB, LGBM) + recall>=0.95 operating point.
- `ml/evaluation/evaluate_phishing.py`: Fixed recall>=0.95 threshold selection, latency ×1000 bug, domain-grouped split, `--dynamic-data`.
- `ml/evaluation/feature_extractor.py`: Populate dynamic features 46-52 (matches production).
- `app/src/main/java/com/protctus/app/data/local/SettingsManager.kt`: data-sharing consent key.
- `app/src/main/java/com/protctus/edge/phishing/ThreatReporter.kt`: suspendable report methods.
- `app/src/main/java/com/protctus/edge/phishing/CloudThreatReporter.kt`: consent-gated uploads.
- `app/src/main/java/com/protctus/app/utils/MalwareTelemetryReporter.kt`: consent-gated uploads.
- `app/src/main/java/com/protctus/app/viewmodel/SettingsViewModel.kt`: expose isDataSharingEnabled.
- `app/src/main/java/com/protctus/app/ui/screens/settings/PreferencesScreen.kt`: data-sharing toggle.
- `app/src/main/res/values/strings.xml`: data_sharing strings.
