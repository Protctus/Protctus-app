# Quark-Engine — Rule Engine Analysis

## Rule Format Deep Dive

### JSON Schema

Every Quark rule is a JSON file with this exact structure:

```json
{
    "crime": "<string: human-readable behavior description>",
    "permission": ["<android.permission.X>", ...],
    "api": [
        {
            "class": "<Dalvik class descriptor>",
            "method": "<method name>",
            "descriptor": "<Dalvik method descriptor>",
            "match_keywords": ["<optional keywords>"]   // OPTIONAL
        },
        {
            "class": "<Dalvik class descriptor>",
            "method": "<method name>",
            "descriptor": "<Dalvik method descriptor>",
            "match_keywords": ["<optional keywords>"]   // OPTIONAL
        }
    ],
    "score": <float: threat weight>,
    "label": ["<category tag>", ...]
}
```

### Field Details

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `crime` | string | YES | Human-readable behavior description |
| `permission` | string[] | YES | Android permissions required (can be empty `[]`) |
| `api` | object[2] | YES | Exactly 2 API method definitions (first & second) |
| `api[n].class` | string | YES | Dalvik class descriptor (e.g., `Landroid/telephony/SmsManager;`) |
| `api[n].method` | string | YES | Method name (e.g., `sendTextMessage`) |
| `api[n].descriptor` | string | YES | Dalvik method descriptor (parameters + return type) |
| `api[n].match_keywords` | string[] | NO | Keywords to match in method arguments |
| `score` | float | YES | Threat weight (negative = more suspicious) |
| `label` | string[] | YES | Category tags for grouping |

### Descriptor Format

Dalvik descriptors use JNI-style notation:
- `V` = void, `Z` = boolean, `B` = byte, `I` = int, `J` = long
- `Ljava/lang/String;` = String object
- `[B` = byte array
- `(Ljava/lang/String; I)V` = method(String, int) → void

### Score Semantics

| Score Range | Meaning |
|-------------|---------|
| `-4` to `-1` | Highly suspicious behavior |
| `-1` to `0` | Mildly suspicious |
| `-0.05` to `0` | Benign but tracked |
| `0` to `+1` | Potentially benign |

---

## Rule Categories (from 274 rules)

### By Label Distribution
Based on analysis of the quark-rules repository:

| Label | Description | Example Behaviors |
|-------|-------------|-------------------|
| `sms` | SMS read/write/send | Read SMS content, send SMS silently |
| `location` | Device location leakage | Get GPS coords, send location data |
| `calendar` | Calendar access | Read calendar events |
| `calllog` | Call log manipulation | Read/delete call records |
| `http` | Network data exfiltration | Send data via HTTP POST |
| `accessibility service` | Accessibility abuse | UI automation attacks |
| `so` | Native library loading | Load suspicious .so files |
| `camera` | Camera access | Take photos silently |
| `collection` | Data collection | Aggregate sensitive data |

### Example Rules (from source)

**Rule: Send Location via SMS** (`sendLocation_SMS.json`)
```json
{
    "crime": "Send Location via SMS",
    "permission": ["android.permission.SEND_SMS", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"],
    "api": [
        {"class": "Landroid/telephony/TelephonyManager", "method": "getCellLocation", "descriptor": "()Landroid/telephony/CellLocation;"},
        {"class": "Landroid/telephony/SmsManager", "method": "sendTextMessage", "descriptor": "(Ljava/lang/String; ...)V"}
    ],
    "score": 4,
    "label": ["location", "collection"]
}
```

**Rule: Read sensitive data into JSON** (`00010.json`)
```json
{
    "crime": "Read sensitive data(SMS, CALLLOG) and put it into JSON object",
    "permission": [],
    "api": [
        {"class": "Landroid/content/ContentResolver;", "method": "query", "descriptor": "(Landroid/net/Uri; ...)Landroid/database/Cursor;"},
        {"class": "Lorg/json/JSONObject;", "method": "put", "descriptor": "(Ljava/lang/String; Ljava/lang/Object;)Lorg/json/JSONObject;"}
    ],
    "score": -1.15,
    "label": ["sms", "calllog", "collection"]
}
```

---

## Behavioral Scoring Algorithm

### Per-Rule Scoring

```python
confidence = check_item.count(True)  # 0-5 stages passed
confidence_pct = confidence * 20     # 0-100%

# Exponential weight growth
weight = (2^(confidence-1) × score) / 16

# confidence=0 → weight = 0
# confidence=1 → weight = score/16
# confidence=2 → weight = score/8
# confidence=3 → weight = score/4
# confidence=4 → weight = score/2
# confidence=5 → weight = score
```

### Global Threat Assessment

```python
weight_sum = Σ(weight for all matched rules)
score_sum  = Σ(score for all rules evaluated)

# Threshold-based classification
if weight_sum <= score_sum/16:    "Low Risk"
elif weight_sum <= score_sum/8:   "Low Risk"
elif weight_sum <= score_sum/4:   "Moderate Risk"
elif weight_sum <= score_sum/2:   "Moderate Risk"
else:                             "High Risk"
```

### Design Insight
The exponential weighting means:
- Stage 1-2 matches (permission + API presence) contribute minimally
- Stage 4-5 matches (sequence + data flow) dominate the score
- This makes the system resilient to false positives from simple API presence

---

## Five-Stage Detection Flow (Code-Level Detail)

### Stage 1: Permission Check (`quark.py:472-479`)
```python
if set(rule_obj.permission).issubset(set(self.apkinfo.permissions)):
    rule_obj.check_item[0] = True
```
Simple set containment check. O(1) per rule.

### Stage 2: Single API Check (`quark.py:481-502`)
```python
first_api_list = self.find_api_usage(class, method, descriptor)
second_api_list = self.find_api_usage(class, method, descriptor)
```
Searches DEX method table for API calls. Uses subclass checking for polymorphism. O(n) where n = number of methods.

### Stage 3: Both APIs Check (`quark.py:509-516`)
```python
if first_api_list and second_api_list:
    rule_obj.check_item[2] = True
```
Simple boolean AND. O(1).

### Stage 4: Sequence Check (`quark.py:520-568`)
```python
# Find common parent functions
mutual_parent = find_intersection(first_xref, second_xref)
# Check call order within parent
if check_sequence(parent, first_wrapper, second_wrapper):
    rule_obj.check_item[3] = True
```
Cross-reference intersection with recursive search (up to 3 layers). O(n×m) per rule.

### Stage 5: Register Check (`quark.py:570-581`)
```python
if self.check_parameter(parent, first_wrapper, second_wrapper):
    rule_obj.check_item[4] = True
```
Full bytecode evaluation using PyEval. Tracks register usage across all instructions. O(k) where k = number of bytecode instructions in parent method.

---

## Reusable Components for Protctus

### Directly Reusable (concept level)
1. **Rule JSON format** — Simple, extensible, easy to parse on Android
2. **Five-stage scoring model** — Can be reimplemented in Kotlin
3. **Exponential weight calculation** — Trivial math
4. **274 behavioral rules** — JSON files, directly loadable

### Requires Reimplementation
1. **API usage finder** — Needs Kotlin DEX parsing
2. **Cross-reference building** — Needs lightweight XREF
3. **Sequence checking** — Needs bytecode offset tracking
4. **Register evaluation (PyEval)** — Complex, needs Dalvik VM simulation in Kotlin

### Not Needed
1. CLI/reporting infrastructure
2. Frida scripting
3. LLM agent
4. Web reporting
5. Visualization
