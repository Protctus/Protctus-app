# PROTCTUS — POSTER FACTSHEET
## MITS Gwalior Macro Project Documentation
**Prepared:** 2026-10-30 | **Source:** Full workspace audit + offline ML evaluation

---

> **Evidence key used throughout this document:**
> - ✅ VERIFIED — confirmed by code inspection and/or offline ML evaluation
> - ⚠️ IMPLEMENTATION EVIDENCE — code present, Android runtime unverified
> - 🔬 ML EVALUATED — measured on held-out test data in Python (2026-10-27)
> - ❌ NOT MET — target not achieved; disclosed honestly
> - 📋 FUTURE WORK — planned, not yet implemented

---

## 1. Project Identity

| Field | Value |
|-------|-------|
| **Official Name** | Protctus |
| **Tagline** | On-Device, Offline-First Android Cyber-Security Platform |
| **Focus for Poster** | NeuroShield — the on-device phishing detection engine |
| **Stage** | MVP / Engineering prototype; security hardening complete |
| **Platform** | Android (minSdk 27 / targetSdk 35) |
| **Package** | `com.protctus.app` |

**Recommended poster focus:** NeuroShield within Protctus. This gives a single, technically defensible arc: problem → ML pipeline → measured results → privacy design → future work.

---

## 2. Problem Statement

India has the world's largest UPI-based digital payment ecosystem. Millions of first-time smartphone users encounter phishing URLs daily via WhatsApp, SMS, and social media. Existing mobile security tools rely on cloud-based signature lookups, exposing user browsing data to servers and failing in low-connectivity or zero-day scenarios. No affordable, privacy-preserving, offline-first phishing detection exists for Indian Android users.

---

## 3. Objectives

1. Detect phishing domains on-device with no URL upload to any server by default.
2. Operate fully offline with sub-5 ms per-domain latency.
3. Block real-time DNS-level phishing via a local VPN sinkhole.
4. Scan installed applications for malware using multiple independent engines.
5. Educate users through gamified cybersecurity microlearning.

---

## 4. System Architecture ✅

```
User URL Input
      ↓
[Layer 0] VVIPAllowlist — 380K Tranco trusted domains (Room DB + LRU cache)
      ↓ (not in allowlist)
[Layer 1] User Whitelist — user-added safe domains
      ↓
[Layer 2] Raw IP Guard — direct-IP domains flagged at max risk
      ↓
[Layer 3] Dynamic Threat Analysis (~0.3 ms)
          BK-Tree brand proximity · Markov DGA detection
          Viterbi word segmentation · Structural anomaly scoring
      ↓
[Layer 4] LightGBM text model (53 lexical features, 3.3 MB)
[Layer 5] ONNX LogisticRegression (53 features, 218 KB)*
      ↓
[Layer 6] EnsembleVerdictEngine
          LightGBM 50% · ONNX 30% · Heuristics 20%
          Fused score ≥ 0.65 → PHISHING (block)
          Fused score ≥ 0.30 → SUSPICIOUS (warn)
          Otherwise → SAFE
      ↓
[Room DB] ScanHistoryEntity persisted locally
[Notification] Phishing alert if VPN path blocked
```

*ONNX model asset currently invalid (1,068-byte stub) — engine degrades gracefully to LightGBM + heuristics.

**Real-time DNS path:** PhishingVpnService → native vpn-core.so (epoll TUN loop) → Layer 0–6 → DNS sinkhole or allow.

---

## 5. Technology Stack ✅

| Category | Technology |
|----------|-----------|
| Language | Kotlin (Android), C++ (NDK), Python (ML), TypeScript (Workers) |
| UI | Jetpack Compose + Material3, MVVM + Hilt DI |
| ML Runtime | ONNX Runtime Android 1.17.0 + LightGBM (text model) + TFLite (legacy) |
| Database | Room (SQLite) + DataStore + EncryptedSharedPreferences |
| Networking | OkHttp3 + Retrofit2 (disclosed remote features only) |
| Backend | Firebase Auth/Firestore/FCM · Cloudflare Workers/D1 |
| Native | CMake/NDK (vpn-core DNS loop, YARA libyara, native_scanner) |
| Payments | Razorpay SDK |
| Admin | Vite.js + Three.js (WebGL threat map) |

---

## 6. Complete Feature Set (Evidence-Graded)

### 6.1 Phishing Detection (NeuroShield)

| Feature | Grade | Notes |
|---------|-------|-------|
| 53-feature lexical domain analysis | ✅ VERIFIED | `LexicalFeatureExtractor` — entropy, DGA score, subdomain depth, punycode, etc. |
| LightGBM phishing model (3.3 MB) | ⚠️ IMPL | Asset present; runtime unverified (no device) |
| ONNX phishing model (218 KB) | ⚠️ IMPL (asset invalid) | Valid model in `ml/training/`; wrong file bundled in APK |
| Ensemble score fusion (LightGBM + ONNX + heuristics) | ⚠️ IMPL | `EnsemblePhishingEngine` — 50/30/20 weighted fusion |
| BK-Tree brand proximity (top-5000 brands, ≤2 edits) | ⚠️ IMPL | `BKTree.kt` |
| Markov chain DGA detection | ⚠️ IMPL | `MarkovChain.kt` |
| Viterbi word segmentation (risk-word detection) | ⚠️ IMPL | `ViterbiSegmenter.kt` |
| 380K-domain VVIPAllowlist (Room DB + LRU cache) | ⚠️ IMPL | `VVIPAllowlist.kt` |
| Privacy: no URL upload to cloud by default | ✅ VERIFIED | Cloud call removed; confirmed in `UrlScannerScreen.kt` |

### 6.2 Real-Time DNS Phishing Shield

| Feature | Grade | Notes |
|---------|-------|-------|
| VpnService local DNS sinkhole | ⚠️ IMPL | `PhishingVpnService.kt` + `edge_phishing_pipeline.cpp` (epoll TUN loop) |
| Per-domain block/warn notifications with Mark Safe / Keep Blocked actions | ⚠️ IMPL | `NotificationActionReceiver.kt` |
| User domain whitelist (persistent) | ⚠️ IMPL | `UserWhitelistManager.kt` |
| Threat persisted to Room DB on block | ⚠️ IMPL | `ThreatAlert` entity |
| Private DNS (DoT/DoH) detection + warning | ⚠️ IMPL | Declared in `THREAT_MODEL.md` |
| Anti-spam: max 10 notifications/day | ⚠️ IMPL | Counter in `PhishingVpnService` |

### 6.3 App Malware Scanner (Multi-Engine Consensus)

| Feature | Grade | Notes |
|---------|-------|-------|
| Quark behavioral engine (5-stage crime model) | ⚠️ IMPL | Kotlin-native; 55 rule files in assets |

---

## 7. ML Evaluation Results 🔬

**Source:** `ml/evaluation/results/full_20261027_seed42.json` — 2026-10-27, Python 3.14, onnxruntime. Genuine held-out data. **Not fabricated.**

Dataset: 380K Tranco benign + 392K Phishing-Database phishing. Split 60/20/20, seed 42.

| Metric | Value | 95% CI | Target | Met? |
|--------|-------|--------|--------|------|
| Accuracy | **0.8120** | [0.808, 0.816] | ≥ 0.95 | ❌ |
| Recall (phishing) | **0.7882** | [0.783, 0.793] | ≥ 0.95 | ❌ |
| Precision | 0.8276 | [0.823, 0.833] | — | — |
| F1 | 0.8073 | [0.803, 0.811] | — | — |
| FPR | 0.1642 | [0.160, 0.169] | — | — |
| Latency | ~2.8 ms/domain | CPU batch | < 5 ms | ✅ |

Confusion matrix (47,996 test): TN=20,059 · FP=3,941 · FN=5,082 · TP=18,914

Comparison on same 53 features, same test set:

| Model | Accuracy | Recall | F1 | FPR |
|-------|----------|--------|----|-----|
| NeuroShield ONNX | 0.8120 | 0.7882 | 0.8073 | 0.1642 |
| Logistic Regression | 0.8782 | 0.8316 | 0.8722 | 0.0752 |
| Random Forest | 0.8905 | 0.8474 | 0.8856 | 0.0664 |
| HistGradientBoosting | **0.8933** | 0.8421 | **0.8876** | **0.0555** |

None reach 0.95/0.95. Root cause: 8 dynamic features (brand-in-SLD, TLD phishing rate, keyword presence) zeroed — corpus not yet integrated.

---

## 9. Limitations (Honest Disclosure)

| Limitation | Impact |
|-----------|--------|
| ML accuracy 0.812 / recall 0.788 — targets NOT met | ~21% of phishing domains missed |
| ONNX asset invalid (1,068-byte stub in APK) | ONNX path disabled; falls back to LightGBM |
| `malware_signatures.bin` missing | Bloom-filter hash detection disabled |
| APK not buildable in dev environment | All Android UI/VPN/scanner features unverified |
| VPN DNS hardcoded to 8.8.8.8 | User DNS queries visible to Google |
| Anti-repackaging placeholder hash | Not enforced in current builds |
| No TLS inspection, domain-age lookup, or redirect monitoring | Not implemented; not claimed |

---

## 10. Future Scope 📋

1. Integrate 8 dynamic corpus features → approach 0.95/0.95 ML targets
2. Copy valid 223 KB ONNX model from `ml/training/` to APK assets
3. Generate and bundle `malware_signatures.bin`
4. User-configurable upstream DNS (DoT/DoH) in VPN
5. Integrate Chakravyuh RASP layer (`RealChakravyuhCore.kt` exists; not yet wired)
6. Build and device-test APK; verify all UI/VPN/malware features
7. Play Store submission with permission justifications
8. Expand training data with time-separated and regional Indian phishing domains

---

## 11. Team

| Role | Name |
|------|------|
| Founder | Jatin Sharma |
| Co-Founder | Swapnil Chaurasiya |
| Co-Founder | Vinayak Chourasiya |
| Co-Founder | Kunal Dhakad |
| Institution | Government Polytechnic College, Shivpuri |
| Programme | Diploma in Computer Science & Engineering |
| Contact | founders@protctus.com · +91 9074274321 |

---

## 12. Poster-Ready Copy

**Recommended Title:**
Protctus: A Privacy-First, Offline-First Android Cybersecurity Platform with On-Device Phishing Detection and Multi-Engine Malware Scanning

**Abstract:**
Protctus is an Android cybersecurity platform for Indian users that detects phishing domains, scans apps for malware, and blocks DNS-level threats entirely on-device without uploading user data. The NeuroShield engine uses a LightGBM model, an ONNX logistic regression model, and six heuristic engines (BK-Tree, Markov DGA, Viterbi segmentation) operating on 53 lexical features per domain at ~2.8 ms/domain. A local VpnService sinkhole blocks phishing DNS queries in real time via a native C++ epoll loop. A four-engine malware consensus scanner (Quark behavioral analysis, ClamAV signatures, YARA rules, Androguard APK inspection) protects against malicious apps. Gamified cybersecurity microlearning in seven Indian languages encourages security awareness for first-time smartphone users. Offline ML evaluation on ~240K held-out domains yields F1=0.807, accuracy=0.812. Accuracy and recall targets of 0.95 are not yet met and are disclosed honestly.

**Key Contributions:**
- On-device phishing detection with no default cloud URL upload (privacy-first)
- Multi-model ensemble (LightGBM + ONNX + 6 heuristic engines) with graceful degradation
- Real-time DNS sinkhole via Android VpnService + native C++ epoll loop
- Four-engine malware consensus scanner (Quark / ClamAV / YARA / Androguard)
- 53-feature lexical extractor + 7 dynamic corpus-derived features
- Reproducible evaluation harness with bootstrap 95% confidence intervals
- Gamified microlearning in 7 Indian languages for first-time smartphone users

---

## 13. Claims That MUST NOT Appear on the Poster

| Claim | Reason |
|-------|--------|
| "95% accuracy" or "95% recall" | Measured: 81.2% / 78.8% — targets not met |
| "Fully working / production-ready app" | Android UI unverified; APK not built |
| "ONNX model working in APK" | Asset is an invalid stub; path uses LightGBM fallback |
| "Blocks all phishing" | FNR = 21.2% — ~1 in 5 phishing domains missed |
| TLS inspection or certificate transparency | Not implemented; never claimed in code |
| Domain-age lookup or redirect monitoring | Not implemented |

---

## 14. Items to Confirm Before Publishing

- [ ] Faculty guide name and designation
- [ ] Full team names, roll numbers, and academic year
- [ ] Whether poster represents Govt. Polytechnic Shivpuri or MITS Gwalior affiliation
- [ ] Poster dimensions and submission format required by MITS Gwalior
- [ ] Whether a physical APK demo device will be available at presentation
- [ ] Whether the valid ONNX model (223 KB) will be deployed to APK before presentation


---

## 8. Privacy Design ✅

- No URL leaves device by default — confirmed in `UrlScannerScreen.kt` (cloud call removed)
- VPN intercepts DNS only; all other traffic flows natively
- Threat telemetry: salted SHA-256 hash of domain + TLD only — not raw domain
- Scan history in Room (app-private); not uploaded
- `android:allowBackup="false"` confirmed in manifest
- PDF reports to app-specific storage — no broad storage permission needed
- **Known gap:** VPN upstream DNS hardcoded to 8.8.8.8 (Google sees queries)

| ClamAV signature matcher (hash + Boyer-Moore pattern) | ⚠️ IMPL | `ClamSignatureEngine`; `.hdb`/`.ndb` assets bundled |
| YARA rule engine (NDK libyara) | ⚠️ IMPL | `YaraScanner.kt` + compiled `yara-core-module` |
| Androguard APK inspector (manifest/dex/certs) | ⚠️ IMPL | `AndroguardEngine` submodules |
| ThreatCorrelationEngine consensus verdict | ⚠️ IMPL | Aggregates all engine verdicts + origin context |
| Bloom filter signature DB (64 KB, MurmurHash3) | ⚠️ IMPL (DB missing) | `SignatureDatabase`; `malware_signatures.bin` not bundled |
| DeepScannerService (foreground, Premium-gated) | ⚠️ IMPL | `DeepScannerService.kt` |
| Stalkerware detection | ⚠️ IMPL | `StalkerwareEngine.kt` |
| USB/side-loaded APK threat detection | ⚠️ IMPL | `UsbThreatEngine.kt` + `ApkDownloadObserver` |

### 6.4 User Platform & Education

| Feature | Grade | Notes |
|---------|-------|-------|
| Firebase Auth (email/password, OTP, biometric) | ⚠️ IMPL | `.env` keys are placeholders |
| Dark web identity scan + local PDF report | ⚠️ IMPL | `DarkWebViewModel` + `PdfReportGenerator` (app-specific storage, no broad permission) |
| Gamified microlearning (XP, quests, chapters) | ⚠️ IMPL | `GamificationEngine`, `GlobalQuestTracker`, education screens |
| Leaderboard & rewards | ⚠️ IMPL | `LeaderboardViewModel`, `RewardsScreen` |
| 7-language localization (en/hi/bn/kn/or/ta/te) | ⚠️ IMPL | `res/values-*/strings.xml` present |
| Razorpay premium subscription (Free/Premium/Elite) | ⚠️ IMPL | `PremiumViewModel`; test-key placeholders |
| Periodic WorkManager scans + notifications | ⚠️ IMPL | `PeriodicScanWorker`, `DailyNotificationWorker` |
| Real-time threat map (WebGL/Three.js, admin) | ⚠️ IMPL | Bundled as `cybermap/index.html` + JS in assets |

### 6.5 Security Controls

| Feature | Grade | Notes |
|---------|-------|-------|
| `android:allowBackup="false"` | ✅ VERIFIED | AndroidManifest confirmed |
| Cleartext HTTP blocked (system CAs only) | ✅ VERIFIED | `network_security_config.xml` confirmed |
| Secrets in `.env` (gitignored; `REPLACE_ME` placeholders) | ✅ VERIFIED | Confirmed |
| Anti-repackaging (`SignatureVerifier`) | ⚠️ IMPL | Placeholder hash — not enforced (DEF-004) |
| `MANAGE_EXTERNAL_STORAGE` and `BATTERY_STATS` removed | ✅ VERIFIED | Manifest confirmed |
| Chakravyuh RASP (root/debugger/emulator detection) | 📋 FUTURE | `RealChakravyuhCore.kt` exists as standalone; not integrated into app |



