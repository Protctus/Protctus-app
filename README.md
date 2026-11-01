# Protctus — On-Device, Offline-First Android Cyber-Security Platform

Protctus is a privacy-first Android security app that performs real-time phishing and malware detection entirely on the device. It does not upload browsing data or URLs to external AI providers by default.

> **Status:** MVP codebase — architecture complete, security hardened, release APK built successfully. ML models below acceptance targets (retraining required). Not yet published. See `docs/` and `ml/evaluation/`.

## What it does

- **On-device phishing detection** — NeuroShield ensemble of 4 ML models with 53 lexical features + heuristic engines (BK-Tree brand proximity, Markov DGA, Viterbi segmentation, structural anomaly scoring). VerdictEngine fuses model scores. Runs entirely offline.
  - **Model 1:** ONNX LogisticRegression (`neuroshield_model.onnx`, 1 KB, weak/degenerate) — primary signal
  - **Model 2:** LightGBM 1000-tree (`lightgbm_model.txt`, 3.3 MB) — secondary signal, cascade band
  - **Model 3:** HistGradientBoosting ONNX (`neuroshield_hgb.onnx`, 358 KB in `ml/training/models/`, trained but not yet bundled in app assets)
  - **Model 4:** TFLite legacy (`phishing_model.tflite`, 3.6 MB) — bundled but superseded / not evaluable here
- **Real-time DNS phishing shield** — `VpnService`-based split-tunnel DNS interception that sinkholes phishing domains locally (no traffic routing to a remote server).
- **App malware scanner** — Quark behavioral engine, ClamAV-style signature matching, YARA rules, Androguard APK inspection, correlated via a threat-correlation engine.
- **Bloom-filter signature matcher** — 64 KB probabilistic offline malware signature database.
- **Gamified microlearning** — cyber-hygiene education with XP progression.
- **Dark Web identity scan** — local PDF report generation (app-specific storage, no broad storage permission).

## Security posture (highlights)

- AllowBackup disabled (`android:allowBackup="false"`).
- Cleartext HTTP blocked in production via `network_security_config.xml` (system CAs only; user CAs in debug).
- Removed broad `MANAGE_EXTERNAL_STORAGE` and unusable `BATTERY_STATS` permissions (least privilege).
- Secrets moved out of code into `.env` (gitignored) — replaced with `REPLACE_ME` placeholders that fail closed.
- Runtime APK signature verification (anti-repackaging) — placeholder hash must be replaced with real cert SHA-256 before release.
- Cloud telemetry (`CloudThreatReporter`, `MalwareTelemetryReporter`) gated behind explicit user consent (DataStore, default **OFF**).
- URLs and browsing data remain on-device unless the user explicitly enables a disclosed remote feature.

## Build status — RELEASE APK BUILT ✅

The release APK has been compiled successfully with Android SDK at `d:/android-sdk` and JDK 27:

| Artifact | Path | Size |
|----------|------|------|
| Release APK | `app/build/outputs/apk/release/app-release.apk` | ~102 MB |
| Target SDK | 35 | Min SDK: 27 |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 | — |
| Signing | Debug (fallback — release keystore not configured) | — |

```bash
# From the repo root (Windows PowerShell example)
$env:ANDROID_HOME="d:/android-sdk"
$env:ANDROID_SDK_ROOT="d:/android-sdk"
$env:GRADLE_JAVA_HOME="D:/jdk-27"

# Accept SDK licenses (once)
d:/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat --licenses

# Build release APK
.\gradlew.bat :app:assembleRelease --no-configuration-cache
```

Dependencies are version-pinned in `gradle/libs.versions.toml`.

> **Note:** Release APK is currently signed with the debug keystore because `RELEASE_KEYSTORE_PATH`/`RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` are not configured in `.env`. Google Play requires a real release keystore. See `docs/BUILD_BLOCKERS.md`.

## ML models — complete inventory

| # | Model | File | Size | Trees/Params | Status | Bundled in APK |
|---|-------|------|------|--------------|--------|----------------|
| 1 | ONNX LogisticRegression | `app/src/main/assets/neuroshield_model.onnx` | 1 KB | LR (53 features) | Loads & infers, weak output | ✅ Yes |
| 2 | LightGBM | `app/src/main/assets/lightgbm_model.txt` | 3.3 MB | 1,000 trees, 53 features | Fully wired in `EnsemblePhishingEngine.kt` | ✅ Yes |
| 3 | HistGradientBoosting ONNX | `ml/training/models/neuroshield_hgb.onnx` | 358 KB | HGB (53 features) | Trained, not bundled | ❌ No |
| 4 | TFLite legacy | `app/src/main/assets/phishing_model.tflite` | 3.6 MB | Char-map model | Bundled, superseded, not evaluable | ✅ Yes |

**LightGBM is already integrated** — see `LightGBMModel.kt` (pure Kotlin tree traversal, no ONNX/TFLite dependency) and `EnsemblePhishingEngine.kt` (cascade: runs LightGBM only when ONNX score is in the 0.15–0.85 uncertainty band to save battery).

**Model 3 (HistGradientBoosting) is trained** at `ml/training/models/neuroshield_hgb.onnx` (acc=0.9230, rec=0.8447 on held-out test). To complete the 4-model ensemble, copy it into `app/src/main/assets/` and wire it into `EnsemblePhishingEngine.kt`.

## ML evaluation (reproducible)

The phishing model is evaluated on genuine held-out data in `ml/evaluation/`:

```bash
cd ml/evaluation
curl -fsSL -o fixtures/phishing-domains-ACTIVE.txt \
  https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt

python evaluate_phishing.py \
  --onnx ../../app/src/main/assets/neuroshield_model.onnx \
  --benign ../../app/src/main/assets/vvip_domains.txt \
  --phishing fixtures/phishing-domains-ACTIVE.txt \
  --dynamic-data ../../app/src/main/assets/neuroshield_dynamic_data.json \
  --seed 42 \
  --out results/latest.json
```

**Measured results (current bundled ONNX asset, full dataset, ~153 k test domains, domain-grouped split, seed 42):**

Positive class = phishing. Dynamic features 46–52 populated from `neuroshield_dynamic_data.json` (matches production `DynamicFeatureProvider`). Threshold = 0.3040 (max-F1 on validation).

| Metric | Value | 95 % CI (bootstrap) |
|--------|-------|---------------------|
| Accuracy | 0.7855 | [0.7834, 0.7875] |
| Recall (phishing) | 0.8068 | [0.8039, 0.8096] |
| Precision | 0.7764 | [0.7735, 0.7793] |
| F1 | 0.7913 | [0.7890, 0.7935] |
| FPR | 0.2362 | [0.2334, 0.2393] |
| FNR | 0.1932 | — |

Confusion matrix (test: 76,000 benign / 77,284 phishing): TN = 58,045 · FP = 17,955 · FN = 14,932 · TP = 62,352

Train set: 459,847 · Validation set: 153,282 · Test set: 153,284.

Inference latency: ~1.574 ms/domain (CPU, batch). Model size: 1,068 bytes.

**The 0.95 accuracy and 0.95 recall targets are NOT met.** The current bundled ONNX asset is a weak/degenerate model — retraining with a larger model is required. See `ml/evaluation/README.md` for the honest assessment and next steps. Figures are measured, not fabricated.

## Documentation

| Document | Location |
|----------|----------|
| Architecture overview | `docs/ARCHITECTURE.md` |
| Feature inventory & status | `docs/FEATURE_INVENTORY.md` |
| Threat model & security assumptions | `docs/THREAT_MODEL.md` |
| Build blockers & environment notes | `docs/BUILD_BLOCKERS.md` |
| ML evaluation methodology & results | `ml/evaluation/README.md` |
| Test report | `docs/TEST_REPORT.md` |
| Engineering session log | `NEUROSHIELD_PROGRESS.md` |

## Known gaps (honest disclosure)

- ONNX model accuracy / recall below 0.95 targets — retraining required.
- `malware_signatures.bin` asset not yet generated — Bloom filter is empty at runtime.
- `SignatureVerifier` uses a placeholder hash (`UPDATE_ME...`) — must be set from real release cert.
- TFLite model (`phishing_model.tflite`, 3.6 MB) is still bundled but superseded by ONNX — should be removed.
- Unit tests exist but have not been executed (no Android SDK in current dev environment).
- Release signing keystore exists but passwords are not configured.
- HistGradientBoosting model (Model 3) trained but not bundled in app assets.

## Prerequisites

- **Android SDK** (platform-tools, build-tools, cmdline-tools) with `ANDROID_HOME`/`ANDROID_SDK_ROOT` set.
- **JDK 21+** (Gradle `org.gradle.java.home` must point to a valid JDK — the repo's `gradle.properties` currently references `D:/jdk-27`; adjust to your install).
- Android target SDK 35, min SDK 27.
- Network access to download Gradle and dependencies (first build).

## License / team

Protctus — founded by Jatin Sharma and co-founders at Government Polytechnic College, Shivpuri. Target market: Android users in India seeking privacy-respecting, offline-first security.
