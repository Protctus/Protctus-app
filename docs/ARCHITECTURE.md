# Protctus Architecture Overview

## High-level diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI (MVVM + Hilt)         │
│  UrlScanner · SmartScan · DeepScan · Settings · Education   │
└──────────────────┬──────────────────────────────────────────┘
                   │ ViewModels / UseCases
┌──────────────────▼──────────────────────────────────────────┐
│  On-Device Security Core                                    │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐     │
│  │ NeuroShield │ │ Phishing     │ │ ThreatCorrelation│     │
│  │ (ONNX LGBM) │ │ Detector     │ │ Engine           │     │
│  └──────┬──────┘ └──────┬───────┘ └──────┬───────────┘     │
│         │               │                │                  │
│  Lexical Feature      │ Heuristics +    │ Quark · ClamAV   │
│  Extractor (53 feats) │ ONNX fusion     │ YARA · Androguard│
│                       │ Verdict Engine  │ Bloom Filter     │
└──────────────────┬────┴─────────────────┴───────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│  Storage / Persistence                                      │
│  Room (SQLite) · DataStore (preferences) · EncryptedShared  │
│  Entities: ScanHistory, ThreatAlert, UserSafeList, …        │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│  Network / Services                                         │
│  PhishingVpnService (VpnService DNS sinkhole, split-tunnel)│
│  SentinelService, DeepScannerService (foreground special-use)│
│  OkHttp + Retrofit (only for disclosed remote features)     │
└─────────────────────────────────────────────────────────────┘
```

## Module boundaries (app module)

| Package | Responsibility |
|---------|----------------|
| `com.protctus.app.ui` | Compose screens, navigation graph, theming |
| `com.protctus.app.viewmodel` | Hilt ViewModels, UI state |
| `com.protctus.app.neuroshield.engine` | ONNX inference, lexical feature extraction, verdict fusion |
| `com.protctus.edge.phishing` | PhishingDetector (ONNX + heuristics), PhishingVpnService, BK-Tree/Markov/Viterbi |
| `com.protctus.app.engine` | URL scanner engine, signature DB (Bloom), correlation, file scanning |
| `com.protctus.app.engine.correlation` | ThreatCorrelationEngine (Quark/Clam/YARA/Androguard consensus) |
| `com.protctus.app.engine.quark`, `.clam`, `.androguard` | Individual malware engines |
| `com.protctus.app.data.local` | Room DB, DAOs, entities, DataStore settings |
| `com.protctus.app.repository` | Repositories (NeuroShieldRepository, PhishingDomainEvaluator, …) |
| `com.protctus.app.di` | Hilt modules |
| `com.protctus.app.network` | Retrofit/OkHttp clients (remote features) |
| `com.protctus.app.security` | SignatureVerifier, TOTP, OTPSecurity |
| `com.protctus.app.services` | VPN, Sentinel, file observer, messaging |
| `com.protctus.app.receiver` | Broadcast receivers (boot, phishing alert, VPN action) |
| `com.protctus.app.worker` | WorkManager periodic scan / notifications |

## Native (C++ / NNDK)

Built in-app via CMake (`app/src/main/cpp/CMakeLists.txt`):

| Library | Source | Purpose |
|---------|--------|---------|
| `vpn-core` | `edge_phishing_pipeline.cpp` | Native DNS interception loop (epoll over TUN), upstream DNS forwarding, block/allow DNS response synthesis |
| `chakravyuh` | `chakravyuh.cpp` | RASP self-protection (planned roadmap) |
| `native_scanner` | `native_scanner.cpp` | Native file scanner helper |

## Data flow — URL scan (on-device)

1. User enters URL in `UrlScannerScreen`.
2. Input validated (non-empty, URL-like host).
3. `PhishingDetector.getVerdict(url)`:
   - L0 user whitelist → SAFE
   - L1 Tranco BloomFilter allowlist → SAFE
   - L2 raw-IP guard → max risk
   - L3 dynamic threat analysis (BK-Tree, Markov, Viterbi, structural) → heuristic score (capped 0.80)
   - L4 NeuroShield ONNX inference (53 lexical features) → phishing probability
   - L5 VerdictEngine fuses ONNX (70%) + heuristic (30%) → verdict
4. UI shows SAFE / SUSPICIOUS / MALICIOUS with triggered indicators and recommended action.
5. Result persisted to Room `ScanHistoryEntity`.

No URL leaves the device. The former cloud RBI/Llama3 call has been removed from the UI scanner.

## Data flow — real-time DNS protection

1. User enables Phishing Shield VPN → `VpnService.prepare()` consent → `PhishingVpnService`.
2. Native `vpn-core` loop reads DNS queries from the TUN interface.
3. Each queried domain checked via `jniIsMalicious` → `PhishingDetector.analyzeDomainRiskDnsOnly`.
4. Score ≥ block threshold → synthetic DNS refusal (0x8183) written back to TUN; notification shown; threat persisted to Room.
5. Score in suspicious range → allow + notify.
6. Essential infrastructure (Google, Apple, Cloudflare, etc.) and user allowlist bypass detection.

## Privacy & trust boundaries

- Detection and inference run on-device. Models/assets bundled in the APK.
- No default remote phishing lookups; the cloud RBI worker call was removed from the UI scanner.
- Crashlytics/Firebase are present but not used to exfiltrate URLs by default.
- VPN split-tunnel: only DNS (port 53/853) is intercepted; other traffic flows natively.
