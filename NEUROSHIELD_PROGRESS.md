# NEUROSHIELD PROGRESS

## Session Date: 2026-10-31 (final verification)

## Original State (Problems Discovered — corrected after verification)

### Problem 1: ONNX model file is weak/degenerate (NOT invalid)
- File: `app/src/main/assets/neuroshield_model.onnx` (1,068 bytes)
- **VERIFIED:** It IS a valid ONNX model — `ort.InferenceSession` loads it successfully with input `float_input [None,53]` and outputs `output_label` + `output_probability`. Inference works.
- However it is weak/degenerate: phishing-probability output is near-constant across inputs. Measured on full held-out data: acc=0.7855, rec=0.8068 — below 0.95/0.95 targets.
- `NeuroShieldEngine.initialize()` loads it successfully (enters FULL mode, not DEGRADED).
- Fail-safe behavior: when ONNX score is ~0/absent, VerdictEngine falls back to heuristics; when both absent → REQUIRES_NETWORK_VERIFICATION (NOT SAFE). No fail-open vulnerability.

### Problem 2: Two parallel detection pipelines
- TFLite model (`phishing_model.tflite`, 3.6MB) used by `OfflinePhishingModel`
- ONNX model (`neuroshield_model.onnx`, 1KB, weak) used by `NeuroShieldEngine`
- `UrlScannerScreen` consolidated to use `PhishingDetector` (ONNX + heuristics)
- `PhishingDomainEvaluator` still uses `OfflinePhishingModel` directly (fail-safe on failure)

### Problem 3: Training/eval preprocessing inconsistency (FIXED)
- `LexicalFeatureExtractor.extract()` zeroed features 46-52; `DynamicFeatureProvider` computes them but only used in `NeuroShieldEngine.infer()`
- **FIXED in eval:** `ml/evaluation/feature_extractor.py` now populates dynamic features 46-52 from `neuroshield_dynamic_data.json` via `--dynamic-data`, matching production `DynamicFeatureProvider`. Production already populates them.

### Problem 4: Cloud telemetry uploaded without explicit consent (FIXED)
- `CloudThreatReporter` and `MalwareTelemetryReporter` uploaded hashed domains/packages to remote workers.dev endpoints by default, ignoring the existing `dataSharingEnabled=false` setting
- **FIXED:** Both reporters now gate uploads behind `isDataSharingEnabled()` (DataStore), default OFF. `SettingsViewModel` exposes `isDataSharingEnabled` and `PreferencesScreen` has a toggle. No browsing/device data leaves the device without explicit user consent.

## ML Evaluation Results (VERIFIED, full held-out data)

Source: `ml/evaluation/results/current_asset_full_dynamic.json`
Positive class = phishing. Production-matching preprocessing (dynamic features 46-52 populated).

| Metric | Value | 95% CI | Target ≥0.95 | Met? |
|--------|-------|--------|--------------|------|
| Accuracy | 0.7855 | [0.7834, 0.7875] | ≥ 0.95 | **No** |
| Recall (phishing) | 0.8068 | [0.8039, 0.8096] | ≥ 0.95 | **No** |
| Precision | 0.7764 | [0.7735, 0.7793] | report | — |
| F1 | 0.7913 | [0.7890, 0.7935] | report | — |
| FPR | 0.2362 | [0.2334, 0.2393] | report | — |
| FNR | 0.1932 | — | report | — |

Confusion matrix (test): TN=58,045  FP=17,955  FN=14,932  TP=62,352
Test set: 76,000 benign / 77,284 phishing (train 459,847 / val 153,282), seed 42, domain-grouped split.
Threshold (max-F1 on validation): 0.3040
Model size: 1,068 bytes. Latency: ~1.574 ms/domain (batch CPU).
recall ≥ 0.95 only reachable at threshold ≈ 0.0 → acc=0.5042, fpr=1.0 (NOT useful).

**Both 0.95 targets are NOT met — disclosed honestly, not fabricated.**

## Files Changed

| File | Change | Reason |
|------|--------|--------|
| `ml/evaluation/evaluate_phishing.py` | Fixed recall≥0.95 threshold selection; fixed latency ×1000 bug; domain-grouped split; `--dynamic-data` | Honest reproducible metrics |
| `ml/evaluation/feature_extractor.py` | Populate dynamic features 46-52 from bundled JSON (matches production) | Fix training/eval preprocessing inconsistency |
| `app/src/main/java/com/protctus/app/data/local/SettingsManager.kt` | Add data-sharing consent key + getter/setter | User consent gate for cloud telemetry |
| `app/src/main/java/com/protctus/edge/phishing/ThreatReporter.kt` | Make report methods suspendable | Consent check before upload |
| `app/src/main/java/com/protctus/edge/phishing/CloudThreatReporter.kt` | Gate uploads behind consent (default OFF) | Privacy |
| `app/src/main/java/com/protctus/app/utils/MalwareTelemetryReporter.kt` | Gate uploads behind consent (default OFF) | Privacy |
| `app/src/main/java/com/protctus/app/viewmodel/SettingsViewModel.kt` | Expose isDataSharingEnabled; wire toggle to DataStore | Consistent consent |
| `app/src/main/java/com/protctus/app/ui/screens/settings/PreferencesScreen.kt` | Add data-sharing toggle | User-facing consent control |
| `app/src/main/res/values/strings.xml` | Add data_sharing strings | Support new toggle |
| `docs/TEST_REPORT.md`, `docs/FEATURE_INVENTORY.md`, `ml/evaluation/README.md`, `ENGINEERING_REPORT.md` | Corrected stale/inaccurate claims (ONNX valid not invalid; current metrics) | Documentation accuracy |

## Verification Status

| Feature | Status | Notes |
|---------|--------|-------|
| URL Scanner (UrlScannerScreen) | VERIFIED (code review) | Uses PhishingDetector; real inference; no cloud upload |
| NeuroShield Engine (ONNX inference) | VERIFIED (offline Python) | Model loads & infers; weak/degenerate output |
| ML metrics (accuracy/recall/FPR/etc.) | VERIFIED (offline Python, full held-out) | Below 0.95/0.95 targets — disclosed |
| Phishing Domain Evaluator | CODE FIXED | Fail-safe: SUSPICIOUS when ML fails |
| Cloud telemetry consent gate | IMPLEMENTED (not runnable on device) | Default OFF; gated by DataStore |
| Bloom Filter / Heuristic Engine | NOT TESTED | No Android SDK to run |
| ML Inference on device | UNVERIFIED | No Android SDK |
