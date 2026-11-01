//! # Heuristic Layer — L1 Deterministic Detection
//!
//! Fast, deterministic heuristics that run before any ML call.
//! Returns a score in [0.0, 1.0] where higher = more suspicious.

use feature_extractor::{extract_features, is_free_hosting, is_raw_ip, sanitize};

/// Result of heuristic analysis.
#[derive(Debug, Clone)]
pub struct HeuristicResult {
    /// Score in [0.0, 1.0]. Higher = more suspicious.
    pub score: f32,
    /// Triggered indicators for explanation.
    pub indicators: Vec<String>,
}

/// Analyze a domain and return a heuristic score.
pub fn analyze(domain: &[u8]) -> HeuristicResult {
    let clean = sanitize(domain);
    if clean.is_empty() {
        return HeuristicResult {
            score: 0.0,
            indicators: Vec::new(),
        };
    }

    let mut score = 0.0f32;
    let mut indicators = Vec::new();

    let domain_len = clean.len() as f32;

    // 1. Domain length
    if domain_len > 50.0 {
        score += 0.10;
        indicators.push(format!("Unusually long domain ({} chars)", domain_len as usize));
    }

    // 2. Subdomain count
    let subdomain_count = clean.split(|&b| b == b'.').count() as f32 - 2.0;
    if subdomain_count >= 3.0 {
        score += 0.15;
        indicators.push(format!(
            "Deep subdomain nesting ({} levels)",
            subdomain_count as usize
        ));
    }

    // 3. Shannon entropy
    let entropy = shannon_entropy(&clean);
    if entropy > 4.2 {
        score += 0.15;
        indicators.push(format!("High domain entropy ({:.1})", entropy));
    }

    // 4. Digit ratio
    let digit_ratio = count_digits(&clean) as f32 / domain_len.max(1.0);
    if digit_ratio > 0.3 {
        score += 0.15;
        indicators.push(format!("High digit ratio ({:.0}%)", digit_ratio * 100.0));
    }

    // 5. Consonant-vowel ratio
    let cv_ratio = consonant_vowel_ratio(&clean);
    if cv_ratio > 4.0 || cv_ratio < 0.3 {
        score += 0.10;
        indicators.push(format!("Unusual consonant-vowel ratio ({:.1})", cv_ratio));
    }

    // 6. Max consonant run
    let max_consonant_run = max_consonant_run(&clean);
    if max_consonant_run >= 5 {
        score += 0.10;
        indicators.push(format!("Long consonant sequence ({} chars)", max_consonant_run));
    }

    // 7. Punycode detection
    if clean.contains(b"xn--") {
        score += 0.15;
        indicators.push("Punycode/IDN domain (potential homograph)".to_string());
    }

    // 8. Double hyphen
    if clean.contains(b"--") {
        score += 0.10;
        indicators.push("Double-hyphen pattern".to_string());
    }

    // 9. TLD risk
    let tld = clean.split(|&b| b == b'.').last().unwrap_or(b"");
    let tld_risk = tld_risk_score(tld);
    if tld_risk > 0.0 {
        score += tld_risk;
        indicators.push(format!("High-risk TLD (.{})", std::str::from_utf8(tld).unwrap_or("")));
    }

    // 10. Free hosting
    if is_free_hosting(&clean) {
        score += 0.15;
        indicators.push("Free/shared hosting platform".to_string());
    }

    // 11. Raw IP address
    if is_raw_ip(&clean) {
        score += 0.30;
        indicators.push("Raw IP address used".to_string());
    }

    // 11b. Rare characters
    let rare_char_count = count_rare_chars(&clean);
    if rare_char_count >= 2 {
        score += 0.10;
        indicators.push(format!("Rare characters in domain ({})", rare_char_count));
    }

    // 12. DGA score
    let dga = dga_score(&clean);
    if dga > 0.7 {
        score += 0.15;
        indicators.push("SLD resembles DGA-generated domain".to_string());
    }

    score = score.min(1.0);

    HeuristicResult { score, indicators }
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
    entropy / 2.0f32.ln()
}

/// Count digits in a byte slice.
fn count_digits(s: &[u8]) -> u32 {
    s.iter().filter(|b| b.is_ascii_digit()).count() as u32
}

/// Consonant-vowel ratio.
fn consonant_vowel_ratio(s: &[u8]) -> f32 {
    let vowels: &[u8] = b"aeiou";
    let mut vowels = 0u32;
    let mut consonants = 0u32;
    for &c in s {
        let lc = c.to_ascii_lowercase();
        if lc.is_ascii_alphabetic() {
            if vowels.contains(&lc) {
                vowels += 1;
            } else {
                consonants += 1;
            }
        }
    }
    if vowels == 0 {
        if consonants == 0 {
            return 0.0;
        }
        return consonants as f32;
    }
    consonants as f32 / vowels as f32
}

/// Max consecutive consonant run.
fn max_consonant_run(s: &[u8]) -> u32 {
    let vowels: &[u8] = b"aeiou";
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

/// Suspicious TLDs with risk weights.
const SUSPICIOUS_TLDS: &[(&[u8], f32)] = &[
    (b"tk", 0.25),
    (b"ml", 0.25),
    (b"ga", 0.25),
    (b"cf", 0.25),
    (b"gq", 0.25),
    (b"buzz", 0.20),
    (b"top", 0.20),
    (b"icu", 0.20),
    (b"xyz", 0.15),
    (b"click", 0.15),
    (b"link", 0.10),
    (b"work", 0.10),
    (b"date", 0.15),
    (b"racing", 0.15),
    (b"win", 0.15),
    (b"bid", 0.15),
    (b"stream", 0.15),
    (b"download", 0.15),
    (b"loan", 0.15),
    (b"trade", 0.10),
    (b"webcam", 0.15),
];

/// Free hosting / shared-domain platforms.
const FREE_HOSTING_SUFFIXES: &[&[u8]] = &[
    b"blogspot.com",
    b"wordpress.com",
    b"weebly.com",
    b"wixsite.com",
    b"000webhostapp.com",
    b"infinityfreeapp.com",
    b"rf.gd",
    b"herokuapp.com",
    b"vercel.app",
    b"netlify.app",
    b"pages.dev",
    b"web.app",
    b"firebaseapp.com",
    b"appspot.com",
    b"github.io",
    b"gitlab.io",
    b"surge.sh",
    b"glitch.me",
    b"replit.app",
    b"repl.co",
    b"onrender.com",
    b"railway.app",
    b"fly.dev",
    b"deno.dev",
    b"workers.dev",
    b"r2.dev",
    b"azurewebsites.net",
    b"azurestaticapps.net",
    b"cloudfront.net",
    b"amplifyapp.com",
    b"notion.site",
    b"webflow.io",
    b"carrd.co",
    b"framer.app",
    b"strikingly.com",
    b"sites.google.com",
    b"bit.ly",
    b"t.co",
    b"tinyurl.com",
    b"is.gd",
    b"goo.gl",
    b"rb.gy",
    b"cutt.ly",
    b"shorturl.at",
    b"rebrand.ly",
    b"tiny.cc",
];

/// Rare characters uncommon in legitimate domains.
const RARE_CHARS: &[u8] = b"_~!$&'()*+,;=";

/// Vowels.
const VOWELS: &[u8] = b"aeiou";

/// Consonants.
const CONSONANTS: &[u8] = b"bcdfghjklmnpqrstvwxyz";

/// TLD risk score lookup.
fn tld_risk_score(tld: &[u8]) -> f32 {
    SUSPICIOUS_TLDS
        .iter()
        .find(|(t, _)| *t == tld)
        .map(|(_, r)| *r)
        .unwrap_or(0.0)
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_raw_ip() {
        assert!(is_raw_ip(b"192.168.1.1"));
        assert!(!is_raw_ip(b"google.com"));
        assert!(!is_raw_ip(b"192.168.001.001"));
    }

    #[test]
    fn test_free_hosting() {
        assert!(is_free_hosting(b"blogspot.com"));
        assert!(is_free_hosting(b"evil.blogspot.com"));
        assert!(!is_free_hosting(b"google.com"));
    }

    #[test]
    fn test_heuristic_phishing() {
        let result = analyze(b"login-paypal.suspicious-site.tk");
        assert!(result.score > 0.5);
        assert!(!result.indicators.is_empty());
    }

    #[test]
    fn test_heuristic_benign() {
        let result = analyze(b"google.com");
        assert!(result.score < 0.3);
    }
}
