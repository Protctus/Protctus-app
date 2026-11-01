# Protctus — On-Device, Offline-First Android Cyber-Security Platform

Protctus is a privacy-first Android security app that performs real-time phishing and malware detection entirely on the device. It does not upload browsing data or URLs to external AI providers by default.

> **Status:** MVP codebase undergoing security hardening and ML verification (see `docs/` and `ml/evaluation/`).

## What it does

- **On-device phishing detection** — NeuroShield (LightGBM → ONNX, ~218 KB) lexical model + heuristic engines (BK-Tree brand proximity, Markov DGA, Viterbi segmentation, structural anomaly scoring). Runs entirely offline.
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
- Runtime APK signature verification (anti-repackaging) with fail-closed hard crash on mismatch.
- URLs and browsing data remain on-device unless the user explicitly enables a disclosed remote feature.

## Prerequisites

- **Android SDK** (platform-tools, build-tools, cmdline-tools) with `ANDROID_HOME`/`ANDROID_SDK_ROOT` set.
- **JDK 21** (Gradle `org.gradle.java.home` must point to a valid JDK — the repo's `gradle.properties` references `D:/jdk-21.0.5`; adjust to your install).
- Android target SDK 35, min SDK 27.
- Network access to download Gradle and dependencies (first build).

> **Build status in this environment:** Compilation/APK build could not be completed because no Android SDK is present and `org.gradle.java.home` points to a non-existent path (`D:/jdk-21.0.5`). See `docs/BUILD_BLOCKERS.md`. ML evaluation was performed offline in Python and is documented in `ml/evaluation/`.

## Build

```bash
# From the repo root (Linux/macOS/WSL or Windows with Android SDK)
export ANDROID_HOME=$HOME/Android/Sdk
export GRADLE_JAVA_HOME=/path/to/jdk-21

./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # requires release keystore config in local.properties
./gradlew :app:testDebugUnitTest
```

Dependencies are version-pinned in `gradle/libs.versions.toml`.

## ML evaluation (reproducible)

The phishing model is evaluated on genuine held-out data in `ml/evaluation/`:

```bash
cd ml/evaluation
curl -fsSL -o fixtures/phishing-domains-ACTIVE.txt \
  https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt

python evaluate_phishing.py \
  --onnx ../app/src/main/assets/neuroshield_model.onnx \
  --benign ../app/src/main/assets/vvip_domains.txt \
  --phishing fixtures/phishing-domains-ACTIVE.txt \
  --seed 42 \
  --out results/latest.json
```

**Measured results (full dataset, ~240k domains, held-out test set):**

| Metric | Value | 95% CI |
|--------|-------|--------|
| Accuracy | 0.812 | [0.808, 0.816] |
| Recall (phishing = positive) | 0.788 | [0.783, 0.793] |
| Precision | 0.828 | [0.823, 0.833] |
| F1 | 0.807 | [0.803, 0.811] |
| FPR | 0.164 | [0.160, 0.169] |
| FNR | 0.212 | — |

Confusion matrix (test: 24,000 benign / 23,996 phishing): TN=20,059  FP=3,941  FN=5,082  TP=18,914

Inference latency: ~2.7 ms/domain (CPU). Model size: 223,781 bytes (~218.5 KB).

**The 0.95 accuracy and 0.95 recall targets are NOT met.** See `ml/evaluation/README.md` for the honest assessment and next steps. Figures are measured, not fabricated.

## Documentation

| Document | Location |
|----------|----------|
| Architecture overview | `docs/ARCHITECTURE.md` |
| Feature inventory & status | `docs/FEATURE_INVENTORY.md` |
| Threat model & security assumptions | `docs/THREAT_MODEL.md` |
| Build blockers & environment notes | `docs/BUILD_BLOCKERS.md` |
| ML evaluation methodology & results | `ml/evaluation/README.md` |
| Test report | `docs/TEST_REPORT.md` |

## License / team

Protctus — founded by Jatin Sharma and co-founders at Government Polytechnic College, Shivpuri. Target market: Android users in India seeking privacy-respecting, offline-first security.
