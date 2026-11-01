# Protctus NeuroShield — On-Device Phishing Detection Engine

## Architecture Overview

NeuroShield is a privacy-first, mobile-first network-threat detection engine that runs
primarily on-device via Android VpnService. It performs real-time domain-level phishing
detection using only locally-visible signals (domain, SNI, IP, basic connection metadata).

No TLS interception. No full URL paths. No page content. No DOM inspection. No MITM.
No user browsing data leaves the device unless explicitly opted into for cloud escalation.

## Trust Boundaries

| Boundary | Direction | What Crosses | Privacy Control |
|----------|-----------|--------------|-----------------|
| Device ⟷ VPN Tunnel | In/Out | DNS queries, TLS ClientHello SNI, QUIC Initial | Split-tunnel: only DNS port 53/853 intercepted; all other traffic passes natively |
| Device ⟷ Cloud | Out (deferred only) | Hashed domain (SHA-256), feature vector hash, ML confidence score | No raw URLs; only deferral-worthy domains; results cached |
| VPN ⟷ App | In/Out | Parcelable with domain, verdict, confidence | Parcelable contains no sensitive data beyond the domain itself |

## Layered Pipeline

```
┌─────────────────────────────────────────────────────────────────────┐
│  L0: Bloom Filter — Trusted Bypass (1M+ Tranco)                      │
│  • O(1) allow for known-good domains                                │
│  • False positives impossible (only false negatives → next layer)   │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (not in bloom)
┌─────────────────────────────────────────────────────────────────────┐
│  L1: Heuristics — Deterministic Rules                                │
│  • Domain entropy, consonant-vowel ratio                             │
│  • TLD risk score (.tk, .ml, .ga, etc.)                              │
│  • Shortener / free-hosting detection                                │
│  • DGA indicators, length, hyphen ratio                              │
│  • Homograph / punycode detection                                    │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (medium uncertainty)
┌─────────────────────────────────────────────────────────────────────┐
│  L2: LightGBM — Local ML Inference (53 features)                     │
│  • Lexical features extracted in O(L) from domain string            │
│  • Gradient-boosted trees, class-balanced training                  │
│  • Native Rust inference (no ONNX dependency)                       │
│  • Output: phishing probability in [0.0, 1.0]                       │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (high uncertainty)
┌─────────────────────────────────────────────────────────────────────┐
│  L4: Cloud Escalation (deferred domains only)                        │
│  • Hashed domain (SHA-256) sent to cloud                            │
│  • DNS, reputation feeds, passive signals                           │
│  • Result feeds back into local model via privacy-preserving update │
└─────────────────────────────────────────────────────────────────────┘
```

## Performance Targets

| Metric | Target | Measurement Point |
|--------|--------|-------------------|
| ML inference latency | < 50 µs | Per domain, mid-range CPU |
| End-to-end decision | < 30 ms | End-to-end (VPN → engine → verdict) |
| Local resolution rate | ≥ 85% | % of domains decided locally (L0–L3) |
| Effective FPR | ≤ 2% | False positives / total benign |
| Recall (phishing) | Maximize | Subject to FPR ≤ 2% |
| Memory footprint | < 50 MB | RSS of engine process |
| Binary size | < 5 MB | .so binary size (strip=symbols) |

## Decision Logic

```
if bloom.contains(domain):
    return ALLOW (L0)

heuristic_score = heuristic(domain)
if heuristic_score < 0.10:
    return ALLOW (L1)
if heuristic_score > 0.80:
    return BLOCK (L1)

onnx_score = ml_infer(domain)   # L2/L3
fused = 0.50 * lgb + 0.30 * onnx + 0.20 * heuristic

if fused >= 0.65:
    return BLOCK (L3)
elif fused >= 0.30:
    return SUSPICIOUS (L3) → defer to cloud
else:
    return ALLOW (L3)

# Cloud deferral:
cloud_result = cloud_lookup(domain_hash)
if cloud_result == MALICIOUS:
    return BLOCK (L4)
elif cloud_result == BENIGN:
    return ALLOW (L4)
else:
    return ALLOW (fail-open)  # Never break connectivity
```

## Crate Structure

```
neuroshield/
├── Cargo.toml                  # Workspace root
├── rust-toolchain.toml         # Toolchain + Android targets
├── Cargo.lock                  # Lockfile
├── crates/
│   ├── ns-engine/              # Core: packet parsing, types, signal bundle
│   │   ├── src/lib.rs          # Public API
│   │   ├── src/error.rs        # PhishingError
│   │   ├── src/types.rs        # SignalBundle, Verdict, PipelineResult
│   │   ├── src/signal.rs       # SignalBundleBuilder
│   │   ├── src/packet/         # IP, DNS, TLS, QUIC parsers
│   │   └── ...
│   ├── bloom-filter/           # NEW: 64KB BitSet Bloom filter
│   │   ├── src/lib.rs
│   │   └── src/bloom.rs
│   ├── heuristics/             # NEW: Deterministic domain analysis
│   │   ├── src/lib.rs
│   │   ├── src/domain.rs       # Entropy, n-grams, length ratios
│   │   ├── src/tld.rs          # TLD risk scoring
│   │   ├── src/structural.rs   # Hyphen, digit, subdomain features
│   │   └── src/dga.rs          # DGA indicators
│   ├── ml-inference/           # NEW: LightGBM native inference
│   │   ├── src/lib.rs
│   │   ├── src/model.rs        # Model loading (text format)
│   │   ├── src/tree.rs         # Tree traversal
│   │   └── src/features.rs     # 53-feature extraction
│   ├── decision-engine/        # NEW: Ensemble + deferral
│   │   ├── src/lib.rs
│   │   ├── src/policy.rs       # Verdict logic
│   │   ├── src/deferral.rs     # Cloud deferral decision
│   │   └── src/cache.rs        # LRU verdict cache
│   └── android-ffi/            # NEW: JNI bindings for Android
│       ├── src/lib.rs
│       └── src/jni_wrapper.rs
```

## Build Configuration

- Target: `aarch64-linux-android`, `armv7-linux-androideabi`, `x86_64-linux-android`, `i686-linux-android`
- Profile: `release` with LTO, opt-level "z", codegen-units=1, strip="symbols", panic="abort"
- No ONNX runtime dependency (native LightGBM text model parsing)
- No external network crates (all detection is local)

## Security Properties

1. **No TLS inspection** — Only SNI from ClientHello is observed, never decrypted.
2. **No MITM** — Certificates are never intercepted or modified.
3. **No sensitive permissions** — Only `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `BIND_VPN_SERVICE`.
4. **No data exfiltration** — Domain hashes only sent to cloud for deferred domains; no raw URLs, no browsing history.
5. **Fail-open** — If the engine crashes or times out, traffic passes through (user connectivity preserved).
6. **No persistent storage of raw domains** — Only hashed domains in threat cache; LRU eviction.

## Deployment

- Model file: `lightgbm_model.txt` (LightGBM text format, ~3.5 MB)
- Bloom filter: `tranco_bloom.bin` (serialized BitSet, ~64 KB)
- Model versioning: Semantic versioning with SHA-256 integrity check
- Update mechanism: Download signed model + bloom filter, verify signature, atomically swap
