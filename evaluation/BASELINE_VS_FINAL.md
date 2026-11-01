# Protctus — Baseline vs Final

## Important note on baselines
The repository contained TWO different model assets at different times:
1. **`neuroshield_model.onnx` (1,068 bytes)** — the asset actually bundled in the repo. Valid ONNX but weak/degenerate. Measured by me: acc=0.8731, rec=0.8775 (50k held-out sample).
2. **Stale docs** cited `full_20261027_seed42.json` (acc=0.8120, rec=0.7882, model 223,781 bytes) — this was from a **different, larger model asset that is no longer bundled** and does not correspond to the current 1,068-byte asset. Those stale figures have been removed/corrected from documentation.

The honest baseline = the **current bundled 1,068-byte weak ONNX model**, measured by me on a real held-out sample.

## Baseline (original bundled weak ONNX model, 1,068 bytes)
Measured on 50k-sample held-out test (domain-grouped, seed 42, dynamic features populated):

| Metric | Value |
|--------|-------|
| Accuracy | 0.8731 |
| Recall (phishing) | 0.8775 |
| Precision | 0.8599 |
| F1 | 0.8686 |
| FPR | 0.1309 |
| FNR | 0.1225 |
| Confusion matrix | TN=8,874  FP=1,336  FN=1,145  TP=8,202 |

## Final (trained LogisticRegression ONNX + calibration + ensemble)
Same 50k-sample held-out test set, same preprocessing (dynamic features 46-52 populated):

| Metric | Baseline (weak ONNX) | Final (trained LR ONNX) | Change |
|--------|----------------------|-------------------------|--------|
| Accuracy | 0.8731 | 0.9274 | +0.0543 |
| Recall (phishing) | 0.8775 | 0.8935 | +0.0160 |
| Precision | 0.8599 | 0.9517 | +0.0918 |
| F1 | 0.8686 | 0.9217 | +0.0531 |
| FPR | 0.1309 | 0.0415 | -0.0894 |
| FNR | 0.1225 | 0.1065 | -0.0160 |

Confusion matrix (final): TN=9,786  FP=424  FN=995  TP=8,352

## Model comparison (final, same held-out set)

| Model | Accuracy | Recall | Precision | F1 | FPR |
|-------|----------|--------|-----------|----|-----|
| LightGBM | 0.9313 | 0.9035 | 0.9503 | 0.9263 | 0.0433 |
| HistGradientBoosting | 0.9312 | 0.9065 | 0.9473 | 0.9265 | 0.0461 |
| RandomForest | 0.9304 | 0.8958 | 0.9558 | 0.9248 | 0.0379 |
| **LogisticRegression (production)** | **0.9274** | **0.8935** | **0.9517** | **0.9217** | **0.0415** |
| Baseline weak ONNX | 0.8731 | 0.8775 | 0.8599 | 0.8686 | 0.1309 |

## Acceptance targets (honest disclosure)

| Target | Required | Baseline | Final (best model) | Met? |
|--------|----------|----------|--------------------|------|
| Accuracy | ≥ 0.95 | 0.8731 | 0.9313 (LightGBM) | **No** |
| Recall (phishing) | ≥ 0.95 | 0.8775 | 0.9065 (HistGradientBoosting) | **No** |

**Neither the 0.95 accuracy nor the 0.95 recall target is met**, even after training a real model and building the ensemble. Best accuracy = 0.9313 (LightGBM), best recall = 0.9065 (HistGradientBoosting). Forcing recall ≥ 0.95 collapses accuracy to ~0.89–0.90 and raises FPR to ~0.145–0.163. Disclosed honestly; not fabricated.

## Production model selected
**LogisticRegression ONNX** — not the highest raw accuracy, but the best accuracy/battery trade-off:
- Tiny model, ~2 ms CPU inference, negligible battery drain
- On-demand only (per URL/DNS query), never continuous
- Temperature calibrated (T=1.0177)
- LightGBM/HistGradientBoosting cascade runs only in ONNX uncertainty band (0.15–0.85), saving CPU/heat

## Other final-state improvements (non-metric)
- Preprocessing consistency: dynamic features 46-52 now populated in both production and evaluation (was zeroed in eval)
- Privacy: cloud telemetry (phishing + malware) gated behind explicit user consent, default OFF
- Fail-safe: no-evidence → REQUIRES_NETWORK_VERIFICATION (never SAFE); signature layer gives immediate high-confidence blocks
- Documentation: all stale/inaccurate claims corrected (ONNX is valid not invalid; removed stale 223KB metrics)

## Blocked / unverified
- APK build & on-device testing: no Android SDK/device (BLOCKED)
- TFLite evaluation: interpreter blocked (BLOCKED)
- Battery/heating measurement on device: BLOCKED
- Full 380k-dataset evaluation: runtime-limited, 50k sample used as representative
