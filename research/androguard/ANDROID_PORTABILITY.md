# Androguard — Android Portability Analysis

## Executive Summary

Androguard is a **pure Python** framework with minimal native dependencies. This makes it one of the most portable malware analysis tools for Android — but running CPython on Android introduces its own overhead. The recommended strategy for Protctus is to **extract core parsing logic and rewrite critical paths in Kotlin/C++** rather than embedding the full Python runtime.

---

## Portability Matrix

### Execution Modes

| Mode | Feasibility | Complexity | Notes |
|------|-------------|------------|-------|
| **Direct on Android** | ⚠️ Partial | HIGH | Requires embedded Python (Chaquopy/Kivy). Heavy runtime overhead. |
| **Through JNI/NDK** | ❌ Not Applicable | N/A | Androguard is Python, not C/C++. No native library to wrap. |
| **Embedded Python** | ✅ Feasible | MEDIUM-HIGH | Chaquopy or BeeWare for Python-on-Android. ~30MB+ runtime overhead. |
| **API/Server-side** | ✅ Best Option | LOW | Run Androguard on server, send results via API. Zero mobile overhead. |
| **Rewritten Kotlin/C++** | ✅ Best for Mobile | HIGH | Rewrite core parsers natively. Best performance. Most effort. |

---

## Module-by-Module Portability Assessment

### 1. APK Parser (`core/bytecodes/apk/`)

| Metric | Value |
|--------|-------|
| **Portability Mode** | Kotlin rewrite (RECOMMENDED) or Embedded Python |
| **Difficulty** | ★★☆☆☆ LOW — ZIP parsing is well-understood |
| **Runtime Overhead** | ~5-20ms per APK on mid-range device |
| **Memory Usage** | 2-10 MB depending on APK size |
| **Battery Impact** | Negligible per scan |
| **Reusability** | ★★★★★ — Standalone, no heavy dependencies |

**Notes**: Android already has `ZipFile` and XML parsing APIs. APK parsing can be done entirely with Android SDK APIs. Rewriting this in Kotlin is straightforward and is the recommended path.

### 2. AXML Decoder (`core/bytecodes/axml/`)

| Metric | Value |
|--------|-------|
| **Portability Mode** | Kotlin rewrite (RECOMMENDED) |
| **Difficulty** | ★★★☆☆ MEDIUM — Binary XML format is non-trivial |
| **Runtime Overhead** | ~2-5ms per manifest |
| **Memory Usage** | <1 MB |
| **Battery Impact** | Negligible |
| **Reusability** | ★★★★★ — Critical for manifest analysis |

**Notes**: Android Binary XML has a specific format. Libraries like `apkparser` already exist in Java/Kotlin. Protctus should use or adapt an existing Kotlin implementation rather than porting the Python code.

### 3. DEX Parser (`core/bytecodes/dvm.py`)

| Metric | Value |
|--------|-------|
| **Portability Mode** | Kotlin/C++ rewrite (RECOMMENDED) or Server-side |
| **Difficulty** | ★★★★☆ HIGH — Complex binary format, instruction set |
| **Runtime Overhead** | 50-500ms depending on DEX complexity |
| **Memory Usage** | 10-100 MB for large multi-DEX apps |
| **Battery Impact** | MODERATE — CPU-intensive parsing |
| **Reusability** | ★★★★★ — Foundation for all bytecode analysis |

**Notes**: This is the most complex module. The DEX format specification is well-documented by Google. Consider using `dexlib2` (from smali project, Java) as a Kotlin-compatible alternative rather than porting the Python implementation.

### 4. Analysis Engine (`core/analysis/`)

| Metric | Value |
|--------|-------|
| **Portability Mode** | Server-side (RECOMMENDED) or Kotlin rewrite |
| **Difficulty** | ★★★★★ VERY HIGH — Cross-referencing, graph algorithms |
| **Runtime Overhead** | 500ms-5s for full XREF build |
| **Memory Usage** | 50-500 MB for large apps |
| **Battery Impact** | HIGH — O(n²) cross-referencing |
| **Reusability** | ★★★★☆ — Essential but expensive |

**Notes**: Full XREF analysis is too expensive for on-device use on mid-range devices. Recommend server-side execution or a stripped-down version that only builds targeted XREFs for specific APIs of interest.

### 5. DAD Decompiler (`decompiler/dad/`)

| Metric | Value |
|--------|-------|
| **Portability Mode** | Server-side ONLY |
| **Difficulty** | ★★★★★ VERY HIGH |
| **Runtime Overhead** | 1-30s per method |
| **Memory Usage** | 20-200 MB |
| **Battery Impact** | VERY HIGH |
| **Reusability** | ★★☆☆☆ — Not needed for malware detection |

**Notes**: Decompilation is NOT needed for Protctus malware scanning. It's useful for research/debugging but should never run on-device. Skip entirely for mobile integration.

### 6. Resource Parser (`core/resources/`)

| Metric | Value |
|--------|-------|
| **Portability Mode** | Kotlin rewrite or skip |
| **Difficulty** | ★★★☆☆ MEDIUM |
| **Runtime Overhead** | 10-50ms |
| **Memory Usage** | 5-20 MB |
| **Battery Impact** | LOW |
| **Reusability** | ★★★☆☆ — Useful for UI-based analysis |

**Notes**: Resource analysis is secondary for malware detection. Can be deferred or done server-side.

### 7. Session Manager (`session.py`)

| Metric | Value |
|--------|-------|
| **Portability Mode** | Replace with Android Room/SQLite |
| **Difficulty** | ★☆☆☆☆ TRIVIAL |
| **Runtime Overhead** | Negligible |
| **Memory Usage** | Negligible |
| **Battery Impact** | Negligible |
| **Reusability** | ★★☆☆☆ — Different persistence model needed |

---

## Recommended Hybrid Architecture for Protctus

```
┌─────────────────────────────────────────────────────────┐
│                    ON-DEVICE (Kotlin/C++)                │
├─────────────────────────────────────────────────────────┤
│  APK Parser (ZIP) ──→ Manifest Decoder (AXML)           │
│       │                    │                            │
│       ▼                    ▼                            │
│  Permission Extraction    Component Extraction          │
│       │                    │                            │
│       ▼                    ▼                            │
│  Quick Risk Scoring ◄──── Suspicious Pattern Check      │
│       │                                                 │
│       ▼                                                 │
│  [DECISION] Low Risk → Allow / High Risk → Deep Scan    │
└─────────────────────────────┬───────────────────────────┘
                              │ (if deep scan needed)
                              ▼
┌─────────────────────────────────────────────────────────┐
│                    SERVER-SIDE (Python)                  │
├─────────────────────────────────────────────────────────┤
│  Full DEX Parsing ──→ XREF Analysis ──→ Call Graph      │
│       │                    │                            │
│       ▼                    ▼                            │
│  API Behavior Scoring     Decompilation (if needed)     │
│       │                                                 │
│       ▼                                                 │
│  Detailed Threat Report ──→ Push to Device              │
└─────────────────────────────────────────────────────────┘
```

## Key Portability Risks

1. **`lxml` dependency** — Requires C compilation. Replace with Android's built-in XML parser.
2. **`networkx` dependency** — Pure Python graph library. Heavy for large call graphs. Replace with lightweight graph algorithms.
3. **`frida` dependency** — OPTIONAL but impossible on non-rooted Android. Skip entirely.
4. **Memory spikes during XREF** — Can exceed 500MB for large APKs. Not viable on 3GB RAM devices.
5. **Python runtime overhead** — ~30MB+ just for the interpreter. Consider Kotlin rewrite for critical paths.
