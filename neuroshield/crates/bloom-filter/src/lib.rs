//! # Bloom Filter — L0 Trusted Bypass
//!
//! 64KB (524,288 bit) Bloom filter with 3 independent MurmurHash3 seeds.
//! Supports ~36,000 entries at < 1% false positive rate.
//! False positives route to next layer; false negatives impossible.

use std::hash::{Hash, Hasher};

const BLOOM_BITS: usize = 524_288;
const BLOOM_BYTES: usize = BLOOM_BITS / 8;
const NUM_HASHES: usize = 3;

const C1: u32 = 0xcc9e_2d51;
const C2: u32 = 0x1b87_3593;

/// Fixed-size Bloom filter stored as a BitSet.
#[derive(Debug, Clone)]
pub struct BloomFilter {
    bits: [u8; BLOOM_BYTES],
    item_count: u32,
}

impl Default for BloomFilter {
    fn default() -> Self {
        Self::new()
    }
}

impl BloomFilter {
    pub fn new() -> Self {
        BloomFilter {
            bits: [0u8; BLOOM_BYTES],
            item_count: 0,
        }
    }

    pub fn len(&self) -> u32 {
        self.item_count
    }

    pub fn is_empty(&self) -> bool {
        self.item_count == 0
    }

    pub fn insert(&mut self, item: &str) {
        for i in 0..NUM_HASHES {
            let hash = murmurhash3(item.as_bytes(), i as u32) as usize;
            let bit_idx = hash % BLOOM_BITS;
            let byte_idx = bit_idx / 8;
            let bit_mask = 1u8 << (bit_idx % 8);
            self.bits[byte_idx] |= bit_mask;
        }
        self.item_count += 1;
    }

    pub fn might_contain(&self, item: &str) -> bool {
        for i in 0..NUM_HASHES {
            let hash = murmurhash3(item.as_bytes(), i as u32) as usize;
            let bit_idx = hash % BLOOM_BITS;
            let byte_idx = bit_idx / 8;
            let bit_mask = 1u8 << (bit_idx % 8);
            if self.bits[byte_idx] & bit_mask == 0 {
                return false;
            }
        }
        true
    }

    pub fn to_bytes(&self) -> [u8; BLOOM_BYTES] {
        self.bits
    }

    pub fn from_bytes(bytes: [u8; BLOOM_BYTES], item_count: u32) -> Self {
        BloomFilter {
            bits: bytes,
            item_count,
        }
    }

    pub const fn serialized_size() -> usize {
        BLOOM_BYTES
    }
}

fn fmix32(mut h: u32) -> u32 {
    h ^= h >> 16;
    h = h.wrapping_mul(0x85eb_ca6b);
    h ^= h >> 13;
    h = h.wrapping_mul(0xc2b2_ae35);
    h ^= h >> 16;
    h
}

fn murmurhash3(data: &[u8], seed: u32) -> u32 {
    let nblocks = data.len() / 4;
    let mut h1 = seed;

    for i in 0..nblocks {
        let mut k1 = u32::from_le_bytes([
            data[i * 4],
            data[i * 4 + 1],
            data[i * 4 + 2],
            data[i * 4 + 3],
        ]);
        k1 = k1.wrapping_mul(C1);
        k1 = k1.rotate_left(15);
        k1 = k1.wrapping_mul(C2);

        h1 ^= k1;
        h1 = h1.rotate_left(13);
        h1 = h1.wrapping_mul(5).wrapping_add(0xe654_6b64);
    }

    let tail_start = nblocks * 4;
    let mut k1: u32 = 0;
    let tail = &data[tail_start..];
    match tail.len() {
        3 => k1 ^= (tail[2] as u32) << 16,
        2 => k1 ^= (tail[1] as u32) << 8,
        1 => {
            k1 ^= tail[0] as u32;
            k1 = k1.wrapping_mul(C1);
            k1 = k1.rotate_left(15);
            k1 = k1.wrapping_mul(C2);
            h1 ^= k1;
        }
        _ => {}
    }

    h1 = fmix32(h1);
    h1
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_insert_and_might_contain() {
        let mut bf = BloomFilter::new();
        bf.insert("google.com");
        bf.insert("microsoft.com");

        assert!(bf.might_contain("google.com"));
        assert!(bf.might_contain("microsoft.com"));
        assert!(!bf.might_contain("evil-phishing-site.tk"));
    }

    #[test]
    fn test_serialize_deserialize() {
        let mut bf = BloomFilter::new();
        bf.insert("example.com");
        bf.insert("test.org");

        let bytes = bf.to_bytes();
        let count = bf.len();
        let restored = BloomFilter::from_bytes(bytes, count);

        assert!(restored.might_contain("example.com"));
        assert!(restored.might_contain("test.org"));
        assert!(!restored.might_contain("unknown.net"));
    }
}
