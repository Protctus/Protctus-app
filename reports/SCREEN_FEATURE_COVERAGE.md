# SCREEN AND FEATURE COVERAGE MATRIX
**Project:** Protctus | **Date:** 2026-10-30

> All manual scenarios are BLOCKED — no Android SDK, no device, no emulator available. Statuses reflect static source inspection only.

---

## Screen Coverage

| ID | Screen | Source File | Status |
|----|--------|------------|--------|
| SCR-001 | Sign In | `SignInScreen.kt` | NOT VERIFIED |
| SCR-002 | Sign Up | `SignUpScreen.kt` | NOT VERIFIED |
| SCR-003 | OTP Verification | `OtpVerificationScreen.kt` | NOT VERIFIED |
| SCR-004 | Set Password | `SetPasswordScreen.kt` | NOT VERIFIED |
| SCR-005 | Password Reset | `PasswordResetScreen.kt` | NOT VERIFIED |
| SCR-006 | Home / Dashboard | `HomeScreen.kt` | NOT VERIFIED |
| SCR-007 | URL Scanner | `UrlScannerScreen.kt` | NOT VERIFIED |
| SCR-008 | Deep Scan | `DeepScanScreen.kt` | NOT VERIFIED |
| SCR-009 | Smart Scan | `SmartScanScreen.kt`* | NOT VERIFIED |
| SCR-010 | Dark Web Scan | `DarkWebScanScreen.kt`* | NOT VERIFIED |
| SCR-011 | Settings | `SettingsScreen.kt` | NOT VERIFIED |
| SCR-012 | Education / Microlearning | `ui.screens.education/` | NOT VERIFIED |
| SCR-013 | Leaderboard | leaderboard screen | NOT VERIFIED |
| SCR-014 | Rewards | `RewardsScreen.kt` | NOT VERIFIED |
| SCR-015 | Threat Blocked | `ThreatBlockedActivity.kt` | NOT VERIFIED |
| SCR-016 | APK Quarantine | `ApkQuarantineActivity.kt` | NOT VERIFIED |
| SCR-017 | Notifications | `ui.screens.notifications/` | NOT VERIFIED |
| SCR-018 | Referral | `ui.screens.referral/` | NOT VERIFIED |

*SmartScanScreen.kt not found at `ui/screens/features/SmartScanScreen.kt`; alternate path suspected.



---

## Feature Coverage

| ID | Feature | Component | Status | Defects |
|----|---------|-----------|--------|---------|
| FEAT-001 | On-device URL phishing scan | `PhishingDetector` + `EnsemblePhishingEngine` | PARTIALLY WORKING | DEF-001 fixed, DEF-002 |
| FEAT-002 | NeuroShield ONNX inference | `NeuroShieldEngine` | BROKEN (invalid asset); graceful degradation | DEF-002 |
| FEAT-003 | LightGBM text model inference | `LightGBMModel` (3.3 MB asset) | UNVERIFIED | None identified |
| FEAT-004 | 53-feature lexical extraction | `LexicalFeatureExtractor` | PARTIALLY WORKING (code reviewed) | Features 46-52 zeroed in base extractor |
| FEAT-005 | Dynamic features 46-52 | `DynamicFeatureProvider` | UNVERIFIED | None |
| FEAT-006 | Score fusion verdict engine | `VerdictEngine` / `EnsemblePhishingEngine` | UNVERIFIED | None |
| FEAT-007 | DNS phishing VPN | `PhishingVpnService` + `vpn-core` | UNVERIFIED | TM-04 (hardcoded 8.8.8.8) |
| FEAT-008 | Bloom filter malware matching | `SignatureDatabase` | PARTIALLY WORKING (code OK; DB missing) | DEF-003 |
| FEAT-009 | VVIPAllowlist (380K domains) | `VVIPAllowlist` | UNVERIFIED | None |
| FEAT-010 | Quark engine (APK behavioral) | `QuarkEngine` | UNVERIFIED | None |
| FEAT-011 | ClamAV signature matching | `ClamSignatureEngine` | UNVERIFIED | None |
| FEAT-012 | YARA scanning | `YaraScanner` (NDK) | UNVERIFIED | None |
| FEAT-013 | Androguard APK inspection | `AndroguardEngine` | UNVERIFIED | None |
| FEAT-014 | Firebase Auth | `AuthViewModel` | UNVERIFIED | Placeholder .env keys |
| FEAT-015 | Gamification / XP / Quests | `GamificationEngine` | UNVERIFIED | None |
| FEAT-016 | Dark web scan + PDF | `DarkWebViewModel` + `PdfReportGenerator` | UNVERIFIED | None |
| FEAT-017 | Razorpay premium payments | `PremiumViewModel` | UNVERIFIED | Test-key placeholders |
| FEAT-018 | Anti-repackaging | `SignatureVerifier` | PARTIALLY WORKING (placeholder hash) | DEF-004 |
| FEAT-019 | Multi-language (7 locales) | `res/values-*/strings.xml` | UNVERIFIED | None |
| FEAT-020 | WorkManager periodic scans | `PeriodicScanWorker` | UNVERIFIED | None |

---

## ML Evaluation (Python offline — EXECUTED 2026-10-27)

| Model | Test Size | Accuracy | Recall | F1 | Status |
|-------|-----------|----------|--------|----|--------|
| NeuroShield ONNX (ml/training/) | 47,996 | 0.8120 | 0.7882 | 0.8073 | ✅ EVALUATED |
| NeuroShield ONNX (app/assets/) | — | N/A | N/A | — | ⚠️ INVALID ASSET |
| TFLite char-map model | — | — | — | — | BLOCKED |
| LightGBM text model (runtime) | — | — | — | — | BLOCKED |

---

## Coverage Totals

| Metric | Count |
|--------|-------|
| Screens discovered | 18 |
| Features discovered | 20 |
| Manual scenarios attempted | 0 (all BLOCKED — no device) |
| PASS | 0 |
| FAIL | 0 |
| BLOCKED | All Android UI scenarios |
| NOT VERIFIED | 18 screens, 16 features |
| ML scenarios executed (Python) | 1 |
| Confirmed defects | 6 (DEF-001 through DEF-006) |
| Defects fixed | 1 (DEF-001) |
| Defects unresolved | 5 |
