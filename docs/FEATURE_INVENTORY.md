# Protctus Feature Inventory & Implementation Status

Status definitions:
- **WORKING** — verified through a reproducible test.
- **PARTIALLY WORKING** — implemented but incomplete or unreliable.
- **BROKEN** — fails its intended behavior.
- **MISSING** — not implemented.
- **UNVERIFIED** — insufficient evidence (cannot build/run Android in this environment).

> **Important:** Because no Android SDK is available in this environment, the app cannot be compiled or instrument-tested here. All Android-facing statuses are therefore **UNVERIFIED** unless backed by pure-JVM unit tests or offline analysis. ML metrics are measured offline in Python.

## Phishing detection

| Feature | Status | Evidence / Notes |
|---------|--------|------------------|
| On-device NeuroShield ONNX inference (53 lexical features) | PARTIALLY WORKING | Model loads & infers offline in Python (VERIFIED valid ONNX, 1,068 bytes) but is weak/degenerate: measured acc=0.7855 rec=0.8068 on full held-out data — below 0.95/0.95 targets. Integrated into PhishingDetector/NeuroShieldEngine. Android-side UNVERIFIED (no SDK). Dynamic features 46-52 populated in production; eval now matches production. |
| PhishingDetector pipeline (whitelist → Bloom → IP → heuristics → ONNX → verdict) | PARTIALLY WORKING | Code present and reviewed. Android runtime UNVERIFIED. |
| URL scanner UI (`UrlScannerScreen`) | PARTIALLY WORKING | Rewritten to use on-device `PhishingDetector` (was TFLite + cloud AI). UNVERIFIED on device. |
| Real-time DNS phishing shield (`PhishingVpnService` + native `vpn-core`) | PARTIALLY WORKING | Native DNS loop present; loads `vpn-core` library. UNVERIFIED (no device). Sends upstream DNS to hardcoded 8.8.8.8 (privacy note in THREAT_MODEL.md). |
| TFLite char-map model (`OfflinePhishingModel`, `phishing_model.tflite`) | PARTIALLY WORKING / UNVERIFIED | Still referenced by `PhishingDomainEvaluator` (unused class) and was used by old UI. Superseded by ONNX. Not evaluated (TFL interpreter blocked in this env). |
| PhishingDomainEvaluator (3-tier) | UNVERIFIED / UNUSED | Class defined but not referenced anywhere. Uses TFLite. |
| ML accuracy ≥ 0.95 | NOT MET | Measured 0.7855 on current 1,068-byte ONNX asset (full held-out data). See ml/evaluation/README.md & current_asset_full_dynamic.json. |
| ML recall ≥ 0.95 | NOT MET | Measured 0.8068 on current 1,068-byte ONNX asset (full held-out data). |

## Malware / app scanning

| Feature | Status | Evidence / Notes |
|---------|--------|------------------|
| Quark behavioral engine (5-stage crime model) | PARTIALLY WORKING | Rules loaded from assets; `QuarkEngine.smartScanPackage` implemented. UNVERIFIED on device. |
| ClamAV signature matcher (hash + pattern) | PARTIALLY WORKING | `ClamSignatureEngine`, `ClamSignatureLoader`. Signature assets present. UNVERIFIED. |
| YARA rule engine (NDK) | PARTIALLY WORKING | `yara-core-module` + `YaraScanner`. UNVERIFIED. |
| Androguard APK inspector (manifest/dex/cert) | PARTIALLY WORKING | `AndroguardEngine` + submodules. UNVERIFIED. |
| ThreatCorrelationEngine consensus | PARTIALLY WORKING | Correlates Quark/Clam/YARA/Androguard + origin context. UNVERIFIED. |
| Bloom filter (64 KB MurmurHash3) | PARTIALLY WORKING | `SignatureDatabase`. Unit-tested (pure JVM). `malware_signatures.bin` asset MISSING → filter empty at runtime. |
| Unit tests (bloom filter, murmur hash, perf) | WORKING (pure JVM) | `SignatureDatabaseTest`, `PerformanceTest` — pure logic, runnable on JVM. |

## App / platform

| Feature | Status | Evidence / Notes |
|---------|--------|------------------|
| Hilt DI, Compose UI, navigation graph | UNVERIFIED | Code present; no Android SDK to build/run. |
| Room DB + DataStore settings | UNVERIFIED | Code present; DAOs/entities defined. |
| Firebase Auth / Firestore / FCM | UNVERIFIED | Configured; credentials are placeholders in `.env` (fail closed). |
| Razorpay payments | UNVERIFIED | Test-key placeholders; payment flow disabled without real keys. |
| Biometric unlock / PIN | UNVERIFIED | Screens present; `USE_BIOMETRIC` declared. |
| Periodic WorkManager scans/notifications | UNVERIFIED | Workers registered in `MainActivity.registerWorkers`. |
| APK signature verification (anti-repackaging) | UNVERIFIED | `SignatureVerifier` with placeholder hash (`UPDATE_ME...`) — fails closed only if hash set; currently logs and continues on mismatch (placeholder). |
| VPNService consent & foreground special-use | UNVERIFIED | Declared in manifest; `PhishingVpnService` implements consent flow. |

## UI / UX

| Feature | Status | Evidence / Notes |
|---------|--------|------------------|
| Auth flow (sign in/up, verify, reset) | UNVERIFIED | Screens present; Firebase-backed. |
| Education / microlearning (XP, quests) | UNVERIFIED | Screens + ViewModels present. |
| Settings, preferences, privacy policy | UNVERIFIED | Screens present. |
| Honest security states (no fake "Protected" badges) | PARTIALLY WORKING | URL scanner now reports real inference verdicts; no hardcoded "secure" claims. |

## Removed / hardened (this cycle)

- Removed `MANAGE_EXTERNAL_STORAGE` + `BATTERY_STATS` permissions (least privilege).
- Set `android:allowBackup="false"`.
- Replaced committed `.env` secrets with `REPLACE_ME` placeholders; `.env` added to `.gitignore`.
- Deleted dead stub `test_link.kt`.
- Removed cloud RBI/Llama3 URL upload from `UrlScannerScreen` (privacy).
- Consolidated URL scanner to on-device `PhishingDetector` pipeline.

## Known gaps

- APK cannot be built in this environment (no Android SDK; `gradle.properties` java.home invalid). See `docs/BUILD_BLOCKERS.md`.
- Instrumented/Espresso tests not runnable here; unit tests not executed.
- TFLite model unevaluated (interpreter blocked by application-control policy).
- ONNX model below 0.95 accuracy/recall targets (measured, disclosed).
