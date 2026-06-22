# ClamAV — Architecture Analysis

## Overview

ClamAV is an industry-grade open-source antivirus engine written in C (with Rust components). It provides signature-based, heuristic, and bytecode-based malware detection. Maintained by Cisco Talos Intelligence Group.

## Source Code Structure

```
clamav/
├── CMakeLists.txt           # Main build (49KB — very complex)
├── CMakeOptions.cmake       # Build configuration options
├── Cargo.toml               # Rust workspace root
├── libclamav/               # ★ CORE ENGINE (298 .c/.h files)
│   ├── clamav.h             # Public API header (114KB)
│   ├── scanners.c           # ★ Magic scan dispatcher (232KB!)
│   ├── matcher.c            # ★ Pattern matching orchestrator (59KB)
│   ├── matcher-ac.c         # Aho-Corasick pattern matching (128KB)
│   ├── matcher-bm.c         # Boyer-Moore pattern matching (16KB)
│   ├── matcher-hash.c       # Hash-based matching (15KB)
│   ├── matcher-pcre.c       # PCRE regex matching (29KB)
│   ├── matcher-byte-comp.c  # Byte comparison matching (40KB)
│   ├── readdb.c             # ★ Signature database loader (219KB!)
│   ├── cache.c              # Scan result cache (27KB)
│   ├── fmap.c               # Memory-mapped file I/O (50KB)
│   ├── filtering.c          # Pre-filter for fast rejection (28KB)
│   ├── filetypes.c          # File type detection (24KB)
│   ├── unzip.c              # ZIP extraction (80KB)
│   ├── pe.c                 # PE executable parser (228KB)
│   ├── elf.c                # ELF parser (32KB)
│   ├── pdf.c                # PDF parser (179KB)
│   ├── htmlnorm.c           # HTML normalizer (93KB)
│   ├── crypto.c             # Cryptographic functions (49KB)
│   ├── bytecode.c           # Bytecode VM (133KB)
│   ├── bytecode_vm.c        # Bytecode VM interpreter (57KB)
│   ├── yara_*.c/h           # YARA rule support
│   ├── phishcheck.c         # Phishing detection (59KB)
│   └── [200+ more files]
├── libclamav_rust/          # Rust components
│   └── src/                 # Rust source for new features
├── libclammspack/           # CAB/MSPack decompression
├── libclamunrar/            # RAR decompression
├── libfreshclam/            # Signature update client
├── clamscan/                # CLI scanner
├── clamd/                   # Daemon scanner
├── sigtool/                 # Signature management
├── freshclam/               # DB updater
└── common/                  # Shared utilities
```

## Scanning Pipeline

```
Input File
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ cli_magic_scan() — scanners.c                        │
│ • File type detection (magic bytes)                  │
│ • Layer recursion management                         │
│ • Scan limit enforcement                             │
└──────┬──────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────┐
│ Format-Specific Parsers                              │
│ • cli_scanzip()  — ZIP/APK/JAR extraction           │
│ • cli_scanpe()   — PE executable analysis           │
│ • cli_scanelf()  — ELF analysis                     │
│ • cli_scanpdf()  — PDF parsing                      │
│ • cli_scanhtml() — HTML normalization               │
│ • cli_scanole2() — Office document analysis         │
│ • cli_scanmail() — Email parsing                    │
│ • [many more format handlers]                       │
└──────┬──────────────────────────────────────────────┘
       │ (extracted content)
       ▼
┌─────────────────────────────────────────────────────┐
│ cli_scan_fmap() — matcher.c                          │
│ • Target-type-specific matching                     │
│ • Signature database selection                      │
└──────┬──────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────┐
│ Pattern Matchers (run in parallel)                   │
│                                                     │
│ ┌───────────────┐  ┌──────────────┐                 │
│ │ Aho-Corasick  │  │ Boyer-Moore  │                 │
│ │ (matcher-ac)  │  │ (matcher-bm) │                 │
│ │ Multi-pattern │  │ Single-pat   │                 │
│ │ O(n+m)        │  │ O(n/m)       │                 │
│ └───────┬───────┘  └──────┬───────┘                 │
│         │                  │                        │
│ ┌───────┴───────┐  ┌──────┴───────┐                 │
│ │ Hash Matcher  │  │ PCRE Matcher │                 │
│ │ (MD5/SHA1/256)│  │ (regex)      │                 │
│ │ O(1) lookup   │  │ O(n×r)       │                 │
│ └───────┬───────┘  └──────┬───────┘                 │
│         │                  │                        │
│ ┌───────┴──────────────────┴───────┐                │
│ │ Byte Comparison Matcher          │                │
│ │ (matcher-byte-comp)              │                │
│ └──────────────┬───────────────────┘                │
│                │                                    │
│         ┌──────┴──────┐                             │
│         │ Logical Sig  │                            │
│         │ Evaluator    │                            │
│         │ (cli_exp_eval)│                           │
│         └──────┬──────┘                             │
│                │                                    │
│    ┌───────────┴───────────┐                        │
│    │ YARA Rule Evaluator   │                        │
│    │ (yara_exec.c)         │                        │
│    └───────────────────────┘                        │
└──────┬──────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────┐
│ Result: CL_CLEAN / CL_VIRUS / CL_ERROR              │
│ • Virus name string                                 │
│ • Cache result for future scans                     │
└─────────────────────────────────────────────────────┘
```

## Key Matching Algorithms

### 1. Aho-Corasick (matcher-ac.c — 128KB)
- Multi-pattern string matching
- Builds finite automaton from all signatures
- Scans file content once, matches all patterns simultaneously
- O(n + m) where n = file size, m = total pattern length
- Pre-filtering for fast rejection

### 2. Boyer-Moore (matcher-bm.c — 16KB)
- Single-pattern fast string search
- Shift-based optimization
- Used for exact byte sequences
- O(n/m) average case

### 3. Hash Matching (matcher-hash.c — 15KB)
- MD5, SHA1, SHA256 hash comparison
- Hash table lookup (O(1))
- Used for known-bad file hashes
- Also supports partial (sectional) hashing

### 4. PCRE Matching (matcher-pcre.c — 29KB)
- Perl-Compatible Regular Expressions
- Used for complex pattern rules
- Most CPU-expensive matcher
- Offset-limited to reduce overhead

### 5. Byte Comparison (matcher-byte-comp.c — 40KB)
- Numeric byte value comparisons
- Compares extracted values against thresholds
- Used for header field validation

## Signature Database System

### Database Types (from readdb.c)

| Extension | Format | Description |
|-----------|--------|-------------|
| `.hdb` | Hash DB | MD5 hash signatures |
| `.hsb` | Hash DB | SHA1/SHA256 hash signatures |
| `.mdb` | Hash DB | PE section hash signatures |
| `.ndb` | Extended | Byte-pattern signatures with offsets |
| `.ldb` | Logical | Boolean combinations of sub-signatures |
| `.cdb` | Container | Container metadata matching |
| `.pdb` | Phishing | Phishing URL patterns |
| `.wdb` | Allowlist | Whitelisted URLs |
| `.yar` | YARA | YARA rules |
| `.cvd` | Packed | Compressed/signed signature packages |

### Signature Format Examples

**Hash Signature (.hdb):**
```
MD5_HASH:SIZE:VIRUS_NAME
```

**Byte Pattern Signature (.ndb):**
```
VIRUS_NAME:TARGET:OFFSET:HEX_PATTERN
```
- TARGET: file type (0=any, 1=PE, 6=ELF, etc.)
- OFFSET: position constraint (*, EP+n, EOF-n, etc.)
- HEX_PATTERN: hex bytes with wildcards (`??`, `{n}`, `{n-m}`)

**Logical Signature (.ldb):**
```
VIRUS_NAME;TARGET;LOGIC;SUBSIG_0;SUBSIG_1;...
```
- LOGIC: boolean expression (`0&1`, `0|1`, `0&!1`, etc.)

### Database Size (Desktop)
- `main.cvd` — ~150MB (core signatures)
- `daily.cvd` — ~400MB+ (daily updates)
- `bytecode.cvd` — ~2MB (bytecode rules)

> [!CAUTION]
> Desktop ClamAV databases total 500MB+. This is COMPLETELY UNSUITABLE for mobile.
> Protctus must create a curated Android-specific signature subset.

## Build System

- **CMake** with extensive configuration options
- **vcpkg** for dependency management
- **Rust** (Cargo) for `libclamav_rust` components

### Key Dependencies
| Dependency | Purpose | Android Feasible? |
|-----------|---------|-------------------|
| `openssl` | Crypto operations | ✅ Via NDK |
| `json-c` | JSON parsing | ✅ Lightweight |
| `libxml2` | XML parsing | ⚠️ Heavy, use Android's |
| `pcre2` | Regex matching | ✅ Via NDK |
| `zlib` | Compression | ✅ Already in NDK |
| `bzip2` | Compression | ✅ Compact |
| `curl` | HTTP updates | ❌ Not needed on-device |
| `pthreads` | Threading | ✅ POSIX threads in NDK |

### Build Option for Minimal Library
```cmake
ENABLE_LIBCLAMAV_ONLY=ON    # Build only the core library
ENABLE_APP=OFF               # Skip CLI tools
ENABLE_MILTER=OFF            # Skip mail filter
ENABLE_CLAMONACC=OFF         # Skip on-access scanner
BYTECODE_RUNTIME=interpreter # No LLVM dependency
```

## Threading Model
- **Multi-threaded** — clamd runs thread pool
- Scan operations are thread-safe when using separate `cl_engine` instances
- Memory pool (mpool) for efficient allocation
- `cl_engine` is the main scanning context

## Memory Profile
- Engine initialization: 50-200MB (database loading)
- Per-scan overhead: 10-50MB (file parsing + signature matching)
- Cache system reduces repeated scans
- Memory pool reduces allocation overhead

## Caching System (cache.c)
- Tree-based cache using file hashes
- Stores scan results to avoid re-scanning
- Configurable cache size
- Eviction based on age
