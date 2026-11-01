# Protctus Engine Map

| Directory | Engine | Language | Purpose | Status |
|-----------|--------|----------|---------|--------|
| `malware-engine1/` | VPN Core Module | C++ | Native phishing pipeline, Chakravyuh security, native scanner | Reference source |
| `malware-engine2/` | YARA Core Module | C++/Kotlin | YARA rule-based pattern matching | Integrated (NDK) |
| `malware-engine3/` | ClamAV (libclamav core) | C | Signature-based AV — hash matching, byte-pattern scanning | **Integrated** (Kotlin reimplementation) |
| `malware-engine4/` | Quark-Engine | Python | Behavioral analysis — 5-stage crime model, threat scoring | **Integrated** (Kotlin reimplementation) |
| `malware-engine5/` | Androguard (core only) | Python | APK reverse engineering — manifest, DEX, certificate analysis | **Integrated** (Kotlin reimplementation) |

## Integration Architecture

All engines are integrated into Protctus as **Kotlin-native reimplementations** of the core detection algorithms. The source code in this directory serves as **reference material** — the actual scan execution happens in:

```
app/src/main/java/com/protctus/app/engine/
├── quark/                      ← Quark behavioral analysis
│   ├── QuarkEngine.kt          ← Main engine (5-stage behavioral scan)
│   ├── QuarkRule.kt            ← Rule data model
│   ├── QuarkRuleLoader.kt      ← JSON rule parser
│   ├── QuarkScanResult.kt      ← Result models
│   └── ApkAnalyzer.kt          ← APK metadata + DEX string pool extraction
├── clam/                       ← ClamAV signature scanning
│   ├── ClamSignatureEngine.kt  ← Hash + Boyer-Moore pattern matching
│   ├── ClamSignatureLoader.kt  ← .hdb/.ndb signature database parser
│   └── ClamSignature.kt        ← Signature data models
├── androguard/                 ← Androguard APK analysis
│   ├── AndroguardEngine.kt     ← Main engine orchestrator
│   ├── ManifestAnalyzer.kt     ← Permission/component/intent analysis
│   ├── DexInspector.kt         ← DEX metadata + string pool analysis
│   ├── CertificateAnalyzer.kt  ← Certificate/signing analysis
│   ├── ApkStructureAnalyzer.kt ← APK ZIP structure analysis
│   └── ApkAnalysisResult.kt    ← Result models
├── ThreatBrain.kt              ← Original heuristic engine
└── DeepScannerService.kt       ← Deep Scan orchestrator
```

## Scan Pipeline (per app)

| Layer | Engine | Phase | Speed |
|-------|--------|-------|-------|
| 1A | YARA | Signature scan on APK bytes | Fast |
| 1B | ThreatBrain | Permission + signing heuristics | Fast |
| 1C | Quark | Behavioral analysis (5-stage model) | Fast |
| 1D | ClamAV | Hash + byte-pattern matching | Fast |
| 1E | Androguard | Manifest, DEX, certificate, structure | Medium |

## Cleanup Notes

Desktop-only components have been removed:
- ClamAV: Removed daemon, CLI tools, mail filter, Rust components, test suites
- Androguard: Removed GUI, CLI, decompiler, pentest tools
- Quark: Removed agent, web reporter, forensic tools
- Only core parsing/matching source code retained as reference
