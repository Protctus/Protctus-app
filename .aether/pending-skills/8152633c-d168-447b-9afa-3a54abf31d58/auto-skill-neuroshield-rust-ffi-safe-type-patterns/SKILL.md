---
name: neuroshield-rust-ffi-safe-type-patterns
description: how to define FFI-safe structs in Rust for the NeuroShield engine when String/Vec/Option cannot go inside #[repr(C)]
source: auto-skill
extracted_at: '2026-07-03T08:58:04.352Z'
---

# Problem

NeuroShield needs structs like `SignalBundle`, `DnsResponse`, and `TlsClientHello` to cross the JNI/C++ boundary via `#[repr(C)]`. The initial implementation placed `String`, `Vec`, and `Option<String>` inside these structs, which is **undefined behavior** — `#[repr(C)]` only guarantees layout for C-compatible types (primitives, raw pointers, fixed arrays).

## Solution

Use a **dual-representation** pattern: keep the safe Rust types for the internal engine pipeline, and expose an explicit FFI-safe struct only at the boundary. Convert between them with a safe adapter that does string/heap management.

### 1. Internal (safe) representation — no FFI

```rust
#[derive(Debug, Clone)]
pub struct DnsResponse {
    pub ttl: u32,
    pub a_record_count: u8,
    pub a_records: [[u8; 4]; 8],
    pub fast_flux: bool,
}
```

- No `#[repr(C)]` needed.
- Can use `String`, `Vec`, `Option` freely.

### 2. FFI-safe representation — C-compatible

```rust
use std::os::raw::c_char;
use std::ptr;

#[repr(C)]
pub struct FfiDnsResponse {
    pub ttl: u32,
    pub a_record_count: u8,
    pub a_records: [[u:*() ptr: pub const a_records: [[u8; 4]; 8] = [[0; 4]; 8];
    pub fast_flux: bool,
}
```

- Only fixed-size arrays, primitives, and raw pointers.
- If a string *must* cross the boundary, pass it as `*const c_char` (null-terminated, managed by C) or a `*const u8` + `usize` length pair. Never `String` or `&str`.

### 3. Adapter (conversion)

```rust
impl FfiSignalBundle {
    /// # Safety
    /// `out` must be valid, non-null, and writable.
    pub unsafe fn from_safe(src: &SignalBundle, out: *mut FfiSignalBundle) -> Result<(), PhishingError> {
        // Validate pointer
        if out.is_null() {
            return Err(PhishingError::Other("null out pointer".into()));
        }
        let dst = &mut *out;
        // Copy fixed-size fields
        dst.port = src.port;
        dst.protocol = src.protocol;
        // ... domain/IP handled externally via CString
        Ok(())
    }
}
```

### 4. Critical rules

| ❌ Do NOT | ✅ Do instead |
|-----------|---------------|
| `String` inside `#[repr(C)]` | `*const c_char` + length, managed by caller |
| `Vec<T>` inside `#[repr(C)]` | Fixed array `[T; N]` + `count` field |
| `Option<String>` inside `#[repr(C)]` | Two fields: `*const c_char` + `bool` flag |
| `#[repr(C)] struct { x: Vec<u8> }` | `#[repr(C)] struct { data: *const u8, len: usize }` (caller owns memory) |

### 5. Why we chose this for NeuroShield

- The internal pipeline (L0→L4) runs entirely in Rust and needs `String`/`Vec`.
- The JNI boundary only needs to *read* the `Verdict` and `SignalBundle` summary.
- Pushing all string management into the adapter keeps the engine code idiomatic and the FFI surface minimal.

## How to apply

Whenever adding a new struct that crosses the C/JNI boundary:
1. Define the safe Rust version in `types.rs` without `#[repr(C)]`.
2. If it must be exposed to C, add an `Ffi*` twin in a new `ffi_types.rs` (or `ffi.rs`).
3. Write an `unsafe` conversion function that validates every pointer and returns a typed error.
4. Never convert `String` to `*const c_char` without checking for null terminators and using `CString` correctly.

## Related files

- `neuroshield/crates/ns-engine/src/types.rs` — safe internal types
- `neuroshield/crates/ns-engine/src/ffi.rs` — FFI types and adapters (planned)
