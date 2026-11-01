# Protctus ML Evaluation — Phishing Detection (NeuroShield ONNX)

This directory contains a **reproducible, offline-first evaluation harness** for the
Protctus phishing-detection model. It is the authoritative source for the reported
accuracy / recall / precision / F1 / FPR / FNR figures.

## Positive class

**Phishing = positive.** Benign = negative.

Metrics (with phishing as positive):
- Recall    = TP / (TP + FN)
- Precision = TP / (TP + FP)
- Accuracy  = (TP + TN) / (TP + TN + FP + FN)
- F1        = 2 × Precision × Recall / (Precision + Recall)
- FPR       = FP / (FP + TN)
- FNR       = FN / (FN + TP)

## Models evaluated

| Model | File | Size | Status |
|-------|------|------|--------|
| NeuroShield (LightGBM → ONNX) | `app/src/main/assets/neuroshield_model.onnx` | 223,781 bytes (~218.5 KB) | **Production path** — verified loads & infers |
| OfflinePhishingModel (TFLite) | `app/src/main/assets/phishing_model.tflite` | 3,599,788 bytes (~3.43 MB) | Secondary / legacy path — **not evaluated here** (no TFLite interpreter available in this environment) |

## Datasets (held-out, genuine)

- **Benign (negative):** `app/src/main/assets/vvip_domains.txt` — 380,000 Tranco-style top/trusted domains bundled with the app.
- **Phishing (positive):** `phishing-domains-ACTIVE.txt` from [Phishing-Database](https://github.com/Phishing-Database/Phishing.Database) (community-verified phishing domains), 392,178 domains at time of download (2026-10-26).

Overlap between classes is removed (a domain present in both is dropped from the phishing side).

## Methodology

1. Domains are validated (must contain a `.`, no spaces, ≤253 chars, no bare IPv4) and deduplicated.
2. Stratified train / validation / test split (60/20/20) with fixed RNG seed **42**.
3. The ONNX model is **already trained** — no training occurs. The validation set is used **only** to select the decision threshold (max F1). The test set is held out and untouched until final scoring.
4. Features are extracted with a Python port of `LexicalFeatureExtractor` (53 features, same order as the Kotlin production extractor).
5. Threshold sweep on validation; final confusion matrix and metrics computed on the **test set** at the chosen threshold.
6. Bootstrap 95% confidence intervals (2,000 resamples) reported for accuracy, recall, precision, F1, FPR.
7. Lightweight sklearn models (Logistic Regression, Random Forest, HistGradientBoosting) are trained on the same 53 features and compared against the ONNX baseline on the same held-out test set.

## Reproducing the results

Prerequisites: Python 3.10+, `onnxruntime`, `numpy`, `scikit-learn`, `idna`.

```bash
cd /path/to/Protctus
python ml/evaluation/evaluate_phishing.py \
    --onnx app/src/main/assets/neuroshield_model.onnx \
    --benign app/src/main/assets/vvip_domains.txt \
    --phishing ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \
    --seed 42 \
    --out ml/evaluation/results/latest.json
```

The phishing fixture is large (~11 MB, ~392k domains). Download it once:

```bash
curl -fsSL -o ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \
  https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt
```

## Reported results (headline)

Source: `ml/evaluation/results/current_asset_full_dynamic.json` — generated from the scripts in this directory
with dynamic features 46-52 populated (matches production `DynamicFeatureProvider` preprocessing).

NeuroShield ONNX, test set (76,000 benign / 77,284 phishing), threshold = 0.3040 (max-F1 on validation):

| Metric | Value | 95% CI (bootstrap) |
|--------|-------|--------------------|
| Accuracy | 0.7855 | [0.7834, 0.7875] |
| Recall (phishing) | 0.8068 | [0.8039, 0.8096] |
| Precision | 0.7764 | [0.7735, 0.7793] |
| F1 | 0.7913 | [0.7890, 0.7935] |
| FPR | 0.2362 | [0.2334, 0.2393] |
| FNR | 0.1932 | — |

Confusion matrix (test): TN=58,045  FP=17,955  FN=14,932  TP=62,352

Inference latency (CPU, batch): ~1.574 ms per domain.
Model size: 1,068 bytes (neuroshield_model.onnx).

### Acceptance targets vs. measured

| Target | Required | Measured (ONNX) | Met? |
|--------|----------|-----------------|------|
| Accuracy | ≥ 0.95 | 0.7855 | **No** |
| Recall (phishing) | ≥ 0.95 | 0.8068 | **No** |

**The 0.95 accuracy and 0.95 recall targets are NOT met.** Pushing recall to ≥0.95 requires a threshold of ≈ 0.0, which collapses accuracy to ~0.50 and FPR to 1.0 (everything flagged phishing) — i.e., the model's lexical-feature signal does not separate the classes strongly enough at any useful operating point.

**Important correctness notes:**
- The current bundled asset `neuroshield_model.onnx` is 1,068 bytes. It IS a valid ONNX model (loads and infers: input `float_input [None,53]`, outputs `output_label` + `output_probability`), but its output distribution is degenerate (near-constant phishing probability across inputs), so it is far weaker than a trained multi-megabyte LightGBM ONNX model would be. The earlier `full_20261027_seed42.json` (0.8120 accuracy, 223,781-byte model) corresponds to a different/larger model asset that is no longer bundled; do not compare across them.
- Evaluation preprocessing now populates dynamic features 46-52 from `app/src/main/assets/neuroshield_dynamic_data.json`, matching production `DynamicFeatureProvider`. (Earlier runs zeroed these features — a training/eval inconsistency that is now fixed.)
- The recall≥0.95 check selects, on validation only, the threshold achieving recall≥0.95 with minimum FPR (not the first threshold from 0.0, which would trivially be thr≈0.0 with FPR≈1.0).
- Latency is reported correctly as ms-per-domain (a prior ×1000 inflation bug is fixed).
- Splits group domains by registrable domain so related subdomains never leak across train/val/test.

Lightweight-model comparison (same 53 features, same held-out test set) — in progress; LogisticRegression test acc=0.8677 rec=0.8126 (also below 0.95/0.95).

## Honest assessment / next steps

- **Never fabricate compliance.** The figures above are measured on genuine held-out data and fall short of the targets.
- To approach the targets: replace the degenerate 1,068-byte ONNX asset with a valid trained LightGBM model (multi-megabyte), compute the dynamic brand/TLD-rate features from a real labeled training corpus, gather more diverse time-separated phishing + benign data, retrain, and re-evaluate on a fresh held-out test set.
- The ONNX model remains the production inference path (it loads and runs). The gap between measured performance and the target is a known, disclosed limitation.
