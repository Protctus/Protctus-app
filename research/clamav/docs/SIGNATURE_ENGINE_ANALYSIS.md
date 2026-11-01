# ClamAV — Signature Engine Analysis

## Signature Matching Architecture

ClamAV's core strength is its multi-layer signature matching system. The engine supports 6 distinct matching algorithms that work together for comprehensive threat detection.

---

## 1. Hash-Based Matching (`matcher-hash.c`)

### How It Works
- Computes MD5, SHA1, or SHA256 of the file (or file section)
- Looks up hash in a sorted hash table
- O(1) lookup time via hash table

### Signature Format
```
HASH:FILE_SIZE:MALWARE_NAME
```
Example:
```
e4d909c290d0fb1ca068ffaddf22cbd0:1234:Trojan.Android.FakeAV
```

### Key Data Structures
```c
struct cli_hash_patt {
    struct cli_htu32 *sizehashes[CLI_HASH_AVAIL_TYPES];
    // CLI_HASH_MD5=0, CLI_HASH_SHA1=1, CLI_HASH_SHA256=2
};
```

### Android Feasibility
- ✅ **Highly portable** — Simple hash computation + lookup
- ✅ Low memory — Hash table scales linearly with signature count
- ✅ Fast — O(1) per file
- Android has native `MessageDigest` for MD5/SHA1/SHA256

---

## 2. Aho-Corasick Multi-Pattern Matching (`matcher-ac.c`)

### How It Works
- Builds a finite automaton (trie + failure links) from ALL byte-pattern signatures
- Scans file content ONCE through the automaton
- Matches ALL patterns simultaneously in O(n + m) time
  - n = file size, m = total pattern length

### Key Data Structures
```c
struct cli_ac_node {
    struct cli_ac_node **trans;   // transition table (256 entries)
    struct cli_ac_node *fail;    // failure link
    struct cli_ac_patt *list;    // patterns ending at this node
};

struct cli_ac_patt {
    uint16_t *pattern;           // pattern bytes
    uint32_t length;             // pattern length
    uint32_t mindist, maxdist;   // offset constraints
    char *virname;               // malware name
};
```

### Pre-filtering (`filtering.c`)
- Before running full AC scan, a Bloom-filter-like structure rejects files
  that cannot possibly match any signature
- Reduces unnecessary AC traversals by ~90%

### Android Feasibility
- ⚠️ **Medium complexity** — AC automaton construction is memory-heavy
- ⚠️ Memory: AC trie can consume 100MB+ for large signature sets
- ✅ Scanning speed is excellent (single-pass)
- **Recommendation:** Build AC trie at app install time, persist to disk, load via mmap

---

## 3. Boyer-Moore String Matching (`matcher-bm.c`)

### How It Works
- Single-pattern fast string search
- Uses bad-character shift table for O(n/m) average case
- Used for signatures with fixed offset positions

### Key Data Structures
```c
struct cli_bm_patt {
    unsigned char *pattern;
    unsigned char *prefix;
    uint32_t length;
    uint32_t offset;
    char *virname;
};
```

### Android Feasibility
- ✅ **Highly portable** — Simple algorithm
- ✅ Minimal memory
- ✅ Fast for offset-specific signatures

---

## 4. PCRE Regex Matching (`matcher-pcre.c`)

### How It Works
- Perl-Compatible Regular Expressions for complex patterns
- Applied AFTER AC/BM matching narrows candidates
- Offset-limited scanning to reduce overhead

### Android Feasibility
- ⚠️ CPU-heavy for complex patterns
- ✅ `java.util.regex` available on Android
- ✅ PCRE2 can compile for NDK
- **Recommendation:** Limit regex count, use only for critical patterns

---

## 5. Byte Comparison Matching (`matcher-byte-comp.c`)

### How It Works
- Extracts numeric values from specific file offsets
- Compares against thresholds (>, <, =, !=)
- Used for header validation and structural checks

### Android Feasibility
- ✅ **Trivially portable** — Simple byte comparison

---

## 6. Logical Signatures (`cli_exp_eval` in `matcher.c`)

### How It Works
- Boolean expressions combining sub-signature matches
- Example: `Sig0 AND Sig1 AND NOT Sig2`
- Evaluated after individual pattern matches are collected

### Signature Format (.ldb)
```
MalwareName;TargetType;LogicExpression;SubSig0;SubSig1;...
```
Example:
```
Trojan.Android.Banker;0;0&1;aabb??ccdd;eeff0011
```

### Android Feasibility
- ✅ **Trivially portable** — Boolean expression evaluation

---

## Signature Database Structure

### Desktop Database (NOT suitable for mobile)
```
main.cvd    ~150MB  (core signatures, updated quarterly)
daily.cvd   ~400MB  (daily updates, grows continuously)
bytecode.cvd ~2MB   (bytecode detection rules)
```

### Proposed Mobile Signature Strategy

> [!IMPORTANT]
> Protctus MUST NOT ship desktop ClamAV databases. They are 500MB+ and designed for email/web scanning, not Android APK analysis.

**Recommended approach:**

1. **Android Hash DB** (~5-10MB)
   - Known Android malware hashes (MD5/SHA256)
   - Curated from VirusTotal/MalwareBazaar Android samples
   - Updated weekly via app updates

2. **Android Pattern DB** (~2-5MB)
   - Byte patterns specific to Android malware families
   - Focus on: DEX headers, native .so signatures, packed APK patterns
   - Curated subset of ClamAV `.ndb` signatures

3. **Logical Rules** (~500KB)
   - Boolean combinations for Android-specific threats
   - Stalkerware detection patterns
   - Adware framework detection

4. **Total target: <20MB** on-device

---

## Update System (freshclam → NOT needed)

ClamAV's `freshclam` downloads signatures via HTTP from ClamAV mirrors.

For Protctus:
- Bundle initial signatures in APK assets
- Check for updates via Firebase Remote Config or CDN
- Download delta updates (new signatures only)
- Store in app-private storage

---

## Scan Result Caching (cache.c)

### How It Works
- Binary tree indexed by file hash
- Stores: `{hash → scan_result, timestamp}`
- Skips re-scanning unchanged files
- Configurable eviction

### Android Implementation
- Use Room/SQLite for scan result cache
- Key: file path + last-modified timestamp + size
- Value: scan result + scan timestamp
- Evict entries older than 7 days

---

## Performance Characteristics

| Operation | Time (Desktop) | Est. Android (Mid-Range) |
|-----------|----------------|--------------------------|
| Engine init (load DB) | 1-5s | 2-8s |
| Hash scan (single file) | <1ms | <5ms |
| AC scan (small file <1MB) | 5-20ms | 20-80ms |
| AC scan (large file >10MB) | 50-200ms | 200-800ms |
| PCRE matching | 10-100ms | 50-500ms |
| Full APK scan (unzip + scan all) | 100-500ms | 500-2000ms |

> [!NOTE]
> These are estimates based on desktop benchmarks with a ~3x slowdown factor
> for mid-range Android ARM64 processors.
