"""
Generate asset files for the Android app's dynamic feature computation.

Produces:
- neuroshield_dynamic_data.json — TLD phishing rates, brands, suspicious TLDs, new gTLDs, keywords
"""
import os, json
from collections import defaultdict

DATA_DIR = r'A:\Protctus-app\ml\training\datasets'
PHISHING_FILE = r'A:\Protctus-app\ml\evaluation\fixtures\phishing-domains-ACTIVE.txt'
UMBRELLA_FILE = os.path.join(DATA_DIR, 'top-1m.csv')
VVIP_FILE = r'A:\Protctus-app\app\src\main\assets\vvip_domains.txt'
OUT_DIR = r'A:\Protctus-app\app\src\main\assets'


def load_domains(path, label, max_samples=None):
    domains = []
    seen = set()
    with open(path, encoding='utf-8', errors='ignore') as f:
        for line in f:
            d = line.strip().lower()
            if not d:
                continue
            if ',' in d:
                d = d.split(',', 1)[1]
            for pre in ('http://', 'https://'):
                if d.startswith(pre):
                    d = d[len(pre):]
            d = d.split('/')[0].split('?')[0].split('#')[0].rstrip('.')
            if not d:
                continue
            if ' ' in d or '.' not in d or len(d) > 253:
                continue
            parts = d.split('.')
            if len(parts) == 4 and all(p.isdigit() for p in parts):
                continue
            if d in seen:
                continue
            seen.add(d)
            domains.append((d, label))
            if max_samples and len(domains) >= max_samples:
                break
    return domains


def compute_tld_phishing_rates(phishing_domains, benign_domains):
    tld_phish = defaultdict(int)
    tld_total = defaultdict(int)
    for d, _ in phishing_domains:
        parts = d.split('.')
        tld = parts[-1].lower() if len(parts) >= 2 else ''
        tld_phish[tld] += 1
        tld_total[tld] += 1
    for d, _ in benign_domains:
        parts = d.split('.')
        tld = parts[-1].lower() if len(parts) >= 2 else ''
        tld_total[tld] += 1
    rates = {}
    for tld in tld_total:
        rates[tld] = round(tld_phish.get(tld, 0) / max(1, tld_total[tld]), 4)
    return rates


def main():
    print('Loading data for asset generation...')
    phish = load_domains(PHISHING_FILE, label=1, max_samples=200000)
    benign_umbrella = load_domains(UMBRELLA_FILE, label=0, max_samples=300000)
    benign_vvip = load_domains(VVIP_FILE, label=0, max_samples=200000)
    all_benign = benign_umbrella + benign_vvip
    benign_set = {d for d, _ in all_benign}
    phish_set = {d for d, _ in phish}
    phish = [(d, l) for d, l in phish if d not in benign_set]
    all_benign = [(d, l) for d, l in all_benign if d not in phish_set]
    print(f'  phishing={len(phish)}, benign={len(all_benign)}')

    tld_rates = compute_tld_phishing_rates(phish, all_benign)
    print(f'  TLDs with rates: {len(tld_rates)}')

    # Only keep TLDs that appear in at least 10 domains (statistically meaningful)
    tld_counts = defaultdict(int)
    for d, _ in phish + all_benign:
        parts = d.split('.')
        tld = parts[-1].lower() if len(parts) >= 2 else ''
        tld_counts[tld] += 1
    filtered_rates = {t: r for t, r in tld_rates.items() if tld_counts.get(t, 0) >= 10}
    print(f'  TLDs with >=10 occurrences: {len(filtered_rates)}')

    brands = sorted([
        'paypal', 'apple', 'microsoft', 'google', 'amazon', 'facebook', 'netflix',
        'bank', 'chase', 'wellsfargo', 'citibank', 'bankofamerica', 'hsbc', 'barclays',
        'santander', 'lloyds', 'natwest', 'americanexpress', 'visa', 'mastercard',
        'dropbox', 'onedrive', 'icloud', 'linkedin', 'twitter', 'instagram',
        'whatsapp', 'telegram', 'discord', 'steam', 'epicgames', 'riotgames',
        'adobe', 'intuit', 'quickbooks', 'salesforce', 'zoom', 'slack',
        'dhl', 'fedex', 'ups', 'usps', 'ebay', 'etsy', 'walmart', 'target',
        'bestbuy', 'homedepot', 'lowes', 'cvs', 'walgreens',
    ])

    suspicious_tlds = sorted(['tk','ml','ga','cf','gq','buzz','top','icu','xyz','click',
        'link','work','date','racing','win','bid','stream','download','loan','trade',
        'webcam','country','kim','science','review'])

    new_gtlds = sorted(['xyz','online','site','vip','club','shop','store','tech','blog',
        'fun','party','review','science'])

    keywords = sorted(['login','secure','account','verify','update','confirm',
        'signin','signin','webscr','cmd','token','auth','security','banking',
        'payment','pay','invoice'])

    data = {
        'tld_phishing_rates': filtered_rates,
        'brands': brands,
        'suspicious_tlds': suspicious_tlds,
        'new_gtlds': new_gtlds,
        'keywords': keywords,
        'version': 1,
        'description': 'Dynamic feature data for NeuroShield phishing detection',
    }

    out_path = os.path.join(OUT_DIR, 'neuroshield_dynamic_data.json')
    with open(out_path, 'w') as f:
        json.dump(data, f, indent=2)
    print(f'\nSaved to {out_path} ({os.path.getsize(out_path)} bytes)')
    print(f'  TLD rates: {len(filtered_rates)}')
    print(f'  Brands: {len(brands)}')
    print(f'  Suspicious TLDs: {len(suspicious_tlds)}')
    print(f'  New gTLDs: {len(new_gtlds)}')
    print(f'  Keywords: {len(keywords)}')


if __name__ == '__main__':
    main()
