# WORKSPACE FILE INVENTORY
**Project:** Protctus | **Audit Date:** 2026-10-30 | **Root:** `A:\Protctus-app`

---

## 1. Workspace Roots
| Root | Type |
|------|------|
| `A:\Protctus-app` | Android multi-module monorepo (single root) |

---

## 2. Top-Level Directories

| Directory | Purpose | Inspection |
|-----------|---------|------------|
| `app/` | Primary Android application module | **Detailed** |
| `ml/` | ML training, evaluation harness, ONNX models | **Detailed** |
| `neuroshield/` | Rust crate `ns_engine` (native Windows inference) | Source inspected; `target/` excluded |
| `engines/proc/malware-engine3` | Vendored ClamAV C source | Inventoried only (third-party) |
| `engines/proc/malware-engine5` | Vendored Androguard Python | Inventoried only (third-party) |
| `phishing-engine/` | Cloudflare Worker (wrangler.toml + src/) | Shallow |
| `protctus-admin/` | Web admin dashboard (Vite/Three.js) | Shallow |
| `protctus-chakravyuh/` | Standalone Kotlin RASP file | **Detailed** |
| `cybermap/` | Empty — built assets in `app/assets/cybermap/` | Confirmed empty |
| `functions/` | Cloudflare Functions stub | `package.json` only |
| `payments-worker/` | Cloudflare Worker (Razorpay) | Shallow |
| `vpn-core-module/` | Android VPN library module | Build artifacts only |
| `yara-core-module/` | YARA NDK library module | Source inspected; `.cxx/` excluded |
| `migrations/` | DB migration files | Not expanded |
| `research/` | Research documents | Not expanded |
| `docs/` | Architecture, threat model, test report, feature inventory, build blockers | **All 5 files detailed** |
| `src/` | Single `index.ts` stub | Inspected |
| `gradle/` | Gradle wrapper + version catalog | Referenced |
| `.aether/` | Automation scripts/config | Not expanded |
| `reports/` | Audit deliverables (this directory) | Created during audit |

---

## 3. Android App Module — Key Source Files

### Kotlin Source (app/src/main/java/)

| Package | Key Files | Inspected |
|---------|-----------|-----------|
| `com.protctus.app` | `MainActivity.kt`, `ProtctusApplication.kt` | ✅ |
| `.neuroshield.engine` | `NeuroShieldEngine.kt`, `LexicalFeatureExtractor.kt`, `VerdictEngine.kt`, `EnsemblePhishingEngine.kt`, `DynamicFeatureProvider.kt`, `LightGBMModel.kt`, `TrustedDomainCache.kt` | ✅ |
| `com.protctus.edge.phishing` | `PhishingDetector.kt` (**DEF-001 fixed**), `PhishingVpnService.kt`, `BKTree.kt`, `BloomFilter.kt`, `MarkovChain.kt`, `ViterbiSegmenter.kt`, `CloudThreatReporter.kt` | ✅ |
| `.engine` | `SignatureDatabase.kt`, `VVIPAllowlist.kt`, `OfflinePhishingModel.kt`, plus `quark/`, `clam/`, `androguard/`, `correlation/` subdirs | Partial ✅ |
| `.repository` | `PhishingDomainEvaluator.kt` | ✅ |
| `.di` | `AppModule.kt`, `DatabaseModule.kt`, `NeuroShieldModule.kt`, `FirebaseModule.kt` | ✅ |
| `.security` | `SignatureVerifier.kt` | ✅ |
| `.ui.screens.*` | Auth, Dashboard, Scanner, Settings screens | Partial ✅ |
| `.viewmodel` | 16 ViewModels | File listing confirmed |
| `.worker` | `PeriodicScanWorker.kt`, `DailyNotificationWorker.kt`, `ThreatTelemetryWorker.kt` | File listing confirmed |

### Native C++ (app/src/main/cpp/)
| File | Purpose |
|------|---------|
| `edge_phishing_pipeline.cpp` | Native DNS TUN loop, block synthesis |
| `chakravyuh.cpp` | RASP self-protection (roadmap) |
| `native_scanner.cpp` | Native file scanner helper |

### Assets (app/src/main/assets/)
| Asset | Size | Status |
|-------|------|--------|
| `neuroshield_model.onnx` | **1,068 bytes** | ⚠️ INVALID |
| `phishing_model.tflite` | 3,599,788 bytes | Present; not evaluable |
| `lightgbm_model.txt` | 3,477,662 bytes | ✅ Valid |
| `vvip_domains.txt` | ~5 MB | ✅ 380K+ domains |
| `malware_signatures.bin` | **MISSING** | ⚠️ Bloom filter empty |
| `quark_rules/*.json` | ~55 files | ✅ Present |

---

## 4. ML Pipeline (ml/)
| File | Purpose | Inspected |
|------|---------|-----------|
| `evaluation/evaluate_phishing.py` | Full evaluation harness | ✅ |
| `evaluation/feature_extractor.py` | Python port of 53-feature extractor | ✅ |
| `evaluation/README.md` | Methodology + honest results | ✅ |
| `evaluation/results/full_20261027_seed42.json` | Full dataset results (n=47,996) | ✅ |
| `evaluation/results/smoke_seed42.json` | Smoke test results (n=6,000) | ✅ |
| `training/models/neuroshield_model.onnx` | Training copy — same invalid 1,068-byte file | ✅ |
| `training/models/lightgbm_model.txt` | LightGBM text model | ✅ Present |
| `training/scripts/` | ~20 training/export scripts | File listing only |

---

## 5. Documentation (docs/)
| File | Inspected | Accuracy vs Source |
|------|-----------|-------------------|
| `ARCHITECTURE.md` | ✅ | Matches source code |
| `BUILD_BLOCKERS.md` | ✅ | Accurate |
| `FEATURE_INVENTORY.md` | ✅ | Accurate; well-maintained |
| `TEST_REPORT.md` | ✅ | Accurate; honest about unverified tests |
| `THREAT_MODEL.md` | ✅ | Comprehensive; matches implementation |

---

## 6. Root Configuration
| File | Purpose | Inspected |
|------|---------|-----------|
| `build.gradle.kts` | Root Gradle (plugins only) | ✅ |
| `settings.gradle.kts` | Includes `:app`, `:yara-core-module` | ✅ |
| `gradle.properties` | JVM args; **invalid java.home** (D:/jdk-21.0.5 does not exist) | ✅ |
| `app/build.gradle.kts` | Full dependency manifest | ✅ |
| `package.json` | Node: gemini-cli, firebase-admin, google-translate-api | ✅ |
| `.env` | **NOT READ** (sensitive); all values confirmed as `REPLACE_ME` | Referenced only |
| `protctus-release-key.jks` | Release keystore binary | NOT READ (binary) |
| `README.md`, `ENGINEERING_REPORT.md`, `NEUROSHIELD_PROGRESS.md`, `incubation_summary.md` | Project docs | ✅ All inspected |

---

## 7. Counts and Reconciliation

| Metric | Value |
|--------|-------|
| Project roots discovered | 1 |
| Top-level directories | 19 |
| Kotlin source files (estimated total) | ~180 |
| Kotlin source files inspected in detail | ~65 |
| Documentation files inspected | 8 |
| Configuration files inspected | 18 |
| Assets with confirmed issues | 2 (`neuroshield_model.onnx` invalid; `malware_signatures.bin` missing) |
| Generated artifact trees excluded | 3 (Rust `target/`, YARA `.cxx/`, NDK `build/`) |
| Third-party vendored trees excluded | 2 (ClamAV C, Androguard Python) |

### Justified Exclusions
| Path | Reason |
|------|--------|
| `neuroshield/target/` | Rust build artifacts |
| `yara-core-module/.cxx/` | NDK CMake build artifacts |
| `engines/proc/malware-engine3/` | Vendored ClamAV C — third-party |
| `engines/proc/malware-engine5/` | Vendored Androguard Python — third-party |
| `.env` | Sensitive; all values confirmed as placeholders only |
| `protctus-release-key.jks` | Binary keystore |
| `ml/evaluation/fixtures/phishing-domains-ACTIVE.txt` | 392k-line data file |
| `migrations/`, `research/`, `.aether/` | Not expanded; no known source-behavior impact |


