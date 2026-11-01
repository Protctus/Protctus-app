# Protctus Implementation Changelog

## 2026-10-31 — Ensemble + Calibration + Trained Model (this session)

### ML model (production asset replaced)
- Replaced weak/degenerate 1,068-byte `app/src/main/assets/neuroshield_model.onnx` with a trained **LogisticRegression** model exported to ONNX via `skl2onnx` (1,068 bytes, input `float_input [None,53]`).
- Trained on 50k-sample held-out split (domain-grouped, seed 42): acc=0.9274, rec=0.8935, prec=0.9517, f1=0.9217, fpr=0.0415 (max-F1 val threshold 0.5730).
- Added temperature calibration (T=1.0177) fitted on validation data; stored in `app/src/main/assets/neuroshield_calibration.json`.
- `neuroshield_model_metrics.json` saved alongside asset with full metrics.

### NeuroShieldEngine.kt
- Added `temperature` field + `loadCalibration()` (reads `neuroshield_calibration.json` from assets).
- Added `calibrate(rawProb, temperature)` — temperature scaling: `sigmoid(logit(raw)/T)`.
- Added `inferCalibrated(domain)` — returns temperature-scaled probability.
- `infer(domain)` now returns **calibrated** probability (drives decisions).
- Added `inferRaw(domain)` — returns raw uncalibrated ONNX output (for indicator generation).
- `initialize()` loads calibration after model load; logs T value.

### EnsemblePhishingEngine.kt (rewritten)
- Added **signature layer**: bundled set of high-confidence phishing domains → immediate PHISHING block (score 0.95).
- Added **battery-efficient cascade**: LightGBM only runs when calibrated ONNX score is in uncertainty band (0.15–0.85); skipped when ONNX is confident (saves CPU/heat).
- Uses **calibrated ONNX** (LogisticRegression) as primary ML signal.
- Fuses ONNX + LightGBM + heuristics + signature with re-normalized weights over active signals only.
- All inference on-demand (per URL/DNS query), never continuous.

### Evaluation scripts
- `evaluate_phishing.py`: Fixed recall>=0.95 threshold selection (min-FPR subject to recall>=0.95 on val, not first-from-zero); fixed latency ×1000 bug; added domain-grouped split (prevents related-domain leakage); added `--dynamic-data`.
- `feature_extractor.py`: Now populates dynamic features 46-52 from `neuroshield_dynamic_data.json` (matches production `DynamicFeatureProvider`).
- `compare_models.py` (new): Compares ONNX, LogisticRegression, RandomForest, HistGradientBoosting, LightGBM on same held-out set; reports recall>=0.95 operating point honestly.
- `train_export_lr.py` (new): Trains LogisticRegression, exports ONNX, fits temperature calibration, saves metrics.
- `verify_ensemble.py` (new): End-to-end simulation of Kotlin ensemble pipeline.

### Privacy (telemetry consent gate)
- `SettingsManager.kt`: Added `DATA_SHARING_ENABLED_KEY`, `isDataSharingEnabled`, `setDataSharingEnabled`.
- `ThreatReporter.kt`: Report methods made suspendable.
- `CloudThreatReporter.kt`: Uploads gated behind `isDataSharingEnabled()` (default OFF).
- `MalwareTelemetryReporter.kt`: Uploads gated behind consent; made suspendable.
- `SettingsViewModel.kt`: Exposes `isDataSharingEnabled` StateFlow; `toggleDataSharing()` writes to DataStore.
- `PreferencesScreen.kt`: Added data-sharing toggle row + `CloudUpload` icon.
- `strings.xml`: Added `data_sharing` and `data_sharing_summary` strings.

### Documentation corrected
- `TEST_REPORT.md`, `FEATURE_INVENTORY.md`, `ml/evaluation/README.md`, `ENGINEERING_REPORT.md`, `NEUROSHIELD_PROGRESS.md`: Corrected stale claims (ONNX is valid not invalid; replaced stale 223,781-byte/0.8120 metrics with current verified values).

### Files created
- `ml/evaluation/compare_models.py`
- `ml/evaluation/train_export_lr.py`
- `ml/evaluation/verify_ensemble.py`
- `app/src/main/assets/neuroshield_calibration.json`
- `evaluation/PROJECT_AUDIT.md`
- `evaluation/FEATURE_MATRIX.md`
- `evaluation/BUG_REGISTER.md`
- `evaluation/MANUAL_VERIFICATION_LOG.md`
- `evaluation/IMPLEMENTATION_CHANGELOG.md` (this file)

### NOT done (blocked)
- APK build (no Android SDK; invalid gradle.java.home).
- Unit/instrumented test execution.
- Device-side manual UI verification.
- TFLite evaluation.
- Battery/heating measurement on device.
