"""
Enhanced feature pipeline with URL-based and brand-impersonation features.

Adds 20 new features to the original 53:
- URL path features (5 features)
- Brand similarity features (5 features)  
- Suspicious keyword features (5 features)
- Structural URL features (5 features)

Total: 73 features.
"""
import math, re
from collections import defaultdict

# Re-export original 53 features
from feature_pipeline import (
    extract_features as extract_features_base,
    add_dynamic_features as add_dynamic_features_base,
    sanitize as sanitize_base,
    BRANDS, SUSPICIOUS_TLDS, NEW_GTLDS,
)

# Suspicious URL path keywords
SUSPICIOUS_PATH_KEYWORDS = {
    'login', 'signin', 'sign-in', 'logon', 'log-on', 'auth', 'authenticate',
    'verify', 'verification', 'secure', 'security', 'account', 'update',
    'confirm', 'confirmation', 'validate', 'validation', 'webscr', 'cmd',
    '_login', '_signin', 'banking', 'paypal', 'payment', 'pay', 'invoice',
    'token', 'session', 'ssn', 'password', 'pwd', 'credential', 'wals',
    'ebayisapi', 'signin_secure', 'account-secure', 'update-payment',
}

# Known brands for impersonation detection
KNOWN_BRANDS = [
    'paypal', 'apple', 'microsoft', 'google', 'amazon', 'facebook', 'netflix',
    'chase', 'wellsfargo', 'citibank', 'bankofamerica', 'hsbc', 'barclays',
    'santander', 'lloyds', 'natwest', 'americanexpress', 'visa', 'mastercard',
    'dropbox', 'onedrive', 'icloud', 'linkedin', 'twitter', 'instagram',
    'whatsapp', 'telegram', 'discord', 'steam', 'ebay', 'walmart', 'target',
]

def levenshtein_distance(s1, s2):
    """Compute Levenshtein edit distance between two strings."""
    if len(s1) < len(s2):
        return levenshtein_distance(s2, s1)
    if len(s2) == 0:
        return len(s1)
    
    previous_row = range(len(s2) + 1)
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row
    
    return previous_row[-1]

def min_brand_distance(sld):
    """Minimum Levenshtein distance from SLD to any known brand."""
    if not sld:
        return 100
    return min(levenshtein_distance(sld, brand) for brand in KNOWN_BRANDS)

def extract_enhanced_features(url_or_domain):
    """
    Extract 73 features: original 53 + 20 new URL-based features.
    
    Features 0-52: Original lexical features (from feature_pipeline)
    Features 53-72: New URL-based features
    
    New features:
    53: url_path_length (length of URL path)
    54: url_path_segments (number of path segments)
    55: url_path_suspicious_keywords (count of suspicious keywords in path)
    56: url_has_suspicious_path (1 if path has suspicious keywords)
    57: url_path_depth (max nesting depth)
    58: url_query_length (length of query string)
    59: url_query_params (number of query parameters)
    60: url_has_at_sign (1 if URL contains @)
    61: url_has_double_slash (1 if URL has // after domain)
    62: url_has_ip_in_path (1 if path looks like IP)
    63: brand_min_levenshtein (min edit distance to known brands)
    64: brand_very_close (1 if min distance <= 2)
    65: brand_close (1 if min distance <= 4)
    66: sld_brand_distance_ratio (levenshtein distance / sld length)
    67: url_total_length (total URL length)
    68: url_has_port (1 if URL has explicit port)
    69: url_has_credentials (1 if URL has user:pass@)
    70: url_tld_suspicious_combo (1 if suspicious TLD + brand in SLD)
    71: url_path_entropy (Shannon entropy of URL path)
    72: url_domain_path_similarity (Jaccard similarity domain vs path)
    """
    # Get base 53 features
    base = extract_features_base(url_or_domain)
    
    # Parse URL
    d = sanitize_base(url_or_domain)
    if not d:
        return list(base) + [0.0] * 20
    
    # Extract path from original URL (before sanitization)
    url_lower = url_or_domain.lower().strip()
    path = ''
    query = ''
    
    # Remove scheme
    for pre in ('http://', 'https://'):
        if url_lower.startswith(pre):
            url_lower = url_lower[len(pre):]
            break
    
    # Split path and query
    if '/' in url_lower:
        path_start = url_lower.index('/')
        path_part = url_lower[path_start:]
        if '?' in path_part:
            q_idx = path_part.index('?')
            path = path_part[:q_idx]
            query = path_part[q_idx+1:]
        else:
            path = path_part
    elif '?' in url_lower:
        q_idx = url_lower.index('?')
        query = url_lower[q_idx+1:]
    
    # ── New features ─────────────────────────────────────────────────
    
    # 53: url_path_length
    path_length = len(path)
    
    # 54: url_path_segments
    path_segments = [s for s in path.split('/') if s]
    path_segment_count = len(path_segments)
    
    # 55-56: suspicious path keywords
    path_lower = path.lower()
    suspicious_count = sum(1 for kw in SUSPICIOUS_PATH_KEYWORDS if kw in path_lower)
    has_suspicious_path = 1 if suspicious_count > 0 else 0
    
    # 57: url_path_depth (max nesting)
    path_depth = path.count('/')
    
    # 58-59: query features
    query_length = len(query)
    query_params = len([p for p in query.split('&') if p]) if query else 0
    
    # 60: url_has_at_sign
    has_at = 1 if '@' in url_or_domain else 0
    
    # 61: url_has_double_slash
    has_double_slash = 1 if '//' in url_or_domain[len('https://'):] or '//' in url_or_domain[len('http://'):] else 0
    
    # 62: url_has_ip_in_path
    ip_in_path = 0
    for seg in path_segments:
        parts = seg.split('.')
        if len(parts) == 4 and all(p.isdigit() for p in parts):
            ip_in_path = 1
            break
    
    # 63-66: brand impersonation
    # Extract SLD from sanitized domain
    domain_parts = d.split('.')
    sld = domain_parts[-2].lower() if len(domain_parts) >= 2 else d.lower()
    
    min_lev = min_brand_distance(sld)
    brand_very_close = 1 if min_lev <= 2 else 0
    brand_close = 1 if min_lev <= 4 else 0
    sld_len = len(sld) if sld else 1
    brand_distance_ratio = min_lev / sld_len
    
    # 67: url_total_length
    url_total_length = len(url_or_domain)
    
    # 68: url_has_port
    has_port = 0
    # Check for port in domain part
    domain_part = url_lower.split('/')[0] if '/' in url_lower else url_lower
    if ':' in domain_part:
        after_colon = domain_part.split(':')[-1]
        # Remove path/query if any
        after_colon = after_colon.split('/')[0].split('?')[0]
        if after_colon.isdigit():
            has_port = 1
    
    # 69: url_has_credentials
    has_credentials = 1 if '@' in url_or_domain and '://' in url_or_domain else 0
    
    # 70: url_tld_suspicious_combo
    tld = domain_parts[-1].lower() if len(domain_parts) >= 2 else ''
    brand_in_sld = 1 if any(b in sld for b in BRANDS) else 0
    tld_suspicious_combo = 1 if (brand_in_sld and tld in SUSPICIOUS_TLDS) else 0
    
    # 71: url_path_entropy
    if path_length > 0:
        counts = defaultdict(int)
        for c in path_lower:
            counts[c] += 1
        path_entropy = 0.0
        for count in counts.values():
            p = count / path_length
            path_entropy -= p * math.log(p)
    else:
        path_entropy = 0.0
    
    # 72: url_domain_path_similarity (Jaccard)
    domain_chars = set(d.lower())
    path_chars = set(path_lower)
    if domain_chars or path_chars:
        inter = len(domain_chars & path_chars)
        union = len(domain_chars | path_chars)
        domain_path_sim = inter / union if union > 0 else 0.0
    else:
        domain_path_sim = 0.0
    
    # Combine
    new_features = [
        float(path_length),
        float(path_segment_count),
        float(suspicious_count),
        float(has_suspicious_path),
        float(path_depth),
        float(query_length),
        float(query_params),
        float(has_at),
        float(has_double_slash),
        float(ip_in_path),
        float(min_lev),
        float(brand_very_close),
        float(brand_close),
        brand_distance_ratio,
        float(url_total_length),
        float(has_port),
        float(has_credentials),
        float(tld_suspicious_combo),
        path_entropy,
        domain_path_sim,
    ]
    
    return list(base) + new_features


FEATURE_COUNT_ENHANCED = 73


if __name__ == '__main__':
    # Quick test
    test_urls = [
        'google.com',
        'login-paypal.suspicious-site.tk',
        'paypal.com/login',
        'paypal-security-update.tk/cgi-bin/webscr',
        'apple.com',
        'appleid.account-update.ml/signin',
        '192.168.1.1',
        'xn--pple-43d.com',
    ]
    for url in test_urls:
        f = extract_enhanced_features(url)
        print(f'{url:55s} features={len(f)}')
    
    # Verify original features match
    print('\nVerifying original 53 features match...')
    from feature_pipeline import extract_features as base_extract
    for url in test_urls[:3]:
        base_f = base_extract(url)
        enh_f = extract_enhanced_features(url)
        match = all(abs(base_f[i] - enh_f[i]) < 1e-6 for i in range(53))
        print(f'  {url}: base features match enhanced = {match}')
