//! # Feature Extraction
//!
//! Extracts 53 lexical features from a domain hostname string.
//! These features match the production model training pipeline exactly.
//!
//! Feature indices:
//!   0: domain_length
//!   1: subdomain_count
//!   2: domain_entropy
//!   3: digit_ratio
//!   4: alpha_ratio
//!   5: vowel_ratio
//!   6: num_digits
//!   7: num_dots
//!   8: num_hyphens
//!   9: tld_length
//!   10: max_consonant_run
//!   11: has_punycode
//!   12: is_raw_ip
//!   13: consonant_ratio
//!   14: consonant_count
//!   15: vowel_count
//!   16: longest_subdomain_len
//!   17: average_subdomain_len
//!   18: sld_length
//!   19: sld_entropy
//!   20: sld_digit_ratio
//!   21: sld_hyphen_count
//!   22: subdomain_length_ratio
//!   23: is_free_hosting
//!   24: is_country_tld
//!   25: dot_ratio
//!   26: hyphen_ratio
//!   27: has_double_hyphen
//!   28: has_numbers
//!   29: has_subdomains
//!   30: sld_vowel_count
//!   31: sld_consonant_count
//!   32: sld_starts_with_digit
//!   33: sld_dga_score
//!   34: sld_digit_count
//!   35: sld_rare_char_count
//!   36: sld_digit_seq_count
//!   37: sld_max_consonant_run
//!   38: subdomain_digit_ratio
//!   39: subdomain_token_count
//!   40: subdomain_hyphen_count
//!   41: subdomain_entropy
//!   42: entropy_ratio
//!   43: subdomain_sld_similarity
//!   44: char_transition_variance
//!   45: domain_depth
//!   46-52: dynamic features (set to 0 by this extractor; filled by caller)

use crate::tree::TreeModel;

const VOWELS: &[u8] = b"aeiou";
const RARE_CHARS: &[u8] = b"_~!$&'()*+,;=";

/// Extract all 53 features from a domain string.
/// Features 46-52 are left as 0.0 and should be filled by the caller
/// with dynamic features (brand_in_sld, brand_in_bad_tld, etc.).
pub fn extract_features(domain: &str) -> [f32; 53] {
    let mut features = [0.0f32; 53];

    let clean = sanitize(domain);
    if clean.is_empty() {
        return features;
    }

    let parts: Vec<&str> = clean.split('.').collect();
    let tld = if parts.len() >= 2 { parts.last().unwrap() } else { "" };
    let sld = if parts.len() >= 2 { parts[parts.len() - 2] } else { clean };
    let subdomain_parts: Vec<&str> = if parts.len() >= 3 {
        parts[..parts.len() - 2].to_vec()
    } else {
        Vec::new()
    };
    let subdomain = subdomain_parts.join(".");
    let domain_depth = parts.len() as f32;

    // ── Basic domain features ──
    features[0] = clean.len() as f32;                                           // domain_length
    features[1] = (parts.len() as i32 - 2).max(0) as f32;                     // subdomain_count
    features[2] = shannon_entropy(&clean);                                     // domain_entropy
    features[3] = digit_ratio(&clean);                                         // digit_ratio
    features[4] = alpha_ratio(&clean);                                         // alpha_ratio
    features[5] = vowel_ratio(&clean);                                         // vowel_ratio
    features[6] = count_digits(&clean) as f32;                                 // num_digits
    features[7] = clean.chars().filter(|c| *c == '.').count() as f32;          // num_dots
    features[8] = clean.chars().filter(|c| *c == '-').count() as f32;          // num_hyphens
    features[9] = tld.len() as f32;                                            // tld_length
    features[10] = max_consonant_run(&clean) as f32;                           // max_consonant_run
    features[11] = if clean.contains("xn--") { 1.0 } else { 0.0 };             // has_punycode
    features[12] = if is_raw_ip(&clean) { 1.0 } else { 0.0 };                  // is_raw_ip
    features[13] = consonant_ratio(&clean);                                    // consonant_ratio
    features[14] = count_consonants(&clean) as f32;                            // consonant_count
    features[15] = count_vowels(&clean) as f32;                                // vowel_count

    // ── Subdomain features ──
    let subdomain_lengths: Vec<usize> = subdomain_parts.iter().map(|p| p.len()).collect();
    features[16] = subdomain_lengths.iter().max().unwrap_or(&0) as f32;        // longest_subdomain_len
    features[17] = if subdomain_lengths.is_empty() {
        0.0
    } else {
        subdomain_lengths.iter().sum::<usize>() as f32 / subdomain_lengths.len() as f32
    };                                                                           // average_subdomain_len

    // ── SLD features ──
    features[18] = sld.len() as f32;                                            // sld_length
    features[19] = shannon_entropy(sld);                                       // sld_entropy
    features[20] = digit_ratio(sld);                                           // sld_digit_ratio
    features[21] = sld.chars().filter(|c| *c == '-').count() as f32;           // sld_hyphen_count

    // ── Ratio features ──
    features[22] = if clean.len() > 0 {
        subdomain.len() as f32 / clean.len() as f32
    } else {
        0.0
    };                                                                          // subdomain_length_ratio
    features[23] = if is_free_hosting(&clean) { 1.0 } else { 0.0 };             // is_free_hosting
    features[24] = if is_country_tld(tld) { 1.0 } else { 0.0 };                 // is_country_tld
    features[25] = if clean.len() > 0 {
        clean.chars().filter(|c| *c == '.').count() as f32 / clean.len() as f32
    } else {
        0.0
    };                                                                          // dot_ratio
    features[26] = if clean.len() > 0 {
        clean.chars().filter(|c| *c == '-').count() as f32 / clean.len() as f32
    } else {
        0.0
    };                                                                          // hyphen_ratio
    features[27] = if clean.contains("--") { 1.0 } else { 0.0 };                // has_double_hyphen
    features[28] = if count_digits(&clean) > 0 { 1.0 } else { 0.0 };            // has_numbers
    features[29] = if subdomain_parts.is_empty() { 0.0 } else { 1.0 };          // has_subdomains

    // ── SLD character features ──
    features[30] = count_vowels(sld) as f32;                                    // sld_vowel_count
    features[31] = count_consonants(sld) as f32;                                // sld_consonant_count
    features[32] = if sld.chars().next().map_or(false, |c| c.is_ascii_digit()) {
        1.0
    } else {
        0.0
    };                                                                          // sld_starts_with_digit
    features[33] = dga_score(sld);                                              // sld_dga_score
    features[34] = count_digits(sld) as f32;                                    // sld_digit_count
    features[35] = count_rare_chars(sld) as f32;                                // sld_rare_char_count
    features[36] = count_digit_sequences(sld) as f32;                           // sld_digit_seq_count
    features[37] = max_consonant_run(sld) as f32;                               // sld_max_consonant_run

    // ── Subdomain structural features ──
    features[38] = digit_ratio(&subdomain);                                     // subdomain_digit_ratio
    features[39] = subdomain_parts.len() as f32;                                // subdomain_token_count
    features[40] = subdomain.chars().filter(|c| *c == '-').count() as f32;      // subdomain_hyphen_count
    features[41] = shannon_entropy(&subdomain);                                 // subdomain_entropy
    features[42] = if shannon_entropy(sld) > 0.0 {
        shannon_entropy(&subdomain) / shannon_entropy(sld)
    } else {
        0.0
    };                                                                          // entropy_ratio
    features[43] = subdomain_sld_similarity(&subdomain, sld);                   // subdomain_sld_similarity
    features[44] = char_transition_variance(&clean);                            // char_transition_variance
    features[45] = domain_depth;                                                // domain_depth

    // ── Dynamic features (46-52) ──
    // These are zeroed here and should be filled by the caller
    // with brand/TLD/keyword information from the dynamic feature provider.
    for i in 46..=52 {
        features[i] = 0.0;
    }

    features
}

/// Sanitize a domain string: strip scheme, www, path, query, fragment, port.
fn sanitize(domain: &str) -> String {
    let mut d = domain.trim().to_lowercase();

    // Remove scheme
    if d.starts_with("http://") {
        d = d[7..].to_string();
    } else if d.starts_with("https://") {
        d = d[8..].to_string();
    }

    // Percent-decode
    d = percent_decode(&d);

    // Strip IPv6 brackets
    if d.starts_with('[') && d.contains(']') {
        let end = d.find(']').unwrap_or(d.len());
        d = d[1..end].to_string();
        // Remove port after bracket
        if let Some(colon) = d.find(':') {
            let after = &d[colon + 1..];
            if after.chars().all(|c| c.is_ascii_digit()) {
                d = d[..colon].to_string();
            }
        }
    }

    if d.starts_with("www.") {
        d = d[4..].to_string();
    }

    // Remove path, query, fragment
    for sep in &['/', '?', '#'] {
        if let Some(idx) = d.find(*sep) {
            d = d[..idx].to_string();
            break;
        }
    }

    // Remove port (only if after-colon is purely numeric)
    if let Some(colon) = d.rfind(':') {
        let after = &d[colon + 1..];
        if after.chars().all(|c| c.is_ascii_digit()) {
            d = d[..colon].to_string();
        }
    }

    d = d.trim_end_matches('.').to_string();

    // IDN to ASCII (best effort - if IDNA fails, use raw)
    if let Ok(ascii) = idna::to_ascii(&d) {
        d = ascii.to_lowercase();
    }

    d
}

/// Percent-decode a string.
fn percent_decode(s: &str) -> String {
    let mut result = String::with_capacity(s.len());
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let hi = hex_digit(bytes[i + 1]);
            let lo = hex_digit(bytes[i + 2]);
            if hi.is_some() && lo.is_some() {
                result.push(((hi.unwrap() << 4) | lo.unwrap()) as u8);
                i += 3;
                continue;
            }
        }
        result.push(bytes[i] as char);
        i += 1;
    }
    result
}

/// Convert a hex character to its numeric value.
fn hex_digit(c: u8) -> Option<u8> {
    match c {
        b'0'..=b'9' => Some(c - b'0'),
        b'a'..=b'f' => Some(c - b'a' + 10),
        b'A'..=b'F' => Some(c - b'A' + 10),
        _ => None,
    }
}

/// Shannon entropy of a string.
fn shannon_entropy(s: &str) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    let len = s.len() as f32;
    let mut counts = [0i32; 128];
    let mut non_ascii = 0;
    for c in s.chars() {
        let code = c as u32;
        if code < 128 {
            counts[code as usize] += 1;
        } else {
            non_ascii += 1;
        }
    }
    let mut entropy = 0.0f32;
    for &count in &counts {
        if count > 0 {
            let p = count as f32 / len;
            entropy -= p * p.ln();
        }
    }
    if non_ascii > 0 {
        let p = non_ascii as f32 / len;
        entropy -= p * p.ln();
    }
    entropy / 2.0f32.ln()
}

/// Count digits in a string.
fn count_digits(s: &str) -> u32 {
    s.chars().filter(|c| c.is_ascii_digit()).count() as u32
}

/// Count vowels in a string.
fn count_vowels(s: &str) -> u32 {
    s.chars().filter(|c| "aeiou".contains(c.to_ascii_lowercase())).count() as u32
}

/// Count consonants in a string.
fn count_consonants(s: &str) -> u32 {
    let vowels: &[u8] = b"aeiou";
    s.chars()
        .filter(|c| {
            let lc = c.to_ascii_lowercase();
            lc.is_ascii_alphabetic() && !vowels.contains(&(lc as u8))
        })
        .count() as u32
}

/// Digit ratio.
fn digit_ratio(s: &str) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    count_digits(s) as f32 / s.len() as f32
}

/// Alpha ratio.
fn alpha_ratio(s: &str) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    s.chars().filter(|c| c.is_ascii_alphabetic()).count() as f32 / s.len() as f32
}

/// Vowel ratio.
fn vowel_ratio(s: &str) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    count_vowels(s) as f32 / s.len() as f32
}

/// Consonant ratio.
fn consonant_ratio(s: &str) -> f32 {
    if s.is_empty() {
        return 0.0;
    }
    count_consonants(s) as f32 / s.len() as f32
}

/// Maximum consecutive consonant run.
fn max_consonant_run(s: &str) -> u32 {
    let vowels: &[u8] = b"aeiou";
    let mut max_run = 0u32;
    let mut cur_run = 0u32;
    for c in s.chars() {
        let lc = c.to_ascii_lowercase();
        if lc.is_ascii_alphabetic() && !vowels.contains(&(lc as u8)) {
            cur_run += 1;
            max_run = max_run.max(cur_run);
        } else {
            cur_run = 0;
        }
    }
    max_run
}

/// Count rare characters.
fn count_rare_chars(s: &str) -> u32 {
    s.chars().filter(|c| RARE_CHARS.contains(&(c as u8))).count() as u32
}

/// Check if domain is a raw IPv4 address.
fn is_raw_ip(domain: &str) -> bool {
    let parts: Vec<&str> = domain.split('.').collect();
    if parts.len() != 4 {
        return false;
    }
    parts.iter().all(|p| {
        if p.is_empty() || p.len() > 3 {
            return false;
        }
        if p.len() > 1 && p.starts_with('0') {
            return false;
        }
        p.chars().all(|c| c.is_ascii_digit())
            && p.parse::<u32>().map_or(false, |v| v <= 255)
    })
}

/// Check if domain is on free/shared hosting.
fn is_free_hosting(domain: &str) -> bool {
    FREE_HOSTING_SUFFIXES.iter().any(|suffix| {
        domain == *suffix || domain.ends_with(&format!(".{}", suffix))
    })
}

/// DGA score based on consonant-vowel transition frequency.
fn dga_score(s: &str) -> f32 {
    let s: String = s.chars().filter(|c| c.is_ascii_alphabetic()).collect();
    if s.len() < 3 {
        return 0.0;
    }

    let vowels: &[u8] = b"aeiou";
    let mut transitions = 0u32;
    let mut prev_is_vowel: Option<bool> = None;
    for c in s.chars() {
        let lc = c.to_ascii_lowercase();
        let is_vowel = vowels.contains(&(lc as u8));
        if let Some(prev) = prev_is_vowel {
            if is_vowel != prev {
                transitions += 1;
            }
        }
        prev_is_vowel = Some(is_vowel);
    }

    let letter_count = s.chars().count() as u32;
    if letter_count < 2 {
        return 0.0;
    }

    let transition_rate = transitions as f32 / (letter_count - 1) as f32;
    let entropy = shannon_entropy(&s);

    ((1.0 - transition_rate) * 0.5 + (entropy / 5.0).min(1.0) * 0.5)
}

/// Jaccard similarity between subdomain and SLD character sets.
fn subdomain_sld_similarity(subdomain: &str, sld: &str) -> f32 {
    if subdomain.is_empty() || sld.is_empty() {
        return 0.0;
    }
    let set_a: std::collections::HashSet<char> = subdomain.chars().collect();
    let set_b: std::collections::HashSet<char> = sld.chars().collect();
    let intersection = set_a.intersection(&set_b).count();
    let union = set_a.union(&set_b).count();
    if union == 0 {
        0.0
    } else {
        intersection as f32 / union as f32
    }
}

/// Character transition variance.
fn char_transition_variance(s: &str) -> f32 {
    if s.len() < 2 {
        return 0.0;
    }
    let transitions: Vec<f32> = s.chars()
        .zip(s.chars().skip(1))
        .map(|(a, b)| (b as i32 - a as i32) as f32)
        .collect();
    if transitions.is_empty() {
        return 0.0;
    }
    let mean = transitions.iter().sum::<f32>() / transitions.len() as f32;
    let variance = transitions.iter()
        .map(|t| (t - mean).powi(2))
        .sum::<f32>() / transitions.len() as f32;
    variance
}

/// Check if TLD is a country-code TLD.
fn is_country_tld(tld: &str) -> bool {
    COUNTRY_TLDS.contains(&tld)
}

/// Country-code TLDs (ISO 3166-1 alpha-2).
const COUNTRY_TLDS: &[&str] = &[
    "ac","ad","ae","af","ag","ai","al","am","ao","aq","ar","as","at","au","aw","ax","az",
    "ba","bb","bd","be","bf","bg","bh","bi","bj","bl","bm","bn","bo","bq","br","bs","bt",
    "bv","bw","by","bz","ca","cc","cd","cf","cg","ch","ci","ck","cl","cm","cn","co","cr",
    "cu","cv","cw","cx","cy","cz","de","dj","dk","dm","do","dz","ec","ee","eg","eh","er",
    "es","et","eu","fi","fj","fk","fm","fo","fr","ga","gb","gd","ge","gf","gg","gh","gi",
    "gl","gm","gn","gp","gq","gr","gs","gt","gu","gw","gy","hk","hm","hn","hr","ht","hu",
    "id","ie","il","im","in","io","iq","ir","is","it","je","jm","jo","jp","ke","kg","kh",
    "ki","km","kn","kp","kr","kw","ky","kz","la","lb","lc","li","lk","lr","ls","lt","lu",
    "lv","ly","ma","mc","md","me","mf","mg","mh","mk","ml","mm","mn","mo","mp","mq","mr",
    "ms","mt","mu","mv","mw","mx","my","mz","na","nc","ne","nf","ng","ni","nl","no","np",
    "nr","nu","nz","om","pa","pe","pf","pg","ph","pk","pl","pm","pn","pr","ps","pt","pw",
    "py","qa","re","ro","rs","ru","rw","sa","sb","sc","sd","se","sg","sh","si","sj","sk",
    "sl","sm","sn","so","sr","ss","st","sv","sx","sy","sz","tc","td","tf","tg","th","tj",
    "tk","tl","tm","tn","to","tr","tt","tv","tw","tz","ua","ug","uk","us","uy","uz","va",
    "vc","ve","vg","vi","vn","vu","wf","ws","ye","yt","za","zm","zw",
];

/// Free hosting / shared-domain platforms.
const FREE_HOSTING_SUFFIXES: &[&str] = &[
    "blogspot.com", "wordpress.com", "weebly.com", "wixsite.com",
    "000webhostapp.com", "infinityfreeapp.com", "rf.gd",
    "herokuapp.com", "vercel.app", "netlify.app", "pages.dev",
    "web.app", "firebaseapp.com", "appspot.com",
    "github.io", "gitlab.io", "surge.sh", "glitch.me",
    "replit.app", "repl.co", "onrender.com", "railway.app",
    "fly.dev", "deno.dev", "workers.dev", "r2.dev",
    "azurewebsites.net", "azurestaticapps.net", "cloudfront.net",
    "amplifyapp.com", "notion.site", "webflow.io", "carrd.co",
    "framer.app", "strikingly.com", "sites.google.com",
    "bit.ly", "t.co", "tinyurl.com", "is.gd", "goo.gl",
    "rb.gy", "cutt.ly", "shorturl.at", "rebrand.ly", "tiny.cc",
];

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_features_count() {
        let f = extract_features("google.com");
        assert_eq!(f.len(), 53);
    }

    #[test]
    fn test_raw_ip_detection() {
        let f = extract_features("192.168.1.1");
        assert_eq!(f[12], 1.0); // is_raw_ip

        let f2 = extract_features("google.com");
        assert_eq!(f2[12], 0.0);
    }

    #[test]
    fn test_punycode_detection() {
        let f = extract_features("xn--pple-43d.com");
        assert_eq!(f[11], 1.0); // has_punycode
    }

    #[test]
    fn test_features_in_range() {
        let f = extract_features("login-paypal.suspicious-site.tk");
        for &val in &f {
            assert!(val >= 0.0 && val <= 100.0, "Feature out of range: {}", val);
        }
    }
}
