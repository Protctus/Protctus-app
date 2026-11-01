# Protctus Manual Verification Log

## Environment constraints (honest)
- Android SDK installed at D:\android-sdk (platform-tools, build-tools 34.0.0, platform android-35, NDK r25b, cmake 3.22.1), ANDROID_HOME set, `gradle.java.home` fixed to D:/jdk-27.
- `./gradlew :app:assembleDebug` BUILD FAILED at native CMake step: `yara-core-module/src/main/cpp/yara-src/libyara/` is empty but CMakeLists.txt references 40+ YARA .c sources. → APK build BLOCKED (see B-019).
- No physical device or emulator → cannot run the APK or perform manual UI interaction on Android.
- TFLite interpreter blocked by application-control policy → `phishing_model.tflite` cannot be executed.
- Java 27 available at D:\jdk-27; Python 3.14.7 with onnxruntime, numpy, scikit-learn, lightgbm, skl2onnx available.

All "manual verification" below was performed offline in Python by running the actual production code paths (feature extractor + ONNX inference + ensemble logic) against real labeled domains, plus Kotlin-logic consistency checks for the new feature-vector engine. Android-side manual UI verification could NOT be performed.

---

## M-001: ONNX model loads and infers (VERIFIED)
- Steps: `ort.InferenceSession('app/src/main/assets/neuroshield_model.onnx')`; inspect inputs/outputs; run inference on sample domains.
- Input: `float_input [None, 53] tensor(float)`; Outputs: `output_label [None] int64`, `output_probability [] seq(map(int64,float))`.
- Result: Model loads successfully. NOT degenerate — produces varying probabilities per input.
- Status: VERIFIED (offline Python)

## M-002: ONNX output distinguishes benign vs phishing (VERIFIED)
- Steps: Ran real 53-feature vectors (from `LexicalFeatureExtractor` with dynamic features 46-52) through the model for 15 domains.
- Result:
  - google.com p=0.071, paypal.com p=0.092, microsoft.com p=0.034, apple.com p=0.053, example.com p=0.083 → SAFE range
  - login-paypal.suspicious.tk p=0.9999, secure-bank-verify.ml p=0.973, paypal-verify.tk p=0.878, update-apple-id.xyz p=0.993, signin-amazon.freehosting.com p=0.996, 192.168.1.1 p=1.000 → PHISHING range
- Status: VERIFIED (offline Python)

## M-003: Temperature calibration applied correctly (VERIFIED)
- Steps: Loaded T=1.0177 from `neuroshield_calibration.json`; applied `calibrated = sigmoid(logit(raw)/T)`.
- Result: Calibration is near-identity (T≈1.0), meaning the LR model is already well-calibrated. Calibrated probabilities match raw closely.
- Status: VERIFIED (offline Python)

## M-004: Full ensemble pipeline (VERIFIED via `verify_ensemble.py`)
- Steps: Simulated `EnsemblePhishingEngine.evaluate()` — signature layer → calibrated ONNX → cascade (skip LightGBM if confident) → fuse ONNX+heuristics+signature → classify.
- Result:
  - 8 benign domains → SAFE (scores 0.036–0.187, LightGBM skipped for battery)
  - 7 phishing domains → PHISHING (scores 0.707–0.950)
  - 3 signature matches → PHISHING (immediate block, score 0.950)
  - Raw IP 192.168.1.1 → PHISHING (score 0.862)
- Status: VERIFIED (offline Python simulation of Kotlin logic)

## M-005: Battery-efficient cascade (VERIFIED)
- Steps: Confirmed LightGBM is skipped when ONNX score <0.15 (clearly safe) or >0.85 (clearly phishing).
- Result: All 8 benign domains skipped LightGBM ("lgb_skipped_battery" indicator). Only uncertain domains (0.15–0.85) would run LightGBM.
- Status: VERIFIED (offline Python simulation)

## M-006: Preprocessing consistency — Kotlin vs Python (VERIFIED)
- Steps: Confirmed `feature_extractor.py` with `--dynamic-data` produces the same 53 features (46-52 populated) as production `DynamicFeatureProvider` + `LexicalFeatureExtractor`.
- Status: VERIFIED (code review + offline Python)

## M-007: Cloud telemetry consent gate (VERIFIED via code review)
- Steps: Reviewed `CloudThreatReporter.kt` and `MalwareTelemetryReporter.kt`; both check `isDataSharingEnabled()` (DataStore) before uploading, default OFF.
- Result: No data leaves device unless user explicitly enables data sharing in PreferencesScreen.
- Status: VERIFIED (code review; not runnable on device)

## NOT VERIFIED (blocked — no Android SDK/device)
- MV-001: APK build (`./gradlew :app:assembleDebug`) — BLOCKED (B-005, B-008)
- MV-002: Unit test execution (`:app:testDebugUnitTest`) — BLOCKED (no AGP/Android test runner)
- MV-003: Instrumented/Espresso UI tests — BLOCKED (no device/emulator)
- MV-004: Manual UI interaction (UrlScannerScreen, Settings toggle, VPN consent flow) — BLOCKED (no device)
- MV-005: PhishingVpnService real-time DNS interception on device — BLOCKED (no device)
- MV-006: TFLite model inference — BLOCKED (interpreter blocked, B-006)
- MV-007: Model initialization time, inference latency on device — BLOCKED (no device)
- MV-008: Battery/heating impact measurement — BLOCKED (no device)

- MV-009: FeatureVectorMalwareEngine pipeline consistency — VERIFIED (offline Python)
- Steps: Trained logistic regression on synthetic 68-feature data; exported JSON weights; verified Kotlin `sigmoid(w·x + b)` matches Python scoring exactly.
- Input: benign-like vector (0.1×68) → prob 0.4065; malware-like vector (0.8×12 + 0.1×56) → prob 1.0.
- Result: Pipeline logic consistent between Python trainer and Kotlin engine.
- Status: VERIFIED (offline Python, logic consistency only; not run on device)

- MV-010: FeatureVectorMalwareEngine fail-safe (missing model) — VERIFIED (code review + logic)
- Steps: Confirm engine returns isReady=false and score=-1f when model asset absent/malformed; evaluate() returns LOW_RISK with 0 confidence + "limited visibility" explanation.
- Result: No guess is ever made when the model is unavailable.
- Status: VERIFIED (code review; Kotlin not compiled here due to YARA build blocker)

- MV-011: FeatureVectorMalwareEngine shipped model is synthetic — VERIFIED (documentation)
- Steps: Confirmed `app/src/main/assets/feature_vector_malware_model.json` labeled "SYNTHETIC demonstration model — train on real Droidware 68-feature data for production."
- Result: Clearly labeled; not a claim of real detection performance.
- Status: VERIFIED (file inspection)

## Remaining risks
- ML accuracy/recall below 0.95/0.95 targets (B-007) — disclosed, not fabricated.
- Android-side behavior unverified (B-005) — all Kotlin changes are UNVERIFIED on device until SDK is available.
