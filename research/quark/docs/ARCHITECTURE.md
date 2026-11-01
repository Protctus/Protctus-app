# Quark-Engine — Architecture Analysis

## Overview

Quark-Engine is a Python-based Android malware scoring system that uses a "Five Stages of Crime" behavioral analysis model. It evaluates APKs by checking if they exhibit suspicious combinations of API calls, permissions, and data flows.

## Source Code Structure

```
quark/
├── __init__.py              # Version
├── cli.py                   # CLI entry point (14KB)
├── config.py                # Configuration
├── freshquark.py            # Rule downloader
├── logo.py                  # ASCII art logo
├── radiocontrast.py         # Obfuscation analysis
├── report.py                # Report generation
├── rulegeneration.py        # Auto rule generation
├── core/
│   ├── analysis.py          # QuarkAnalysis data model (2KB)
│   ├── apkinfo.py           # Androguard-based APK analysis (21KB)
│   ├── apkpatcher.py        # APK modification (13KB)
│   ├── parallelquark.py     # Parallel scanning (4KB)
│   ├── quark.py             # ★ MAIN ENGINE — Five-stage checker (33KB)
│   ├── r2apkinfo.py         # Radare2 backend (24KB)
│   ├── rzapkinfo.py         # Rizin backend (28KB)
│   ├── shurikenapkinfo.py   # Shuriken backend (24KB)
│   ├── axmlreader/          # Custom AXML parser
│   ├── interface/           # BaseApkinfo interface
│   └── struct/
│       ├── bytecodeobject.py   # Bytecode representation
│       ├── methodobject.py     # Method representation
│       ├── registerobject.py   # Register tracking
│       ├── ruleobject.py       # ★ Rule model (4KB)
│       ├── tableobject.py      # Table output
│       └── valuenode.py        # Value/call tracking (8KB)
├── evaluator/
│   └── pyeval.py            # ★ Dalvik opcode evaluator (31KB)
├── utils/
│   ├── colors.py            # Terminal coloring
│   ├── graph.py             # Call graph visualization
│   ├── output.py            # Report output
│   ├── pprint.py            # Pretty printing
│   ├── tools.py             # Utility functions
│   └── weight.py            # ★ Threat level calculation
├── rules/                   # Sample rules
├── agent/                   # LLM agent integration
├── forensic/                # Forensic analysis
├── script/                  # Frida scripting
└── webreport/               # HTML report templates
```

## Five-Stage Crime Detection Model

This is the core innovation of Quark-Engine. Each rule defines a "crime" and the engine checks how many stages the APK passes through:

```
Stage 1: Permission Request
    │  Does the APK request the permissions needed for this behavior?
    │  (e.g., SEND_SMS + ACCESS_FINE_LOCATION)
    ▼
Stage 2: Native API Usage
    │  Does the APK contain calls to either of the two APIs?
    │  (e.g., getCellLocation OR sendTextMessage)
    ▼
Stage 3: Both APIs Present
    │  Does the APK contain calls to BOTH APIs?
    │  (e.g., getCellLocation AND sendTextMessage)
    ▼
Stage 4: Call Sequence
    │  Are both APIs called from a common parent function
    │  in the correct order?
    │  (e.g., getCellLocation called BEFORE sendTextMessage
    │   within the same execution path)
    ▼
Stage 5: Same Register (Data Flow)
    │  Do both APIs operate on the same register/variable?
    │  (e.g., location data from getCellLocation is passed
    │   to sendTextMessage as the message body)
    ▼
CONFIDENCE = stage_count × 20%
WEIGHT = (2^(confidence-1) × score) / 2^4
```

## Rule Format (JSON)

```json
{
    "crime": "Send Location via SMS",
    "permission": [
        "android.permission.SEND_SMS",
        "android.permission.ACCESS_FINE_LOCATION"
    ],
    "api": [
        {
            "class": "Landroid/telephony/TelephonyManager",
            "method": "getCellLocation",
            "descriptor": "()Landroid/telephony/CellLocation;"
        },
        {
            "class": "Landroid/telephony/SmsManager",
            "method": "sendTextMessage",
            "descriptor": "(Ljava/lang/String; ...)V"
        }
    ],
    "score": 4,
    "label": ["location", "collection"]
}
```

**Rule Components:**
- `crime` — Human-readable description of the suspicious behavior
- `permission` — Required Android permissions (Stage 1 check)
- `api` — Pair of APIs defining the behavior (class/method/descriptor in Dalvik format)
- `score` — Weight value for threat calculation (negative = suspicious, can be any float)
- `label` — Category tags for grouping

## Available Rules (274 in ev-flow/quark-rules)

Labels include: `sms`, `location`, `calendar`, `calllog`, `http`, `accessibility service`, `so`, `camera`, `collection`

## Scoring System (weight.py)

```
Total Score = Σ(rule.score for all rules)
Total Weight = Σ(rule.get_score(confidence) for all rules)

Threat Level Thresholds:
- weight ≤ score_sum/16     → Low Risk
- weight ≤ score_sum/8      → Low Risk  
- weight ≤ score_sum/4      → Moderate Risk
- weight ≤ score_sum/2      → Moderate Risk
- weight > score_sum/2      → High Risk

get_score(confidence) = (2^(confidence-1) × score) / 16
  confidence=0 → 0
  confidence=1 → score/16
  confidence=2 → score/8
  confidence=3 → score/4
  confidence=4 → score/2
  confidence=5 → score
```

## Key Algorithms

### Method Intersection (find_intersection)
- Finds common parent functions that call both APIs
- Recursive search up to `MAX_SEARCH_LAYER = 3` levels
- Uses cross-reference (XREF) traversal via `upperfunc()`

### Sequence Checking (check_sequence)
- Verifies API A is called before API B within common parent
- Uses bytecode offset comparison
- Sorted by call offset

### Parameter Checking (check_parameter → PyEval)
- Evaluates Dalvik bytecodes to track register usage
- Builds register-to-value mappings
- Checks if both APIs share register data (data flow)
- Most complex and CPU-intensive stage

## Backend Abstraction

Quark supports multiple DEX analysis backends through `BaseApkinfo`:

| Backend | Module | Description |
|---------|--------|-------------|
| Androguard | `apkinfo.py` | Default. Full Python DEX analysis |
| Shuriken | `shurikenapkinfo.py` | C++ backed, faster |
| Rizin | `rzapkinfo.py` | RE framework integration |
| Radare2 | `r2apkinfo.py` | RE framework integration |

**For Protctus:** We need our own `BaseApkinfo` implementation that wraps Android-native DEX parsing.

## Threading Model
- **Single-threaded** by default
- `parallelquark.py` provides multi-process scanning (Python multiprocessing)
- Not suitable for Android (no fork() semantics)
- For Protctus: rewrite with Kotlin coroutines

## Memory Profile
- Rule loading: ~1MB per 274 rules
- Per-APK analysis: 50-300MB (depends on DEX size)
- Most memory from Androguard DEX parsing + XREF building

## I/O-Heavy Sections
- APK reading and DEX extraction
- Rule JSON loading (274 files)

## CPU-Heavy Sections
- Stage 4: Cross-reference intersection search
- Stage 5: PyEval bytecode evaluation (register tracking)
- Androguard's DEX parsing initialization

## Dependencies

| Dependency | Purpose | Android Portable? |
|-----------|---------|-------------------|
| `androguard==3.4.0a1` | DEX analysis | ❌ Python-only |
| `prettytable` | Table output | Not needed |
| `numpy` | Statistical calcs | ⚠️ Heavy |
| `tqdm` | Progress bars | Not needed |
| `colorama` | Terminal colors | Not needed |
| `graphviz` | Call graphs | Not needed |
| `r2pipe` | Radare2 | Not needed |
| `rzpipe` | Rizin | Not needed |
| `click` | CLI | Not needed |
| `plotly` | Visualization | Not needed |
