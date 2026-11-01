"""
Feature pipeline for NeuroShield phishing detection.

Computes all 53 lexical features matching the Kotlin LexicalFeatureExtractor
exactly, so training and production inference are consistent.

Also computes the 8 "dynamic" features that were previously zeroed:
  - has_brand_in_sld
  - brand_in_bad_tld
  - is_new_gtld
  - tld_phishing_rate
  - sld_has_keyword
  - subdomain_has_brand
  - subdomain_has_keyword
  (8th feature reserved)

These dynamic features require a training corpus to compute brand/TLD statistics.
"""
import math, re, os, json, sys, hashlib
from collections import Counter, defaultdict

# ── Kotlin-compatible feature extraction ──────────────────────────────

VOWELS = set('aeiou')
RARE_CHARS = set('_~!$&\'()*+,;=')

COUNTRY_TLDS = {
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
}

FREE_HOSTING_SUFFIXES = {
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
}

# Known phishing-targeted brands (for dynamic features)
BRANDS = {
    'paypal', 'apple', 'microsoft', 'google', 'amazon', 'facebook', 'netflix',
    'bank', 'chase', 'wellsfargo', 'citibank', 'bankofamerica', 'hsbc', 'barclays',
    'santander', 'lloyds', 'natwest', 'americanexpress', 'visa', 'mastercard',
    'dropbox', 'onedrive', 'icloud', 'linkedin', 'twitter', 'instagram',
    'whatsapp', 'telegram', 'discord', 'steam', 'epicgames', 'riotgames',
    'adobe', 'intuit', 'quickbooks', 'salesforce', 'zoom', 'slack',
    'dhl', 'fedex', 'ups', 'usps', 'amazon', 'ebay', 'etsy', 'walmart',
    'target', 'bestbuy', 'home depot', 'lowes', 'cvs', 'walgreens',
}

# Suspicious TLDs (high phishing rate historically)
SUSPICIOUS_TLDS = {'tk','ml','ga','cf','gq','buzz','top','icu','xyz','click',
                   'link','work','date','racing','win','bid','stream','download',
                   'loan','trade','webcam','country','kim','science','review',
                   'download','stream','racing','win','bid','loan','trade'}

# New gTLDs (recently introduced, higher risk)
NEW_GTLDS = {'xyz','online','site','vip','club','shop','store','tech','blog',
             'club','fun','party','bid','win','loan','date','review','science'}


def sanitize(domain):
    """Kotlin-compatible sanitization including percent-decoding and IPv6 brackets."""
    d = domain.strip().lower()
    if len(d) > 2048:
        d = d[:2048]
    for prefix in ('http://', 'https://'):
        if d.startswith(prefix):
            d = d[len(prefix):]
    # Percent-decode
    sb = []
    i = 0
    while i < len(d):
        c = d[i]
        if c == '%' and i + 2 < len(d):
            hi, lo = d[i+1], d[i+2]
            try:
                sb.append(chr(int(hi + lo, 16)))
                i += 3
                continue
            except ValueError:
                pass
        sb.append(c)
        i += 1
    d = ''.join(sb)
    # IPv6 brackets
    if d.startswith('[') and ']' in d:
        cb = d.index(']')
        d = d[1:cb]
        if ':' in d:
            after = d.split(':', 1)[1]
            if after.isdigit():
                d = d[:d.index(':', 1)]
    if d.startswith('www.'):
        d = d[4:]
    for sep in ('/', '?', '#'):
        idx = d.find(sep)
        if idx >= 0:
            d = d[:idx]
    # Remove port (only if after-colon is purely numeric)
    last_colon = d.rfind(':')
    if last_colon >= 0:
        after = d[last_colon + 1:]
        if after and all(ch.isdigit() for ch in after):
            d = d[:last_colon]
    d = d.rstrip('.')
    # IDN → ASCII
    try:
        import idna
        d = idna.encode(d).decode('ascii').lower()
    except Exception:
        try:
            d = d.encode('idna').decode('ascii').lower()
        except Exception:
            pass
    return d


def shannon_entropy(s):
    if not s:
        return 0.0
    length = float(len(s))
    counts = [0] * 128
    non_ascii = 0
    for ch in s:
        code = ord(ch)
        if code < 128:
            counts[code] += 1
        else:
            non_ascii += 1
    LN2 = 0.6931471805599453
    entropy = 0.0
    for count in counts:
        if count > 0:
            p = count / length
            entropy -= p * (math.log(p) / LN2)
    if non_ascii > 0:
        p = non_ascii / length
        entropy -= p * (math.log(p) / LN2)
    return entropy


def count_digits(s):
    return sum(1 for ch in s if ch.isdigit())


def count_vowels(s):
    return sum(1 for ch in s if ch.lower() in VOWELS)


def count_consonants(s):
    return sum(1 for ch in s if ch.lower().isalpha() and ch.lower() not in VOWELS)


def count_char(s, target):
    return sum(1 for ch in s if ch == target)


def count_rare_chars(s):
    return sum(1 for ch in s if ch in RARE_CHARS)


def digit_ratio(s):
    if not s:
        return 0.0
    return count_digits(s) / len(s)


def alpha_ratio(s):
    if not s:
        return 0.0
    return sum(1 for ch in s if ch.isalpha()) / len(s)


def vowel_ratio(s):
    if not s:
        return 0.0
    return count_vowels(s) / len(s)


def consonant_ratio(s):
    if not s:
        return 0.0
    return count_consonants(s) / len(s)


def max_consonant_run(s):
    max_run = cur = 0
    for ch in s:
        lc = ch.lower()
        if lc.isalpha() and lc not in VOWELS:
            cur += 1
            max_run = max(max_run, cur)
        else:
            cur = 0
    return max_run


def is_raw_ip(domain):
    parts = domain.split('.')
    if len(parts) == 4:
        all_valid = all(
            1 <= len(p) <= 3 and p.isdigit()
            and (len(p) == 1 or p[0] != '0')  # no leading zeros
            and 0 <= int(p) <= 255
            for p in parts
        )
        if all_valid:
            return True
    if ':' in domain:
        v6 = domain.strip()
        if all(c == ':' or c.isdigit() or c in 'abcdef' for c in v6):
            if v6.count(':') >= 2:
                return True
    return False


def is_free_hosting(domain):
    d = domain.lower()
    return any(d == s or d.endswith('.' + s) for s in FREE_HOSTING_SUFFIXES)


def count_digit_sequences(s):
    count = 0
    in_seq = False
    for ch in s:
        if ch.isdigit():
            if not in_seq:
                count += 1
                in_seq = True
        else:
            in_seq = False
    return count


def dga_score(s):
    if len(s) < 3:
        return 0.0
    transitions = 0
    prev_is_vowel = None
    for ch in s:
        if not ch.isalpha():
            continue
        is_vowel = ch.lower() in VOWELS
        if prev_is_vowel is not None and is_vowel != prev_is_vowel:
            transitions += 1
        prev_is_vowel = is_vowel
    letter_count = sum(1 for ch in s if ch.isalpha())
    if letter_count < 2:
        return 0.0
    transition_rate = transitions / float(letter_count - 1)
    entropy = shannon_entropy(s)
    return (1.0 - transition_rate) * 0.5 + min(entropy / 5.0, 1.0) * 0.5


def subdomain_sld_similarity(subdomain, sld):
    if not subdomain or not sld:
        return 0.0
    set_a = set(subdomain)
    set_b = set(sld)
    inter = len(set_a & set_b)
    union = len(set_a | set_b)
    return inter / union if union > 0 else 0.0


def char_transition_variance(s):
    if len(s) < 2:
        return 0.0
    transitions = [float(ord(s[i+1]) - ord(s[i])) for i in range(len(s)-1)]
    mean = sum(transitions) / len(transitions)
    var = sum((t - mean) ** 2 for t in transitions) / len(transitions)
    return var


def extract_features(domain):
    """Extract all 53 features matching Kotlin LexicalFeatureExtractor."""
    features = [0.0] * 53
    clean = sanitize(domain)
    if not clean:
        return features

    parts = clean.split('.')
    tld = parts[-1] if len(parts) >= 2 else ''
    sld = parts[-2] if len(parts) >= 2 else clean
    subdomain_parts = parts[:-2] if len(parts) >= 3 else []
    subdomain = '.'.join(subdomain_parts)
    domain_depth = len(parts)

    idx = 0
    features[idx] = float(len(clean)); idx += 1                       # 0 domain_length
    features[idx] = float(max(0, domain_depth - 2)); idx += 1          # 1 subdomain_count
    features[idx] = shannon_entropy(clean); idx += 1                   # 2 domain_entropy
    features[idx] = digit_ratio(clean); idx += 1                       # 3 digit_ratio
    features[idx] = alpha_ratio(clean); idx += 1                       # 4 alpha_ratio
    features[idx] = vowel_ratio(clean); idx += 1                       # 5 vowel_ratio
    features[idx] = float(count_digits(clean)); idx += 1               # 6 num_digits
    features[idx] = float(clean.count('.')); idx += 1                  # 7 num_dots
    features[idx] = float(clean.count('-')); idx += 1                  # 8 num_hyphens
    features[idx] = float(len(tld)); idx += 1                          # 9 tld_length
    features[idx] = float(max_consonant_run(clean)); idx += 1          # 10 max_consonant_run
    features[idx] = 1.0 if 'xn--' in clean else 0.0; idx += 1          # 11 has_punycode
    features[idx] = 1.0 if is_raw_ip(clean) else 0.0; idx += 1         # 12 is_raw_ip
    features[idx] = consonant_ratio(clean); idx += 1                   # 13 consonant_ratio
    features[idx] = float(count_consonants(clean)); idx += 1           # 14 consonant_count
    features[idx] = float(count_vowels(clean)); idx += 1               # 15 vowel_count

    subdomain_lengths = [len(p) for p in subdomain_parts]
    features[idx] = float(max(subdomain_lengths) if subdomain_lengths else 0); idx += 1  # 16 longest_subdomain_len
    features[idx] = float(sum(subdomain_lengths) / len(subdomain_lengths)) if subdomain_lengths else 0.0; idx += 1  # 17 average_subdomain_len

    features[idx] = float(len(sld)); idx += 1                          # 18 sld_length
    features[idx] = shannon_entropy(sld); idx += 1                     # 19 sld_entropy
    features[idx] = digit_ratio(sld); idx += 1                         # 20 sld_digit_ratio
    features[idx] = float(sld.count('-')); idx += 1                    # 21 sld_hyphen_count

    features[idx] = (float(len(subdomain)) / float(len(clean))) if clean else 0.0; idx += 1   # 22 subdomain_length_ratio
    features[idx] = 1.0 if is_free_hosting(clean) else 0.0; idx += 1   # 23 is_free_hosting
    features[idx] = 1.0 if tld.lower() in COUNTRY_TLDS else 0.0; idx += 1  # 24 is_country_tld
    features[idx] = (float(clean.count('.')) / float(len(clean))) if clean else 0.0; idx += 1  # 25 dot_ratio
    features[idx] = (float(clean.count('-')) / float(len(clean))) if clean else 0.0; idx += 1  # 26 hyphen_ratio
    features[idx] = 1.0 if '--' in clean else 0.0; idx += 1            # 27 has_double_hyphen
    features[idx] = 1.0 if count_digits(clean) > 0 else 0.0; idx += 1  # 28 has_numbers
    features[idx] = 1.0 if subdomain_parts else 0.0; idx += 1          # 29 has_subdomains

    features[idx] = float(count_vowels(sld)); idx += 1                 # 30 sld_vowel_count
    features[idx] = float(count_consonants(sld)); idx += 1             # 31 sld_consonant_count
    features[idx] = 1.0 if (sld and sld[0].isdigit()) else 0.0; idx += 1  # 32 sld_starts_with_digit
    features[idx] = dga_score(sld); idx += 1                           # 33 sld_dga_score
    features[idx] = float(count_digits(sld)); idx += 1                 # 34 sld_digit_count
    features[idx] = float(count_rare_chars(sld)); idx += 1             # 35 sld_rare_char_count
    features[idx] = float(count_digit_sequences(sld)); idx += 1        # 36 sld_digit_seq_count
    features[idx] = float(max_consonant_run(sld)); idx += 1            # 37 sld_max_consonant_run

    features[idx] = digit_ratio(subdomain); idx += 1                   # 38 subdomain_digit_ratio
    features[idx] = float(len(subdomain_parts)); idx += 1              # 39 subdomain_token_count
    features[idx] = float(subdomain.count('-')); idx += 1              # 40 subdomain_hyphen_count
    features[idx] = shannon_entropy(subdomain); idx += 1               # 41 subdomain_entropy
    se_sld = shannon_entropy(sld)
    features[idx] = (shannon_entropy(subdomain) / se_sld) if se_sld > 0 else 0.0; idx += 1  # 42 entropy_ratio

    features[idx] = subdomain_sld_similarity(subdomain, sld); idx += 1 # 43 subdomain_sld_similarity
    features[idx] = char_transition_variance(clean); idx += 1          # 44 char_transition_variance
    features[idx] = float(domain_depth); idx += 1                      # 45 domain_depth

    # ── Dynamic features (previously zeroed) ──────────────────────────
    # These are populated below using corpus statistics
    features[idx] = 0.0; idx += 1  # 46 has_brand_in_sld
    features[idx] = 0.0; idx += 1  # 47 brand_in_bad_tld
    features[idx] = 0.0; idx += 1  # 48 is_new_gtld
    features[idx] = 0.0; idx += 1  # 49 tld_phishing_rate
    features[idx] = 0.0; idx += 1  # 50 sld_has_keyword
    features[idx] = 0.0; idx += 1  # 51 subdomain_has_brand
    features[idx] = 0.0; idx += 1  # 52 subdomain_has_keyword

    return features


def add_dynamic_features(features, domain, brand_tld_stats, tld_phishing_rates):
    """Populate dynamic features 46-52 using corpus statistics."""
    clean = sanitize(domain)
    if not clean:
        return features
    parts = clean.split('.')
    tld = parts[-1] if len(parts) >= 2 else ''
    sld = parts[-2] if len(parts) >= 2 else clean
    subdomain_parts = parts[:-2] if len(parts) >= 3 else []
    subdomain = '.'.join(subdomain_parts)

    # 46: has_brand_in_sld
    sld_lower = sld.lower()
    has_brand = 1.0 if any(b in sld_lower for b in BRANDS) else 0.0
    features[46] = has_brand

    # 47: brand_in_bad_tld
    brand_in_bad = 1.0 if (has_brand and tld.lower() in SUSPICIOUS_TLDS) else 0.0
    features[47] = brand_in_bad

    # 48: is_new_gtld
    features[48] = 1.0 if tld.lower() in NEW_GTLDS else 0.0

    # 49: tld_phishing_rate
    features[49] = float(tld_phishing_rates.get(tld.lower(), 0.0))

    # 50: sld_has_keyword
    keywords = {'login', 'secure', 'account', 'verify', 'update', 'confirm',
                'signin', 'sign-in', 'webscr', 'cmd', 'token', 'auth',
                'security', 'banking', 'payment', 'pay', 'invoice'}
    sld_kw = 1.0 if any(k in sld_lower for k in keywords) else 0.0
    features[50] = sld_kw

    # 51: subdomain_has_brand
    sub_lower = subdomain.lower()
    sub_brand = 1.0 if any(b in sub_lower for b in BRANDS) else 0.0
    features[51] = sub_brand

    # 52: subdomain_has_keyword
    sub_kw = 1.0 if any(k in sub_lower for k in keywords) else 0.0
    features[52] = sub_kw

    return features


if __name__ == '__main__':
    # Quick smoke test
    for d in ['google.com', 'login-paypal.suspicious-site.tk', '192.168.1.1', '[::1]', 'example%2Ecom']:
        f = extract_features(d)
        print(f'{d:45s} len={len(f)} nonzero={sum(1 for x in f if x != 0)}')
