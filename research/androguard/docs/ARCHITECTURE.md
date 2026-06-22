# Androguard — Architecture Analysis

## Overview

Androguard is a pure-Python reverse engineering framework for Android applications. It provides complete APK/DEX/AXML parsing, cross-reference analysis, decompilation, and dynamic analysis capabilities.

## Source Code Structure

```
androguard/
├── __init__.py              # Version info
├── misc.py                  # Convenience wrappers (AnalyzeAPK, AnalyzeDEX)
├── session.py               # Session management, persistence
├── util.py                  # Utility functions
├── core/
│   ├── analysis/            # Cross-reference & call-graph analysis
│   ├── androconf.py         # Configuration & environment detection
│   ├── api_specific_resources/  # AOSP permission mappings (JSON)
│   ├── apk/                 # APK parsing (__init__.py = 116KB!)
│   ├── axml/                # Android Binary XML decoder (__init__.py = 141KB)
│   ├── bytecode.py          # Bytecode utility functions (33KB)
│   ├── dex/                 # DEX/ODEX parser (__init__.py = 298KB!)
│   ├── mutf8/               # Modified UTF-8 encoding
│   └── resources/           # Android resource parser (public.xml)
├── cli/                     # Command-line interface
├── decompiler/              # DAD decompiler, Jadx integration
├── pentest/                 # Penetration testing utilities
└── ui/                      # Interactive UI (androlyze)
```

## Scanning Pipeline

```
APK File (bytes)
    │
    ▼
┌─────────────────────────────────────────┐
│ APK Parser (core/apk/__init__.py)       │
│ • ZIP extraction                        │
│ • Certificate extraction (RSA/DSA/EC)   │
│ • File enumeration                      │
│ • APK validation                        │
└──────┬──────────────────────────────────┘
       │
       ├──→ AndroidManifest.xml
       │         │
       │         ▼
       │    ┌─────────────────────────────┐
       │    │ AXML Decoder (core/axml/)   │
       │    │ • Binary XML → XML tree     │
       │    │ • String pool parsing       │
       │    │ • Resource ID resolution    │
       │    │ • Namespace handling        │
       │    └─────────────────────────────┘
       │         │
       │         ▼
       │    Permissions, Activities, Services,
       │    Receivers, Providers, Intent Filters
       │
       ├──→ classes.dex, classes2.dex, ...
       │         │
       │         ▼
       │    ┌─────────────────────────────┐
       │    │ DEX Parser (core/dex/)      │
       │    │ • Header parsing            │
       │    │ • String pool               │
       │    │ • Type/Proto/Field/Method   │
       │    │ • Class definitions         │
       │    │ • Bytecode instructions     │
       │    │ • Annotation parsing        │
       │    └─────────────────────────────┘
       │         │
       │         ▼
       │    ┌─────────────────────────────┐
       │    │ Analysis Engine             │
       │    │ • Cross-references (XREF)   │
       │    │ • Call graphs               │
       │    │ • Class hierarchy           │
       │    │ • API usage tracking        │
       │    │ • String analysis           │
       │    └─────────────────────────────┘
       │
       └──→ Resources, Certificates
```

## Key Components for Protctus

### 1. APK Parser (`core/apk/__init__.py` — 116KB)
**What it does:**
- Parses APK ZIP structure
- Extracts AndroidManifest.xml (binary)
- Extracts certificates (v1/v2/v3 signing)
- Lists files, detects multi-DEX
- Calculates file hashes (MD5, SHA1, SHA256)

**Reusable for Protctus:** YES — Core functionality. Can be reimplemented in Kotlin using `java.util.zip.ZipFile`.

### 2. AXML Decoder (`core/axml/__init__.py` — 141KB)
**What it does:**
- Parses Android Binary XML format
- Decodes string pools (UTF-8 and UTF-16)
- Resolves resource IDs
- Builds XML element tree
- Handles namespaces

**Key data structures:**
- `StringPool` — packed string table
- `ResourceChunk` — resource ID mapping
- `StartElement` / `EndElement` — XML tree nodes

**Reusable for Protctus:** YES — Critical for manifest analysis. Existing Kotlin/Java libraries exist (e.g., `apkparser`, `aapt2`).

### 3. DEX Parser (`core/dex/__init__.py` — 298KB, `dex_types.py` — 8KB)
**What it does:**
- Parses DEX file header (magic, checksum, SHA1)
- Reads string table, type table, proto table
- Parses class definitions
- Decodes Dalvik bytecode instructions
- Handles multi-DEX (classes2.dex, etc.)
- ODEX/VDEX support

**Key classes:**
- `DalvikVMFormat` — main DEX parser
- `ClassDefItem` — class definition
- `EncodedMethod` — method with bytecode
- `Instruction` hierarchy — all Dalvik opcodes

**Reusable for Protctus:** PARTIALLY — Full DEX parsing is very complex. For behavioral analysis, we only need method signatures and API call sites, not full bytecode traversal. Consider `dexlib2` (smali project, Java) as alternative.

### 4. Analysis Engine (`core/analysis/`)
**What it does:**
- Builds cross-references (who calls whom)
- Identifies external vs internal methods
- Tracks string usage
- Identifies Android API calls
- Builds class hierarchy

**Reusable for Protctus:** PARTIALLY — Full XREF is expensive (O(n²)). For Protctus, we only need targeted API usage tracking.

## Threading Model
- **Single-threaded** — Androguard is inherently single-threaded
- No built-in parallelism
- Session management is NOT thread-safe
- For Protctus: must wrap in coroutine dispatcher

## Memory Profile
- Small APK (~5MB): 50-100MB RAM
- Medium APK (~30MB): 100-300MB RAM
- Large APK (~100MB): 300-800MB+ RAM
- Most memory consumed during XREF analysis

## I/O-Heavy Sections
- APK ZIP extraction (disk I/O)
- DEX file reading (disk I/O for large multi-DEX)
- Resource file parsing (disk I/O)

## CPU-Heavy Sections
- DEX bytecode parsing (CPU)
- Cross-reference building (CPU, O(n²))
- Class hierarchy resolution (CPU)
- Decompilation (CPU, extremely heavy)

## Dependencies (Relevant for Android)
| Dependency | Portable? | Android Alternative |
|-----------|-----------|-------------------|
| `lxml` | ❌ C extension | Android's `XmlPullParser` |
| `asn1crypto` | ✅ Pure Python | Java's `java.security` |
| `cryptography` | ⚠️ Needs OpenSSL | Android's `java.security` |
| `networkx` | ✅ Pure Python | Custom lightweight graph |
| `mutf8` | ✅ Pure Python | Java natively handles MUTF-8 |
| `pydot` | ⚠️ Needs Graphviz | Not needed for scanning |
| `frida` | ❌ Root required | Not needed |
| `loguru` | ✅ Pure Python | Android Log.d/w/e |
| `apkInspector` | ✅ Pure Python | Can integrate |
