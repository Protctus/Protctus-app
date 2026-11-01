# Protctus Test Report

## Environment (execution constraints)

- No Android SDK available → APK build and instrumented (Espresso/AndroidJUnitRunner) tests **could not be executed**.
- No working TFLite interpreter (application-control policy) → TFLite model inference **could not be executed**.
- JVM available: Java 27, Python 3.14.7 with `onnxruntime`, `numpy`, `scikit-learn`, `tensorflow-cpu` (blocked at import).
- Pure-JVM/Kotlin unit tests written but **not executed** (no Android test runner; JUnit-only tests require the Android Gradle plugin to invoke).

## Tests written (source)

| File | Type | Coverage | Executable here? |
|------|------|----------|------------------|
| `app/src/test/.../engine/SignatureDatabaseTest.kt` | Unit (pre-existing) | Bloom filter, MurmurHash3, FPR | No (needs AGP) |
| `app/src/test/.../engine/PerformanceTest.kt` | Unit/perf (pre-existing) | Bloom/perf/murmur/file-risk | No (needs AGP) |
| `app/src/test/.../engine/UrlScannerEngineTest.kt` | Unit (new, Robolectric) | URL host extraction, IP guard, malformed input, heuristic cap | No (needs AGP+Robolectric) |
| `app/src/test/.../neuroshield/engine/LexicalFeatureExtractorTest.kt` | Unit (new) | 53-feature contract, determinism, punycode/IP, edge cases | No (needs AGP) |
| `app/src/test/.../neuroshield/engine/VerdictEngineTest.kt` | Unit (new) | Threshold classification, degraded mode, explanation | No (needs AGP) |
| `app/src/test/.../security/IntegrityShieldTest.kt` | Unit (pre-existing) | — | No |
| `app/src/test/.../security/TotpCryptoManagerTest.kt` | Unit (pre-existing) | TOTP | No |

> Test status: **UNVERIFIED (not executed)** — none of the above were run in this environment.

## ML evaluation (executed offline in Python)

This is the only quantitative verification completed. See `ml/evaluation/README.md` and `ml/evaluation/evaluate_phishing.py`.

### Methodology (verified)

- Benign: `app/src/main/assets/vvip_domains.txt` (380,000 trusted domains).
- Phishing: `phishing-domains-ACTIVE.txt` (392,178 community-verified phishing domains).
- Overlap between classes removed; domains validated/deduplicated.
- Stratified train/val/test split (60/20/20), fixed seed 42.
- Validation set used **only** for threshold selection (max F1). Test set held out until final scoring.
- Features: Python port of production `LexicalFeatureExtractor` (53 features, identical order).
- Bootstrap 95% CIs (2,000 resamples).
- sklearn Logistic Regression / Random Forest / HistGradientBoosting compared on the same features/test set.

### Results — NeuroShield ONNX (full dataset, current bundled asset)

**Note:** The current bundled asset `app/src/main/assets/neuroshield_model.onnx` is **1,068 bytes**. It IS a valid ONNX model (loads & infers: input `float_input [None,53]`) but is weak/degenerate — far below the multi-megabyte model that produced the earlier 0.8120 figures (those were from a different, larger asset no longer bundled).

Evaluation now populates dynamic features 46-52 from `neuroshield_dynamic_data.json`, matching production `DynamicFeatureProvider` (previously these were zeroed — a training/eval inconsistency now fixed).

Positive class = phishing. Test set: 76,000 benign / 77,284 phishing (153,284 total); train 459,847 / val 153,282. Threshold = 0.3040 (max-F1 on validation). Domain-grouped split (registrable domain) to prevent cross-split leakage.

| Metric | Value | 95% CI (bootstrap) |
|--------|-------|--------------------|
| Accuracy | 0.7855 | [0.7834, 0.7875] |
| Recall | 0.8068 | [0.8039, 0.8096] |
| Precision | 0.7764 | [0.7735, 0.7793] |
| F1 | 0.7913 | [0.7890, 0.7935] |
| FPR | 0.2362 | [0.2334, 0.2393] |
| FNR | 0.1932 | — |

Confusion matrix: TN=58,045  FP=17,955  FN=14,932  TP=62,352

Inference latency: ~1.574 ms/domain (CPU, batch). Model size: 1,068 bytes.

recall ≥ 0.95: only reachable at threshold ≈ 0.0 → test acc=0.5042, rec=1.0, fpr=1.0 (NOT a useful operating point). The model cannot achieve 0.95 recall at any useful threshold.

### Acceptance targets

| Target | Required | Measured | Met? |
|--------|----------|----------|------|
| Accuracy | ≥ 0.95 | 0.7855 | **No** |
| Recall (phishing) | ≥ 0.95 | 0.8068 | **No** |

**Both targets are NOT met.** Disclosed honestly; not fabricated.

### Lightweight-model comparison (same 53 features, same held-out test set)

In progress. LogisticRegression: test acc=0.8677, rec=0.8126, fpr=0.0762 (also below 0.95/0.95). RandomForest / HistGradientBoosting results pending completion of full-data training.

## Security review (static)

Conducted per OWASP MASVS/MSTG. Key findings (see `docs/THREAT_MODEL.md`):

- Removed `MANAGE_EXTERNAL_STORAGE` + `BATTERY_STATS`; `allowBackup="false"`.
- `.env` secrets replaced with placeholders + gitignored.
- SignatureVerifier placeholder hash — must be set for release (currently non-fail-closed).
- Native VPN upstream DNS hardcoded to 8.8.8.8 (privacy).
- `getNativeMetrics` returns zeros (placebo).
- Cloud RBI/Llama3 URL upload removed from UI scanner (privacy).
- URL scanner consolidated to on-device `PhishingDetector` pipeline.

## Known failures & limitations

1. APK not built (no Android SDK) — B-01, B-02.
2. Unit tests not executed — no AGP/Android test runner.
3. TFLite model not evaluated — interpreter blocked — B-03.
4. ML accuracy 0.812 / recall 0.788 below 0.95 targets — disclosed.
5. `malware_signatures.bin` asset missing → Bloom filter empty at runtime.
6. Release signing not configured.

## Next steps for verification

1. Resolve B-01/B-02; build debug APK.
2. Run `:app:testDebugUnitTest` and fix failures.
3. Run Robolectric unit tests (new tests above).
4. Evaluate TFLite model in a permissive environment or retire it in favor of ONNX.
5. Improve ML pipeline to approach 0.95/0.95 (dynamic features, better data, retrain).
6. Set real SignatureVerifier hash; configure release signing.
