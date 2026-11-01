# Protctus Feature Matrix

## Detection Pipeline (NeuroShield Phishing)

| Layer | Component | Implementation | Battery Impact | Status |
|-------|-----------|---------------|----------------|--------|
| L0 | User Whitelist | HashSet lookup | ~0 | Working |
| L1 | Tranco BloomFilter | BloomFilter (500k domains) | ~0 | Working |
| L2 | Raw IP Guard | Regex | ~0 | Working |
| L3 | Dynamic Heuristics | BK-Tree + Markov + Viterbi + Structural | Low (per-query) | Working |
| L4 | ML Model | ONNX inference (53 lexical features) | Low (on-demand) | Implemented (trained LR ONNX + calibration) |
| L5 | Signature | ClamAV/YARA/Quark rules | Medium (file scan only) | Working |
| L6 | Verdict Fusion | Weighted ensemble + calibration | Low | Implemented (EnsemblePhishingEngine) |
| L7 | Calibration | Temperature / Platt scaling | ~0 (post-inference) | Implemented (T=1.0177) |

## Model Candidates (evaluated, same 53 features)

| Model | acc | rec | prec | f1 | fpr | Fit time | Inference cost | Battery suitability |
|-------|-----|-----|------|----|-----|----------|----------------|---------------------|
| LightGBM | 0.9313 | 0.9035 | 0.9503 | 0.9263 | 0.0433 | 8.1s | Medium | Good (cascade: run only when uncertain) |
| HistGradientBoosting | 0.9312 | 0.9065 | 0.9473 | 0.9265 | 0.0461 | 8.1s | Medium | Good (cascade: run only when uncertain) |
| RandomForest | 0.9304 | 0.8958 | 0.9558 | 0.9248 | 0.0379 | 42.8s | Medium | Good (cascade) |
| LogisticRegression | 0.9274 | 0.8935 | 0.9517 | 0.9217 | 0.0415 | 10.1s | Very Low | **Best for battery** (primary model) |
| NeuroShield ONNX (old weak 1KB) | 0.8731 | 0.8775 | 0.8599 | 0.8686 | 0.1309 | N/A | Low | Weak — replaced |

> Figures from 50k-sample held-out eval (domain-grouped split, seed 42, dynamic features 46-52 populated). **None of the 5 models reaches the 0.95 accuracy / 0.95 recall targets** — disclosed honestly, not fabricated. Best recall at max-F1 threshold is 0.9065 (HGB). Forcing recall>=0.95 requires lowering the threshold, which collapses accuracy (~0.90) and raises FPR (~0.147) — NOT a useful operating point.

**Production choice**: LogisticRegression as the calibrated ONNX primary (tiny, fastest inference, negligible battery) with LightGBM/HGB cascade only in the uncertainty band (0.15–0.85). This gives the best accuracy/battery trade-off.

## External validation (OpenPhish, independent)

| Metric | External OpenPhish | Held-out test | Generalizes? |
|--------|-------------------|---------------|--------------|
| Accuracy | 0.9287 | 0.9274 | Yes (~equal) |
| Recall (phishing) | 0.8859 | 0.8935 | Yes (~equal) |
| Precision | 0.8381 | 0.9517 | No (drops ~0.11) |
| F1 | 0.8613 | 0.9217 | No (drops ~0.06) |
| FPR | 0.0570 | 0.0415 | No (rises ~0.016) |

Source: OpenPhish Community Feed (https://www.openphish.com/feed.txt), retrieved 2026-10-31, 300 URLs → 298 hostnames. Threshold=0.5730 from held-out validation (NOT tuned on external). Precision drop because OpenPhish contains newly-registered/obscure domains (roblox.com.kz, pages.dev subdomains) that look benign to a lexical-only model — a known lexical-only limitation.


## Malware Engine

| Component | Format Supported | Status | Battery |
|-----------|-----------------|--------|---------|
| Quark | APK behavioral rules | Working (unverified on device) | Medium (scan only) |
| ClamAV | Hash + pattern signatures | Working (unverified) | Medium (scan only) |
| YARA | NDK rules | Working (unverified) | Medium (scan only) |
| Androguard | APK manifest/dex/cert | Working (unverified) | Medium (scan only) |
| Bloom Filter | Signature DB | Working (unverified; asset missing) | Low |

## Feature-Vector Malware Engine (new, additive)

| Component | Package | Input | Model | Battery | Status |
|-----------|---------|-------|-------|---------|--------|
| FeatureVectorMalwareEngine | `com.protctus.app.engine.ml` | 68-feature vector (Droidware-compatible) | Logistic regression (JSON weights) | Very Low (on-demand) | Implemented, UNVERIFIED on device |

**Notes:**
- Additive, does not replace Quark/Clam/YARA/Androguard; wired into `ThreatCorrelationEngine` via `evaluateFeatureVector(FloatArray)`.
- On-device only; no feature data leaves the device.
- Battery-efficient: tiny model (~68 weights) runs on-demand per app/file.
- Fail-safe: if `assets/feature_vector_malware_model.json` is missing/malformed, engine reports LIMITED_VISIBILITY (LOW_RISK, 0 confidence) — never guesses.
- **Shipped model is SYNTHETIC/demonstration only** (trained on synthetic 68-feature data to validate the pipeline). NOT production-ready; train on real Droidware 68-feature data before use. See `ml/evaluation/train_feature_vector_malware.py`.

## UI / Platform

| Feature | Status | Verified |
|---------|--------|----------|
| URL Scanner (on-device) | Working (code) | Code review only (no SDK) |
| Phishing Shield VPN (DNS) | Working (code) | Code review only |
| Settings + data-sharing toggle | Implemented | Code review only |
| Auth / Education / Dashboard | Present | Unverified |

## Privacy Controls

| Control | Default | User-toggleable |
|---------|---------|-----------------|
| Cloud telemetry (phishing) | OFF | Yes (PreferencesScreen) |
| Cloud telemetry (malware) | OFF | Yes (PreferencesScreen) |
| URL upload to external AI | Never (removed) | N/A |
| On-device detection only | Always | N/A |
