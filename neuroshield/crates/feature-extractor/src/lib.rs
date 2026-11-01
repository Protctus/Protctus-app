//! # Feature Extractor — 53 Lexical Features for Phishing Detection
//!
//! Extracts exactly 53 lexical features from a domain hostname string in O(L) time,
//! zero heap allocation on the hot path. Operates on byte slices.
//!
//! Feature order matches the training pipeline exactly.
//! Indices 0-45: Structural/lexical features
//! Indices 46-52: Dynamic features (brand, TLD rate, keyword) — left zeroed here,
//! filled by DynamicFeatureProvider.

use core::str::from_utf8;

const MAX_DOMAIN_LEN: usize = 253;
const VOWELS: &[u8] = b"aeiou";
const CONSONANTS: &[u8] = b"bcdfghjklmnpqrstvwxyz";
const RARE_CHARS: &[u8] = b"_~!$&'()*+,;=";
const HOMOGLYPH_PREFIX: &[u8] = b"xn--";

/// Country-code TLDs (ISO 3166-1 alpha-2).
const COUNTRY_TLDS: &[&[u8]] = &[
    b"ac", b"ad", b"ae", b"af", b"ag", b"ai", b"al", b"am", b"ao", b"aq", b"ar", b"as", b"at",
    b"au", b"aw", b"ax", b"az", b"ba", b"bb", b"bd", b"be", b"bf", b"bg", b"bh", b"bi", b"bj",
    b"bl", b"bm", b"bn", b"bo", b"bq", b"br", b"bs", b"bt", b"bv", b"bw", b"by", b"bz", b"ca",
    b"cc", b"cd", b"cf", b"cg", b"ch", b"ci", b"ck", b"cl", b"cm", b"cn", b"co", b"cr", b"cu",
    b"cv", b"cw", b"cx", b"cy", b"cz", b"de", b"dj", b"dk", b"dm", b"do", b"dz", b"ec", b"ee",
    b"eg", b"eh", b"er", b"es", b"et", b"eu", b"fi", b"fj", b"fk", b"fm", b"fo", b"fr", b"ga",
    b"gb", b"gd", b"ge", b"gf", b"gg", b"gh", b"gi", b"gl", b"gm", b"gn", b"gp", b"gq", b"gr",
    b"gs", b"gt", b"gu", b"gw", b"gy", b"hk", b"hm", b"hn", b"hr", b"ht", b"hu", b"id", b"ie",
    b"il", b"im", b"in", b"io", b"iq", b"ir", b"is", b"it", b"je", b"jm", b"jo", b"jp", b"ke",
    b"kg", b"kh", b"ki", b"km", b"kn", b"kp", b"kr", b"kw", b"ky", b"kz", b"la", b"lb", b"lc",
    b"li", b"lk", b"lr", b"ls", b"lt", b"lu", b"lv", b"ly", b"ma", b"mc", b"md", b"me", b"mf",
    b"mg", b"mh", b"mk", b"ml", b"mm", b"mn", b"mo", b"mp", b"mq", b"mr", b"ms", b"mt", b"mu",
    b"mv", b"mw", b"mx", b"my", b"mz", b"na", b"nc", b"ne", b"nf", b"ng", b"ni", b"nl", b"no",
    b"np", b"nr", b"nu", b"nz", b"om", b"pa", b"pe", b"pf", b"pg", b"ph", b"pk", b"pl", b"pm",
    b"pn", b"pr", b"ps", b"pt", b"pw", b"py", b"qa", b"re", b"ro", b"rs", b"ru", b"rw", b"sa",
    b"sb", b"sc", b"sd", b"se", b"sg", b"sh", b"si", b"sj", b"sk", b"sl", b"sm", b"sn", b"so",
    b"sr", b"ss", b"st", b"sv", b"sx", b"sy", b"sz", b"tc", b"td", b"tf", b"tg", b"th", b"tj",
    b"tk", b"tl", b"tm", b"tn", b"to", b"tr", b"tt", b"tv", b"tw", b"tz", b"ua", b"ug", b"uk",
    b"us", b"uy", b"uz", b"va", b"vc", b"ve", b"vg", b"vi", b"vn", b"vu", b"wf", b"ws", b"ye",
    b"yt", b"za", b"zm", b"zw",
];

/// Free hosting / shared-domain platforms (PSL private domains).
const FREE_HOSTING_SUFFIXES: &[&[u8]] = &[
    b"blogspot.com", b"wordpress.com", b"weebly.com", b"wixsite.com",
    b"000webhostapp.com", b"infinityfreeapp.com", b"rf.gd",
    b"herokuapp.com", b"vercel.app", b"netlify.app", b"pages.dev",
    b"web.app", b"firebaseapp.com", b"appspot.com",
    b"github.io", b"gitlab.io", b"surge.sh", b"glitch.me",
    b"replit.app", b"repl.co", b"onrender.com", b"railway.app",
    b"fly.dev", b"deno.dev", b"workers.dev", b"r2.dev",
    b"azurewebsites.net", b"azurestaticapps.net", b"cloudfront.net",
    b"amplifyapp.com", b"notion.site", b"webflow.io", b"carrd.co",
    b"framer.app", b"strikingly.com", b"sites.google.com",
    b"bit.ly", b"t.co", b"tinyurl.com", b"is.gd", b"goo.gl",
    b"rb.gy", b"cutt.ly", b"shorturl.at", b"rebrand.ly", b"tiny.cc",
];

/// Suspicious TLDs with risk weights.
const SUSPICIOUS_TLDS: &[(&[u8], f32)] = &[
    (b"tk", 0.25), (b"ml", 0.25), (b"ga", 0.25), (b"cf", 0.25), (b"gq", 0.25),
    (b"buzz", 0.20), (b"top", 0.20), (b"icu", 0.20), (b"xyz", 0.15),
    (b"click", 0.15), (b"link", 0.10), (b"work", 0.10), (b"date", 0.15),
    (b"racing", 0.15), (b"win", 0.15), (b"bid", 0.15), (b"stream", 0.15),
    (b"download", 0.15), (b"loan", 0.15), (b"trade", 0.10), (b"webcam", 0.15),
];

/// Known brands for impersonation detection (feature 46, 47, 51).
const BRAND_TOKENS: &[&[u8]] = &[
    b"paypal", b"apple", b"microsoft", b"google", b"amazon", b"facebook",
    b"netflix", b"chase", b"wellsfargo", b"citibank", b"bankofamerica",
    b"hsbc", b"barclays", b"santander", b"lloyds", b"natwest",
    b"americanexpress", b"visa", b"mastercard", b"dropbox", b"onedrive",
    b"icloud", b"linkedin", b"twitter", b"instagram", b"whatsapp",
    b"telegram", b"discord", b"steam", b"ebay", b"walmart", b"target",
];

/// Suspicious keywords for feature 50, 52.
const PHISHING_KEYWORDS: &[&[u8]] = &[
    b"login", b"signin", b"sign-in", b"logon", b"log-on", b"auth",
    b"authenticate", b"verify", b"verification", b"secure", b"security",
    b"account", b"update", b"confirm", b"confirmation", b"validate",
    b"validation", b"webscr", b"cmd", b"token", b"password", b"pwd",
    b"credential", b"payment", b"pay", b"invoice",
];

/// Result of feature extraction.
#[derive(Debug, Clone, Copy)]
pub struct FeatureVector {
    /// 53 features in model-expected order.
    pub values: [f32; 53],
}

/// Sanitize a domain string: strip scheme, www, path, query, fragment, port,
/// percent-decode, strip IPv6 brackets.
pub fn sanitize(domain: &[u8]) -> Vec<u8> {
    let mut d = domain.to_vec();
    d.make_ascii_lowercase();

    // Remove scheme
    if d.starts_with(b"http://") {
        d = d[7..].to_vec();
    } else if d.starts_with(b"https://") {
        d = d[8..].to_vec();
    }

    // Percent-decode
    d = percent_decode(&d);

    // Strip IPv6 brackets: [::1] -> ::1, [2001:db8::1]:443 -> 2001:db8::1
    if d.starts_with(b"[") {
        if let Some(close_idx) = d.iter().position(|&b| b == b']') {
            let mut inner = d[1..close_idx].to_vec();
            // Remove port after bracket if numeric
            if let Some(colon) = inner.iter().position(|&b| b == b':') {
                let after = &inner[colon + 1..];
                if !after.is_empty() && after.iter().all(|b| b.is_ascii_digit()) {
                    inner = inner[..colon].to_vec();
                }
            }
            d = inner;
        }
    }

    if d.starts_with(b"www.") {
        d = d[4..].to_vec();
    }

    // Remove path, query, fragment
    for sep in &[b'/', b'?', b'#'] {
        if let Some(idx) = d.iter().position(|&b| b == *sep) {
            d = d[..idx].to_vec();
            break;
        }
    }

    // Remove port (only if after-colon is purely numeric)
    if let Some(colon) = d.iter().rposition(|&b| b == b':') {
        let after = &d[colon + 1..];
        if !after.is_empty() && after.iter().all(|b| b.is_ascii_digit()) {
            d = d[..colon].to_vec();
        }
    }

    d = d.trim_end_matches(b'.').to_vec();

    d
}

/// Percent-decode a byte slice.
fn percent_decode(s: &[u8]) -> Vec<u8> {
    let mut result = Vec::with_capacity(s.len());
    let mut i = 0;
    while i < s.len() {
        if s[i] == b'%' && i + 2 < s.len() {
            let hi = hex_digit(s[i + 1]);
            let lo = hex_digit(s[i + 2]);
            if let (Some(h), Some(l)) = (hi, lo) {
                result.push((h << 4) | l);
                i += 3;
                continue;
            }
        }
        result.push(s[i]);
        i += 1;
    }
    result
}

fn hex_digit(c: u8) -> Option<u8> {
    match c {
        b'0'..=b'9' => Some(c - b'0'),
        b'a'..=b'f' => Some(c - b'a' + 10),
        b'A'..=b'F' => Some(c - b'A' + 10),
        _ => None,
    }
}

/// Shannon entropy of a byte slice (bits).
fn shannon_entropy(s: &[u8]) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    let len = s.len() as f32;
    let mut counts = [0i32; 256];
    for &c in s {
        counts[c as usize] += 1;
    }
    let mut entropy = 0.0f32;
    for &count in &counts {
        if count > 0 {
            let p = count as f32 / len;
            entropy -= p * p.ln();
        }
    }
    entropy / 2.0f32.ln() // convert to bits
}

/// Count digits in a byte slice.
fn count_digits(s: &[u8]) -> u32 {
    s.iter().filter(|b| b.is_ascii_digit()).count() as u32
}

/// Count vowels in a byte slice.
fn count_vowels(s: &[u8]) -> u32 {
    s.iter().filter(|c| VOWELS.contains(c)).count() as u32
}

/// Count consonants in a byte slice.
fn count_consonants(s: &[u8]) -> u32 {
    s.iter()
        .filter(|c| {
            let lc = c.to_ascii_lowercase();
            lc.is_ascii_alphabetic() && !VOWELS.contains(&lc)
        })
        .count() as u32
}

/// Max consecutive consonant run.
fn max_consonant_run(s: &[u8]) -> u32 {
    let mut max_run = 0u32;
    let mut cur_run = 0u32;
    for &c in s {
        let lc = c.to_ascii_lowercase();
        if lc.is_ascii_alphabetic() && !VOWELS.contains(&lc) {
            cur_run += 1;
            max_run = max_run.max(cur_run);
        } else {
            cur_run = 0;
        }
    }
    max_run
}

/// Count rare characters.
fn count_rare_chars(s: &[u8]) -> u32 {
    s.iter().filter(|c| RARE_CHARS.contains(c)).count() as u32
}

/// Count digit sequences (contiguous runs of digits).
fn count_digit_sequences(s: &[u8]) -> u32 {
    let mut count = 0u32;
    let mut in_seq = false;
    for &c in s {
        if c.is_ascii_digit() {
            if !in_seq {
                count += 1;
                in_seq = true;
            }
        } else {
            in_seq = false;
        }
    }
    count
}

/// Check if domain is a raw IPv4 address.
fn is_raw_ip(domain: &[u8]) -> bool {
    let parts: Vec<&[u8]> = domain.split(|&b| b == b'.').collect();
    if parts.len() != 4 {
        return false;
    }
    parts.iter().all(|p| {
        if p.is_empty() || p.len() > 3 {
            return false;
        }
        if p.len() > 1 && p[0] == b'0' {
            return false;
        }
        p.iter().all(|b| b.is_ascii_digit())
            && p.iter()
                .collect::<Vec<_>>()
                .iter()
                .map(|&b| (b - b'0') as u32)
                .fold(0u32, |acc, d| acc * 10 + d)
                <= 255
    })
}

/// Check if domain is hosted on free/shared hosting.
fn is_free_hosting(domain: &[u8]) -> bool {
    FREE_HOSTING_SUFFIXES.iter().any(|suffix| {
        domain == *suffix || domain.ends_with(suffix)
    })
}

/// Check if TLD is a country-code TLD.
fn is_country_tld(tld: &[u8]) -> bool {
    COUNTRY_TLDS.iter().any(|&t| t == tld)
}

/// Check if TLD is suspicious.
fn tld_risk(tld: &[u8]) -> f32 {
    SUSPICIOUS_TLDS
        .iter()
        .find(|(t, _)| *t == tld)
        .map(|(_, r)| *r)
        .unwrap_or(0.0)
}

/// Check if SLD contains any brand token.
fn sld_has_brand(sld: &[u8]) -> bool {
    BRAND_TOKENS.iter().any(|brand| sld.contains(brand))
}

/// Check if subdomain contains any brand token.
fn subdomain_has_brand(subdomain: &[u8]) -> bool {
    BRAND_TOKENS.iter().any(|brand| subdomain.contains(brand))
}

/// Check if SLD contains any keyword.
fn sld_has_keyword(sld: &[u8]) -> bool {
    PHISHING_KEYWORDS.iter().any(|kw| sld.contains(kw))
}

/// Check if subdomain contains any keyword.
fn subdomain_has_keyword(subdomain: &[u8]) -> bool {
    PHISHING_KEYWORDS.iter().any(|kw| subdomain.contains(kw))
}

/// DGA score based on consonant-vowel transition frequency.
fn dga_score(s: &[u8]) -> f32 {
    if s.len() < 3 {
        return 0.0;
    }
    let mut transitions = 0u32;
    let mut prev_is_vowel: Option<bool> = None;
    for &c in s {
        let lc = c.to_ascii_lowercase();
        let is_vowel = VOWELS.contains(&lc);
        if let Some(prev) = prev_is_vowel {
            if is_vowel != prev {
                transitions += 1;
            }
        }
        prev_is_vowel = Some(is_vowel);
    }
    let letter_count = s.iter().filter(|c| c.is_ascii_alphabetic()).count() as u32;
    if letter_count < 2 {
        return 0.0;
    }
    let transition_rate = transitions as f32 / (letter_count - 1) as f32;
    let entropy = shannon_entropy(s);
    ((1.0 - transition_rate) * 0.5 + (entropy / 5.0).min(1.0) * 0.5)
}

/// Character transition variance.
fn char_transition_variance(s: &[u8]) -> f32 {
    if s.len() < 2 {
        return 0.0;
    }
    let transitions: Vec<f32> = s
        .windows(2)
        .map(|w| (w[1] as i32 - w[0] as i32) as f32)
        .collect();
    if transitions.is_empty() {
        return 0.0;
    }
    let mean = transitions.iter().sum::<f32>() / transitions.len() as f32;
    let variance = transitions
        .iter()
        .map(|t| (t - mean).powi(2))
        .sum::<f32>()
        / transitions.len() as f32;
    variance
}

/// Jaccard similarity between subdomain and SLD character sets (36-bit mask).
fn subdomain_sld_similarity(subdomain: &[u8], sld: &[u8]) -> f32 {
    if subdomain.is_empty() || sld.is_empty() {
        return 0.0;
    }
    let mut mask_a: u64 = 0;
    let mut mask_b: u64 = 0;
    for &c in subdomain {
        let lc = c.to_ascii_lowercase();
        if let Some(bit) = char_to_bit(lc) {
            mask_a |= 1u64 << bit;
        }
    }
    for &c in sld {
        let lc = c.to_ascii_lowercase();
        if let Some(bit) = char_to_bit(lc) {
            mask_b |= 1u64 << bit;
        }
    }
    let intersection = (mask_a & mask_b).count_ones() as f32;
    let union = (mask_a | mask_b).count_ones() as f32;
    if union == 0.0 {
        0.0
    } else {
        intersection / union
    }
}

/// Map alphanumeric char to bit position 0-35.
fn char_to_bit(c: u8) -> Option<u32> {
    match c {
        b'0'..=b'9' => Some((c - b'0') as u32),
        b'a'..=b'z' => Some(10 + (c - b'a') as u32),
        _ => None,
    }
}

/// Extract all 53 features from a domain string.
///
/// Features 46-52 are left as 0.0 and should be filled by the caller
/// with dynamic features (brand, TLD rate, keyword).
pub fn extract_features(domain: &[u8]) -> [f32; 53] {
    let clean = sanitize(domain);
    if clean.is_empty() {
        return [0.0f32; 53];
    }

    let parts: Vec<&[u8]> = clean.split(|&b| b == b'.').collect();
    let tld = if parts.len() >= 2 {
        parts.last().unwrap()
    } else {
        &clean[..]
    };
    let sld = if parts.len() >= 2 {
        parts[parts.len() - 2]
    } else {
        &clean[..]
    };
    let subdomain_parts: Vec<&[u8]> = if parts.len() >= 3 {
        parts[..parts.len() - 2].to_vec()
    } else {
        Vec::new()
    };
    let subdomain = subdomain_parts.join(".");
    let domain_depth = parts.len() as f32;

    let mut features = [0.0f32; 53];

    // ── Basic domain features ──
    features[0] = clean.len() as f32; // domain_length
    features[1] = (parts.len() as i32 - 2).max(0) as f32; // subdomain_count
    features[2] = shannon_entropy(&clean); // domain_entropy
    features[3] = digit_ratio(&clean); // digit_ratio
    features[4] = alpha_ratio(&clean); // alpha_ratio
    features[5] = vowel_ratio(&clean); // vowel_ratio
    features[6] = count_digits(&clean) as f32; // num_digits
    features[7] = clean.iter().filter(|c| **c == b'.').count() as f32; // num_dots
    features[8] = clean
        .iter()
        .filter(|c| **c == b'-')
        .count() as f32; // num_hyphens
    features[9] = tld.len() as f32; // tld_length
    features[10] = max_consonant_run(&clean) as f32; // max_consonant_run
    features[11] = if clean.starts_with(b"xn--") || clean.contains(b".xn--") {
        1.0
    } else {
        0.0
    }; // has_punycode
    features[12] = if is_raw_ip(&clean) { 1.0 } else { 0.0 }; // is_raw_ip
    features[13] = consonant_ratio(&clean); // consonant_ratio
    features[14] = count_consonants(&clean) as f32; // consonant_count
    features[15] = count_vowels(&clean) as f32; // vowel_count

    // ── Subdomain features ──
    let subdomain_lengths: Vec<usize> = subdomain_parts.iter().map(|p| p.len()).collect();
    features[16] = subdomain_lengths.iter().max().unwrap_or(&0) as f32; // longest_subdomain_len
    features[17] = if subdomain_lengths.is_empty() {
        0.0
    } else {
        subdomain_lengths.iter().sum::<usize>() as f32 / subdomain_lengths.len() as f32
    }; // average_subdomain_len

    // ── SLD features ──
    features[18] = sld.len() as f32; // sld_length
    features[19] = shannon_entropy(sld); // sld_entropy
    features[20] = digit_ratio(sld); // sld_digit_ratio
    features[21] = sld.chars().filter(|c| *c == b'-').count() as f32; // sld_hyphen_count

    // ── Ratio features ──
    features[22] = if clean.len() > 0 {
        subdomain.len() as f32 / clean.len() as f32
    } else {
        0.0
    }; // subdomain_length_ratio
    features[23] = if is_free_hosting(&clean) { 1.0 } else { 0.0 }; // is_free_hosting
    features[24] = if is_country_tld(tld) { 1.0 } else { 0.0 }; // is_country_tld
    features[25] = if clean.len() > 0 {
        clean.iter().filter(|c| **c == b'.').count() as f32 / clean.len() as f32
    } else {
        0.0
    }; // dot_ratio
    features[26] = if clean.len() > 0 {
        clean.iter().filter(|c| **c == b'-').count() as f32 / clean.len() as f32
    } else {
        0.0
    }; // hyphen_ratio
    features[27] = if clean.contains(b"--") { 1.0 } else { 0.0 }; // has_double_hyphen
    features[28] = if count_digits(&clean) > 0 { 1.0 } else { 0.0 }; // has_numbers
    features[29] = if subdomain_parts.is_empty() { 0.0 } else { 1.0 }; // has_subdomains

    // ── SLD character features ──
    features[30] = count_vowels(sld) as f32; // sld_vowel_count
    features[31] = count_consonants(sld) as f32; // sld_consonant_count
    features[32] = if sld.first().map_or(false, |c| c.is_ascii_digit()) {
        1.0
    } else {
        0.0
    }; // sld_starts_with_digit
    features[33] = dga_score(sld); // sld_dga_score
    features[34] = count_digits(sld) as f32; // sld_digit_count
    features[35] = count_rare_chars(sld) as f32; // sld_rare_char_count
    features[36] = count_digit_sequences(sld) as f32; // sld_digit_seq_count
    features[37] = max_consonant_run(sld) as f32; // sld_max_consonant_run

    // ── Subdomain structural features ──
    features[38] = digit_ratio(&subdomain); // subdomain_digit_ratio
    features[39] = subdomain_parts.len() as f32; // subdomain_token_count
    features[40] = subdomain
        .chars()
        .filter(|c| *c == b'-')
        .count() as f32; // subdomain_hyphen_count
    features[41] = shannon_entropy(&subdomain); // subdomain_entropy
    features[42] = if shannon_entropy(sld) > 0.0 {
        shannon_entropy(&subdomain) / shannon_entropy(sld)
    } else {
        0.0
    }; // entropy_ratio
    features[43] = subdomain_sld_similarity(&subdomain, sld); // subdomain_sld_similarity
    features[44] = char_transition_variance(&clean); // char_transition_variance
    features[45] = domain_depth; // domain_depth

    // ── Dynamic features (46-52) ──
    // Zeroed here; filled by DynamicFeatureProvider
    for i in 46..=52 {
        features[i] = 0.0;
    }

    features
}

/// digit_ratio helper
fn digit_ratio(s: &[u8]) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    count_digits(s) as f32 / s.len() as f32
}

/// alpha_ratio helper
fn alpha_ratio(s: &[u8]) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    s.iter()
        .filter(|c| c.is_ascii_alphabetic())
        .count() as f32
        / s.len() as f32
}

/// vowel_ratio helper
fn vowel_ratio(s: &[u8]) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    count_vowels(s) as f32 / s.len() as f32
}

/// consonant_ratio helper
fn consonant_ratio(s: &[u8]) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    count_consonants(s) as f32 / s.len() as f32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_feature_count() {
        let f = extract_features(b"google.com");
        assert_eq!(f.len(), 53);
    }

    #[test]
    fn test_raw_ip_detection() {
        assert!(is_raw_ip(b"192.168.1.1"));
        assert!(!is_raw_ip(b"google.com"));
        assert!(!is_raw_ip(b"192.168.001.001")); // leading zeros rejected
        assert!(!is_raw_ip(b"256.1.1.1")); // invalid octet
    }

    #[test]
    fn test_punycode_detection() {
        let f = extract_features(b"xn--pple-43d.com");
        assert_eq!(f[11], 1.0);
    }

    #[test]
    fn test_free_hosting() {
        assert!(is_free_hosting(b"blogspot.com"));
        assert!(is_free_hosting(b"evil.blogspot.com"));
        assert!(!is_free_hosting(b"google.com"));
    }

    #[test]
    fn test_all_features_finite() {
        let f = extract_features(b"login-paypal.suspicious-site.tk");
        for &v in &f {
            assert!(v.is_finite());
            assert!(v >= 0.0);
        }
    }

    #[test]
    fn test_empty_domain() {
        let f = extract_features(b"");
        for &v in &f {
            assert_eq!(v, 0.0);
        }
    }
}
