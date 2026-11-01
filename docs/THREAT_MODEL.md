# Protctus Threat Model & Security Assumptions

## Scope

Android application "Protctus" (package `com.protctus.app`), minSdk 27 / targetSdk 35.
Threat model covers on-device phishing/malware detection, real-time DNS shielding, local storage, and network integration.

## Trust boundaries

| Boundary | Trust level | Notes |
|----------|-------------|-------|
| Device / app process | Trusted (host) | All inference runs in-app; model/assets bundled. |
| Android OS | Trusted | Relies on Android sandbox, permission model, VpnService. |
| User | Partially trusted | May grant VPN/accessibility permissions; may add domains to allowlist. |
| Local storage (Room/DataStore) | Trusted (app-private) | App-private dir; no broad storage permission. |
| Network (Internet) | Untrusted | HTTPS only (cleartext blocked); system CAs only in release. |
| External AI / cloud services | Untrusted | Not used by default for detection. |
| Upstream DNS (8.8.8.8) | Partially trusted | Hardcoded in native `vpn-core` (see findings TM-04). |

## Assets to protect

- User browsing data / URLs (must not leave device by default).
- Local detection history and threat alerts (Room).
- User credentials, OTP/TOTP secrets, payment tokens.
- APK integrity (anti-repackaging).
- ML model files (bundled assets).

## Threats & mitigations

| ID | Threat | Impact | Mitigation | Status |
|----|--------|--------|------------|--------|
| TM-01 | Phishing site tricks user into entering credentials | High | On-device NeuroShield + heuristic pipeline blocks/warns. NOT at 0.95 recall target (see ML report). Residual risk disclosed. |
| TM-02 | Malformed/misleading URL bypasses parser | Medium | Host extraction strips scheme/www/port/path/query; punycode via IDN; IP guard. Tested in unit tests. |
| TM-03 | Homograph / IDN spoofing (`xn--`) | Medium | `has_punycode` feature + indicator; `VerdictEngine` flags it. |
| TM-04 | Native VPN sends all DNS to hardcoded 8.8.8.8 | Medium (privacy) | User DNS queries visible to Google. Not user-configurable. **Finding — see remediation.** |
| TM-05 | Private DNS (DoT/DoH) bypasses VPN DNS interception | Medium | Detected; user warned via notification. Cannot block without breaking system DoT. |
| TM-06 | Cleartext HTTP traffic | Low | `network_security_config.xml` blocks cleartext; system CAs only. |
| TM-07 | APK repackaging / tampering | Medium | `SignatureVerifier` runtime check; currently placeholder hash (`UPDATE_ME...`) — must be set for release. |
| TM-08 | Broad storage permission abuse | Low/removed | `MANAGE_EXTERNAL_STORAGE` removed; reports go to app-specific external files dir (no permission). |
| TM-09 | Backup exfiltration of local DB | Low | `android:allowBackup="false"`; empty backup/data-extraction rules. |
| TM-10 | Secrets committed to repo | High | `.env` real keys replaced with `REPLACE_ME` placeholders; `.env` gitignored. Build fails closed if key missing/placeholder. |
| TM-11 | URLs uploaded to external AI by default | High (privacy) | **Fixed:** Cloud RBI/Llama3 call removed from `UrlScannerScreen`; detection is on-device only. |
| TM-12 | Fake "Protected/Secure" UI claims | Medium | Removed misleading UI; scanner reports real inference verdicts with indicators. |
| TM-13 | Swallowed exceptions conceal failures | Medium | Audited key paths; VPN `getNativeMetrics` returns zeros (placeholder — see TM-14). |
| TM-14 | Placebo metrics (`getNativeMetrics` all zeros) | Low | Native metrics stub returns `{0,0,0,0,0,0}`. Logged but not actionable. |
| TM-15 | Model below accuracy/recall target | High | Measured 0.812 / 0.788. Disclosed; not fabricated. See ml/evaluation/README.md. |

## Fail-open vs fail-closed

- Model load failure → DEGRADED mode (heuristics only), score 0.0 from ONNX. **Fail-open** (does not block benign traffic on model failure).
- Malformed URL → SAFE (fail-open, avoids blocking valid traffic).
- VPN setup failure → logged, traffic not intercepted (fail-open for connectivity).
- Signature verification placeholder hash → currently does NOT crash (logs only). **Should be fail-closed for release.**

## Privacy assumptions

- Detection is 100% local; no URL is sent to any remote service by default.
- Firebase (Auth/Firestore/FCM/Crashlytics) is integrated but not used to exfiltrate browsing data.
- Threat telemetry (hashes, scores) is stored locally in Room; not uploaded by default.
- VPN split-tunnel intercepts only DNS; other traffic bypasses the tunnel at native speed.

## Out of scope

- TLS inspection / certificate transparency / domain-age lookup (not implemented; never claimed).
- Reputation feed / cloud blocklist (not used by default).
- Redirect monitoring (DNS-only, no HTTP proxy).
- iOS / desktop platforms.
