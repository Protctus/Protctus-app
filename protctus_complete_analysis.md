---
stylesheet: https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.2.0/github-markdown-dark.min.css
body_class: markdown-body
pdf_options:
  format: A4
  margin: 20mm
  printBackground: true
  headerTemplate: |-
    <style>
      section { margin: 0 auto; font-family: 'Inter', sans-serif; font-size: 10px; color: #7F909F; }
    </style>
    <section>Protctus Security Analysis Report</section>
  footerTemplate: |-
    <style>
      section { margin: 0 auto; font-family: 'Inter', sans-serif; font-size: 10px; color: #7F909F; }
    </style>
    <section>Page <span class="pageNumber"></span> of <span class="totalPages"></span></section>
css: |-
  @import url('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css');
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap');

  body {
    background-color: #1C1919 !important;
    color: #E3E3E3 !important;
    font-family: 'Inter', -apple-system, sans-serif !important;
    line-height: 1.6;
  }

  .markdown-body {
    background-color: #1C1919 !important;
    color: #E3E3E3 !important;
  }

  h1, h2, h3, h4 {
    color: #7F909F !important;
    border-bottom: 1px solid #2A2B31 !important;
    padding-bottom: 0.3em;
    margin-top: 1.5em;
  }

  h1 { font-size: 2.2em; text-align: center; margin-bottom: 1em; border-bottom: none; }

  table {
    border-collapse: collapse !important;
    width: 100% !important;
    margin: 1.5em 0 !important;
    background-color: #161b22 !important;
    border: 1px solid #30363d !important;
  }

  th {
    background-color: #21262d !important;
    color: #7F909F !important;
    padding: 12px !important;
    border: 1px solid #30363d !important;
  }

  td {
    padding: 10px !important;
    border: 1px solid #30363d !important;
  }

  blockquote {
    border-left: 4px solid #10B981 !important;
    background-color: #2A2B31 !important;
    color: #A0AEC0 !important;
    padding: 1em !important;
    margin: 1.5em 0 !important;
  }

  code {
    background-color: #2A2B31 !important;
    color: #EF4444 !important;
    padding: 0.2em 0.4em !important;
    border-radius: 4px !important;
  }

  .icon { margin-right: 12px; color: #10B981; font-size: 0.9em; }
  
  .mermaid {
    background: #161b22 !important;
    padding: 20px !important;
    border-radius: 8px !important;
    border: 1px solid #30363d !important;
    margin: 2em 0 !important;
    text-align: center !important;
  }
---

<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<script>
  mermaid.initialize({
    startOnLoad: true,
    theme: 'dark',
    themeVariables: {
      primaryColor: '#7F909F',
      primaryTextColor: '#E3E3E3',
      primaryBorderColor: '#2A2B31',
      lineColor: '#10B981',
      secondaryColor: '#2A2B31',
      tertiaryColor: '#161b22'
    }
  });
</script>

<div style="text-align: center; padding: 40px 0;">
  <i class="fas fa-shield-halved" style="font-size: 80px; color: #10B981;"></i>
</div>

# Protctus — Complete Application Analysis Report

> **Date:** April 4, 2026  
> **Codebase:** Android (Kotlin / Jetpack Compose), Cloudflare Workers (TypeScript), Firebase  
> **Architecture:** MVVM + Hilt DI + Room + DataStore + Coroutines

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Complete Feature Inventory](#2-complete-feature-inventory)
3. [Security Engine Deep-Dive](#3-security-engine-deep-dive)
4. [Phishing Detector — 5-Layer AI DNS Filter](#4-phishing-detector--5-layer-ai-dns-filter)
5. [Backend Infrastructure](#5-backend-infrastructure)
6. [Data Layer Architecture](#6-data-layer-architecture)
7. [UI/UX Architecture](#7-uiux-architecture)
8. [Gamification System](#8-gamification-system)
9. [Security Posture of the App Itself](#9-app-self-security)
10. [3-Tier Phishing Defense Architecture](#10-3-tier-phishing-defense-architecture)
11. [Device Requirements & System Specifications](#11-device-requirements--system-specifications)
12. [APK Build Audit & Size Breakdown](#12-apk-build-audit--size-breakdown)

---

## <i class="fas fa-network-wired icon"></i> 1. Architecture Overview

```mermaid
graph TD
    A["Android App<br/>(Kotlin + Compose)"] --> B["Firebase Auth"]
    A --> C["Firestore<br/>(protctus instance)"]
    A --> D["Cloudflare Workers"]
    A --> E["Room DB<br/>(Local)"]
    A --> F["DataStore<br/>(Preferences)"]
    D --> G["D1 Database<br/>(Quests, Lab, Tickets)"]
    A --> H["XposedOrNot API<br/>(Dark Web)"]
    A --> I["Render.com<br/>(Live Threat Feed)"]
    A --> J["RBI Worker<br/>(Phishing Scan)"]
    
    subgraph "Security Engines"
        K["ThreatBrain"]
        L["SignatureDatabase<br/>(Bloom Filter)"]
        M["StalkerwareEngine"]
        N["UsbThreatEngine"]
        O["SecurityScanner"]
        P["FileScanner"]
        Q["DeepScannerService"]
    end
    
    A --> K
    A --> L
    A --> M
    A --> N
    A --> O
    A --> P
    A --> Q
```

| Layer | Technology | Purpose |
|-------|-----------|---------|
| UI | Jetpack Compose + Material 3 | All screens |
| DI | Hilt | 16 ViewModels injected |
| State | StateFlow + MutableStateFlow | Reactive UI |
| Local DB | Room (v7, 8 entities) | Threats, Settings, VVIP, SafeList |
| Preferences | DataStore | 11 toggleable settings |
| Auth | Firebase Auth + Email OTP | Sign-up, Sign-in, 2FA, OTP Security |

---

## <i class="fas fa-list-check icon"></i> 2. Complete Feature Inventory

### SECURITY ENGINES (7 Real Engines)

| # | Feature | Status | How It Works |
|---|---------|--------|--------------|
| 1 | **ThreatBrain Heuristic Engine** | Real | Multi-factor behavioral analysis: permission combos, headless detection. |
| 2 | **Bloom Filter Signature DB** | Real | MurmurHash3 Bloom Filter with 64KB BitSet, 3 hash functions. |
| 3 | **Stalkerware Detection** | Real | 4-rule scoring: sideloading, accessibility/admin, hidden icon. |
| 4 | **USB Juice Jacking Guard** | Real | Real-time USB state monitoring. SOS Morse vibration. |
| 5 | **Deep Scanner Service** | Real | Foreground Service with WakeLock for recursive system scanning. |
| 6 | **File Scanner** | Real | Concurrent coroutine-batched scanner (8 files/chunk). |
| 7 | **Device Security Scanner** | Real | 12 real checks across 3 categories with scored findings. |

---

## <i class="fas fa-microchip icon"></i> 3. Security Engine Deep-Dive — "World's Fastest"

> [!IMPORTANT]
> The engine's speed comes from its **architectural design** — it never downloads definitions at runtime and uses probabilistic data structures.

#### Speed Factor: Bloom Filter (O(1) Lookup)
Protctus uses a **64KB Bloom Filter** with MurmurHash3.
- **3 hash probes per lookup** — each takes ~100ns.
- **Total lookup:** ~300ns per file vs. ~50ms for a linear DB scan.

---

## <i class="fas fa-fish-fins icon"></i> 4. Phishing Engine — 5-Layer AI DNS Filter

```mermaid
graph TD
    DNS["DNS Query"] --> VPN["Isolated Coroutine per packet"]
    VPN --> L0["Layer 0: RBI Blacklist<br/>~0μs HashSet"]
    L0 -->|"Malicious"| BLOCK["BLOCK"]
    L0 -->|"Unknown"| L1["Layer 1: Tranco Whitelist<br/>~1μs"]
    L1 -->|"Safe"| PASS["FORWARD"]
    L1 -->|"Unknown"| L2["Layer 2: IP Check"]
    L2 -->|"Raw IP"| BLOCK
    L2 -->|"Domain"| L3["Layer 3: Brand Impersonation"]
    L3 -->|"Match"| BLOCK
    L3 -->|"No Match"| L4["Layer 4: TFLite ML"]
    L4 -->|"Suspicious"| BLOCK
    L4 -->|"Safe"| L5["Layer 5: Llama 3 Deep RBI"]
```

---

## <i class="fas fa-server icon"></i> 5. Backend Infrastructure

| Worker | Purpose |
|--------|---------|
| **Quests API** | Admin panel, Quests, Lab, Ticket management. |
| **Leaderboard** | Xp tracking and global user ranking. |
| **RBI Phishing** | Deep browser-based phishing analysis. |
| **OTP Auth** | Secure Email OTP generation and verification. |

---

## <i class="fas fa-database icon"></i> 6. Data Layer Architecture

### Room Database (v7, 8 Entities)
| Entity | Use |
|--------|-----|
| `ThreatAlert` | All security findings and scan history. |
| `UserSafeList` | User-whitelisted domains. |
| `VvipDomain` | Tranco Top-380K trusted domains. |
| `OtpState` | Advanced rate limiting and cooldown logic. |

---

## <i class="fas fa-mobile-screen-button icon"></i> 7. UI/UX Architecture

The app contains **47+ screens** built with Jetpack Compose.

| Category | Screens |
|----------|---------|
| **Dashboard** | Home, RankDefend, Rewards, Live Threat Map. |
| **Scanner** | SmartScan, DeepScan, URL Scanner, Dark Web Scan. |
| **Lab** | Chapters, Topics, Microlearning, Quizzes. |

---

## <i class="fas fa-trophy icon"></i> 8. Gamification System

| Level | Rank | XP Required |
|-------|------|-------------|
| 1 | Cyber Novice | 0 |
| 4 | Virus Slayer | 600 |
| 8 | Cyber Knight | 4000 |

---

## <i class="fas fa-user-shield icon"></i> 9. App Self-Security

- **Certificate Pinning**: Cloudflare ECC CA-3 + DigiCert P384.
- **Hardware Keystore**: AES-256-GCM encryption.
- **Native Anti-Tamper**: Chakravyuh JNI module.

---

## <i class="fas fa-layer-group icon"></i> 10. 3-Tier Phishing Defense Architecture

```mermaid
graph TD
    URL["Incoming URL"] --> L1{"Tier 1:<br/>VVIP (380K)"}
    L1 -->|"Match"| SAFE["SAFE"]
    L1 -->|"No"| L2{"Tier 2:<br/>SafeList"}
    L2 -->|"Match"| SAFE
    L2 -->|"No"| L3{"Tier 3:<br/>TFLite ML"}
    L3 -->|"High"| BLOCK["BLOCK"]
    L3 -->|"Mid"| WARN["WARN"]
    L3 -->|"Low"| SAFE
```

---

## <i class="fas fa-android icon"></i> 11. Device Requirements

| Requirement | Specification |
|-------------|---------------|
| **OS** | Android 8.0+ (Oreo) |
| **RAM** | 3GB Minimum (4GB Recommended) |
| **Storage** | ~65MB Install / ~150MB Runtime |

---

## <i class="fas fa-file-invoice icon"></i> 12. APK Build Audit

```mermaid
pie title APK Size Breakdown
    "Compiled code (DEX)" : 8.2
    "Native Libs (JNI)" : 6.8
    "VVIP Dataset (380K)" : 5.6
    "ML Model / Assets" : 8.5
    "UI Resources" : 3.42
```
