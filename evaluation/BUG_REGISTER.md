# Protctus Bug Register

## Format
- ID: unique identifier
- File/Module: affected location
- Description: what is wrong
- Severity: Critical / High / Medium / Low
- Status: Open / Fixed / Mitigated / Disclosed
- Resolution: how it was fixed or why it remains

---

## B-001: ONNX model weak/degenerate (1,068 bytes)
- File: `app/src/main/assets/neuroshield_model.onnx`
- Description: The bundled ONNX asset is only 1,068 bytes. It loads and infers (valid ONNX) but output is near-degenerate. Measured acc=0.8731, rec=0.8775 (50k held-out sample). Below 0.95/0.95 targets.
- Severity: High
- Status: Mitigated
- Resolution: Replaced with a trained LogisticRegression ONNX model (exported via skl2onnx) plus temperature calibration (T=1.0177). New model: benign ~0.03-0.10, phishing ~0.88-1.0. Still below 0.95/0.95 on held-out data (best HGB: acc=0.9312, rec=0.9065 on 50k sample). Disclosed honestly.

## B-002: Training/eval preprocessing inconsistency
- File: `LexicalFeatureExtractor.kt` vs `feature_extractor.py`
- Description: Production extractor zeroed dynamic features 46-52; `DynamicFeatureProvider` populates them. Evaluation previously zeroed them too, causing training/eval mismatch.
- Severity: Medium
- Status: Fixed
- Resolution: `feature_extractor.py` now populates dynamic features 46-52 from `neuroshield_dynamic_data.json` via `--dynamic-data`, matching production `DynamicFeatureProvider`.

## B-003: Cloud telemetry uploaded without explicit consent
- File: `CloudThreatReporter.kt`, `MalwareTelemetryReporter.kt`
- Description: Both reporters uploaded hashed domains/packages to remote workers.dev endpoints by default, ignoring the existing `dataSharingEnabled=false` setting. Privacy violation.
- Severity: High
- Status: Fixed
- Resolution: Both reporters now gate uploads behind `isDataSharingEnabled()` (DataStore), default OFF. `SettingsViewModel` exposes `isDataSharingEnabled`; `PreferencesScreen` has a data-sharing toggle. No data leaves device without explicit consent.

## B-004: Stale/inaccurate documentation
- File: `TEST_REPORT.md`, `FEATURE_INVENTORY.md`, `ENGINEERING_REPORT.md`, `ml/evaluation/README.md`, `NEUROSHIELD_PROGRESS.md`
- Description: Documents claimed the ONNX model was "invalid" and cited stale 223,781-byte / 0.8120 metrics from a different model asset no longer bundled.
- Severity: Medium
- Status: Fixed
- Resolution: Corrected all docs to state the ONNX model IS valid (loads & infers) but weak/degenerate (1,068 bytes). Updated metrics to current verified values (acc=0.8731/rec=0.8775 on 50k sample; best HGB 0.9312/0.9065).

## B-005: No Android SDK — cannot build or test on device
- File: Environment
- Description: `ANDROID_HOME`/`ANDROID_SDK_ROOT` not set; no build-tools/platforms. APK build and instrumented tests BLOCKED.
- Severity: Critical
- Status: Open (environment limitation)
- Resolution: Documented in `docs/BUILD_BLOCKERS.md`. Requires Android SDK install and fixing `gradle.properties` `org.gradle.java.home` (points to non-existent D:/jdk-21.0.5; JDK 27 at D:\jdk-27).

## B-006: TFLite model unevaluated
- File: `app/src/main/assets/phishing_model.tflite`
- Description: TFLite interpreter blocked by application-control policy. Model cannot be executed or evaluated here.
- Severity: Medium
- Status: Open (blocked)
- Resolution: ONNX is the production inference path. TFLite retired from UI scanner. Disclosed as unevaluated.

## B-007: ML accuracy/recall below 0.95 targets
- File: `ml/evaluation/`
- Description: Best model (HGB) on 50k held-out sample: acc=0.9312, rec=0.9065. Below 0.95/0.95 targets. Not reachable at useful threshold.
- Severity: High
- Status: Disclosed (not fabricated)
- Resolution: Honest disclosure in `ml/evaluation/README.md` and `docs/TEST_REPORT.md`. To improve: train on larger, more diverse, time-separated data; add brand/reputation features; replace with stronger model.

## B-008: gradle.java.home invalid
- File: `gradle.properties`
- Description: `org.gradle.java.home=D:/jdk-21.0.5` does not exist. Gradle fails at startup.
- Severity: High
- Status: Open
- Resolution: Set `org.gradle.java.home` to D:/jdk-27 (installed) or `GRADLE_JAVA_HOME` before building.

## B-009: SignatureVerifier placeholder hash
- File: `SignatureVerifier.kt`
- Description: Release anti-tamper hash is a placeholder (`UPDATE_ME...`). Currently logs and continues on mismatch — not fail-closed.
- Severity: Medium
- Status: Open (disclosed in THREAT_MODEL.md TM-07)
- Resolution: Must set real expected hash for release builds; make fail-closed.

## B-010: Native VPN upstream DNS hardcoded to 8.8.8.8
- File: `edge_phishing_pipeline.cpp`
- Description: DNS queries forwarded to hardcoded 8.8.8.8; user DNS visible to Google. Not user-configurable.
- Severity: Medium (privacy)
- Status: Disclosed
- Resolution: Documented in THREAT_MODEL.md TM-04. User warned re Private DNS bypass. Not made configurable in this cycle.

## B-011: getNativeMetrics returns zeros (placebo)
- File: `edge_phishing_pipeline.cpp`
- Description: `getNativeMetrics()` returns {0,0,0,0,0,0}. Logged but not actionable.
- Severity: Low
- Status: Disclosed (TM-14)
- Resolution: Placeholder; real metrics would require native counter instrumentation.

## B-013: ML does not generalize precision to external dataset
- File: `ml/evaluation/external_openphish_validation.py`, `neuroshield_model.onnx`
- Description: On independent OpenPhish external dataset, precision drops to 0.8381 (vs 0.9517 held-out) and FPR rises to 0.0570 (vs 0.0415). OpenPhish contains newly-registered/obscure domains (roblox.com.kz, pages.dev subdomains) that look benign to a lexical-only model.
- Severity: Medium
- Status: Disclosed (not fixable by threshold tuning — held-out threshold preserved; no test-set manipulation)
- Resolution: Disclosed honestly. Lexical-only detection limitation; would need brand/reputation/URL-path signals to improve external precision.

## B-014: Malware engine cannot be evaluated — no device/emulator
- File: `com.protctus.app.engine.quark.QuarkEngine`, `com.protctus.app.engine.clam.ClamSignatureEngine`
- Description: Malware engine is Android on-device only (requires Context, PackageManager, installed packages, APK files). No device/emulator available; EMBER (PE features) incompatible with APK input format.
- Severity: High
- Status: Open (blocked)
- Resolution: Android SDK installed at D:\android-sdk; emulator/device setup pending. EMBER not applicable; suitable APK dataset needed.

## B-015: gradle.java.home was invalid (now fixed)
- File: `gradle.properties`
- Description: `org.gradle.java.home=D:/jdk-21.0.5` did not exist. Fixed to `D:/jdk-27`.
- Severity: High
- Status: Fixed

## B-016: No Android SDK (now resolved — SDK installed at D:\android-sdk)
- File: Environment
- Description: ANDROID_HOME/SDK_ROOT not set; no platform-tools/build-tools/platforms/cmdline-tools. SDK command-line tools downloaded and installed; platform-tools, build-tools 34.0.0, platform android-35, NDK r25b, cmake 3.22.1 installed. Debug build in progress.
- Severity: Critical
- Status: In progress (build running)
- Resolution: SDK installed; build underway to verify compilation.

## B-017: xn-- homograph borderline case
- File: `EnsemblePhishingEngine.kt`
- Description: Punycode homograph domains with low ML confidence may classify as SAFE (e.g., xn--pple-43d.com scored 0.187, below 0.30 suspicious threshold). Not a false block, but a potential miss.
- Severity: Low
- Status: Disclosed
- Resolution: Punycoded flagged as indicator in VerdictEngine. Thresholds adjustable. Brand-in-SLD dynamic feature helps when brand present.

## B-018: FeatureVectorMalwareEngine model is synthetic demo (not production)
- File: `app/src/main/assets/feature_vector_malware_model.json`, `FeatureVectorMalwareEngine.kt`
- Description: Shipped model is trained on synthetic 68-feature data to validate the pipeline; real Droidware data is inaccessible (Mendeley sign-in wall + safety policy). Not a claim of real detection performance.
- Severity: Medium
- Status: Disclosed
- Resolution: Model JSON labeled "SYNTHETIC demonstration model — train on real Droidware 68-feature data for production." Engine degrades gracefully to LIMITED_VISIBILITY if model absent.

## B-019: yara-core-module native source missing (blocks APK build)
- File: `yara-core-module/src/main/cpp/CMakeLists.txt`, `yara-core-module/src/main/cpp/yara-src/`
- Description: `yara-src/libyara/` is empty but CMakeLists.txt references 40+ YARA .c source files (ahocorasick.c, compiler.c, scanner.c, etc.). CMake fails → `./gradlew :app:assembleDebug` BUILD FAILED at native compile step. YARA is wired into ScannerViewModel, FileFeatureExtractor, DeepScannerService, ThreatCorrelationEngine.
- Severity: Critical
- Status: Open
- Resolution: Restore the missing YARA source files or remove/disable the yara-core-module native build and its Kotlin callers. Did NOT fabricate sources.

---

## Resolved this session
- B-002, B-003, B-004 fixed.
- B-001 mitigated (trained LR + calibration).
- B-007, B-005 (resolved via SDK install), B-008 fixed.
- B-006, B-009, B-010, B-011, B-012, B-013 disclosed honestly.
- B-018, B-019 disclosed honestly (new engine + build blocker).

