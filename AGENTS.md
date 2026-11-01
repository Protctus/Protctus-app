# Protctus — Agent Context

## Build environment (blocked in this workspace)
- **No Android SDK is installed.** `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` cannot run here.
- `gradle.properties` sets `org.gradle.java.home=D:/jdk-21.0.5`, which does not exist. Java 27 is at `D:\jdk-27`. Adjust `gradle.properties` or set `GRADLE_JAVA_HOME` before any Gradle command.
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` are not set.

## ML evaluation (runs here — the only verified path)
- `ml/evaluation/evaluate_phishing.py` evaluates the ONNX model on genuine held-out data.
- Requires: `onnxruntime`, `numpy`, `scikit-learn`, `idna`.
- Run from repo root:
  ```
  python ml/evaluation/evaluate_phishing.py \
    --onnx app/src/main/assets/neuroshield_model.onnx \
    --benign app/src/main/assets/vvip_domains.txt \
    --phishing ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \
    --seed 42 \
    --out ml/evaluation/results/latest.json
  ```
- The phishing fixture (~11 MB, 392k domains) must be downloaded once from `https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt`.
- TFLite model (`phishing_model.tflite`) is NOT evaluable here — the TFL interpreter is blocked by application-control policy. ONNX (`neuroshield_model.onnx`) is the production path.

## Kotlin tests (not executed here)
- `app/src/test/...` unit tests exist (SignatureDatabaseTest, PerformanceTest, new LexicalFeatureExtractorTest, VerdictEngineTest, UrlScannerEngineTest) but cannot run without the Android Gradle Plugin test runner.
- Do not claim they pass.

## Key source locations
- ONNX inference: `app/src/main/java/com/protctus/app/neuroshield/engine/NeuroShieldEngine.kt`
- Feature extraction (53 features): `app/src/main/java/com/protctus/app/neuroshield/engine/LexicalFeatureExtractor.kt`
- Verdict/thresholds: `app/src/main/java/com/protctus/app/neuroshield/engine/VerdictEngine.kt`
- Phishing pipeline (ONNX + heuristics): `app/src/main/java/com/protctus/app/edge/phishing/PhishingDetector.kt`
- VPN/DNS enforcement: `app/src/main/java/com/protctus/app/edge/phishing/PhishingVpnService.kt` + `app/src/main/cpp/edge_phishing_pipeline.cpp`
- URL scanner UI (was TFLite + cloud AI, now on-device ONNX): `app/src/main/java/com/protctus/app/ui/screens/scanner/UrlScannerScreen.kt`

## Security posture (already hardened)
- `android:allowBackup="false"`; `MANAGE_EXTERNAL_STORAGE` and `BATTERY_STATS` removed.
- `.env` real secrets replaced with `REPLACE_ME` placeholders; `.env` is gitignored.
- No cloud URL upload in the URL scanner — detection is on-device only.

## Docs
- `docs/BUILD_BLOCKERS.md` — environment blockers.
- `docs/THREAT_MODEL.md` — threats, mitigations, fail-open/closed.
- `docs/FEATURE_INVENTORY.md` — feature status inventory.
- `docs/TEST_REPORT.md` — test inventory, ML results, gaps.
- `NEUROSHIELD_PROGRESS.md` — current engineering session state and next task.

## Rules from the master prompt (do not violate)
- No fabricated metrics, mock detection, hardcoded success, or placeholder screens.
- No swallowed exceptions concealing failures.
- No insecure defaults, embedded secrets, or unnecessary permissions.
- Never claim TLS inspection, DNS visibility, domain-age lookup, or redirect monitoring unless the code really provides it.
- URLs/browsing data stay on-device unless the user explicitly enables a disclosed remote feature.
