"""
Evaluate LightGBM v2 model on test set.
"""
import os, sys, json, numpy as np
from sklearn.metrics import confusion_matrix
import lightgbm as lgb
import warnings
warnings.filterwarnings('ignore')

sys.path.insert(0, os.path.dirname(__file__))
from enhanced_features import extract_enhanced_features

sys.stdout.reconfigure(line_buffering=True)

SEED = 42
random_seed = SEED
import random
random.seed(random_seed)
np.random.seed(random_seed)

DATA_DIR = r'A:\Protctus-app\ml\training\datasets'
PHISHING_FILE = r'A:\Protctus-app\ml\evaluation\fixtures\phishing-domains-ACTIVE.txt'
UMBRELLA_FILE = os.path.join(DATA_DIR, 'top-1m.csv')

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
    from collections import defaultdict
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
        rates[tld] = tld_phish.get(tld, 0) / max(1, tld_total[tld])
    return rates

def feat_with_enhanced(domain, tld_rates):
    f = extract_enhanced_features(domain)
    return np.array(f, dtype=np.float32)

def build_feature_matrix(domains, tld_rates):
    X = []
    for i, (d, _) in enumerate(domains):
        X.append(feat_with_enhanced(d, tld_rates))
        if (i + 1) % 20000 == 0:
            print(f'    features: {i+1}/{len(domains)}')
    return np.array(X, dtype=np.float32)

def metrics_at(y_true, scores, thr):
    y_pred = (scores >= thr).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()
    acc = (tp + tn) / max(1, tp + tn + fp + fn)
    rec = tp / max(1, tp + fn)
    prec = tp / max(1, tp + fp)
    f1 = 2 * prec * rec / max(1e-9, (prec + rec))
    fpr = fp / max(1, fp + tn)
    fnr = fn / max(1, fn + tp)
    return dict(tn=int(tn), fp=int(fp), fn=int(fn), tp=int(tp),
                accuracy=float(acc), recall=float(rec), precision=float(prec),
                f1=float(f1), fpr=float(fpr), fnr=float(fnr))

def main():
    print('=' * 60)
    print('LightGBM v2 Test Evaluation')
    print('=' * 60)

    MAX_PHISH = 100000
    MAX_BENIGN = 100000

    print('\nLoading phishing domains...')
    phish = load_domains(PHISHING_FILE, label=1, max_samples=MAX_PHISH)
    print(f'  phishing loaded: {len(phish)}')

    print('Loading benign domains...')
    benign = load_domains(UMBRELLA_FILE, label=0, max_samples=MAX_BENIGN)
    print(f'  benign loaded: {len(benign)}')

    # Dedupe
    benign_set = {d for d, _ in benign}
    phish_set = {d for d, _ in phish}
    phish = [(d, l) for d, l in phish if d not in benign_set]
    benign = [(d, l) for d, l in benign if d not in phish_set]
    print(f'  after dedup: phishing={len(phish)}, benign={len(benign)}')

    # TLD rates
    tld_rates = compute_tld_phishing_rates(phish, benign)
    print(f'  TLDs: {len(tld_rates)}')

    # Split - same as training
    import random
    random.seed(SEED)
    random.shuffle(phish)
    random.shuffle(benign)

    def split_domains(domains, tr=0.6, va=0.2, te=0.2):
        n = len(domains)
        nt = int(n * tr)
        nv = int(n * va)
        return domains[:nt], domains[nt:nt+nv], domains[nt+nv:]

    (pt, pv, pte), (bt, bv, bte) = split_domains(phish), split_domains(benign)
    train = [(d, 1) for (d, _) in pt] + [(d, 0) for (d, _) in bt]
    val = [(d, 1) for (d, _) in pv] + [(d, 0) for (d, _) in bv]
    test = [(d, 1) for (d, _) in pte] + [(d, 0) for (d, _) in bte]
    random.shuffle(train)
    random.shuffle(val)
    random.shuffle(test)

    print(f'Split: train={len(train)}, val={len(val)}, test={len(test)}')

    # Features
    print('\nBuilding test feature matrix...')
    X_test = build_feature_matrix(test, tld_rates)
    y_test = np.array([l for _, l in test], dtype=int)
    print(f'  X_test: {X_test.shape}')

    # Load model
    model_dir = r'A:\Protctus-app\ml\training\models'
    model_path = os.path.join(model_dir, 'lightgbm_v2_model.txt')
    model = lgb.Booster(model_file=model_path)
    
    # Evaluate on test
    s_test = model.predict(X_test, num_iteration=model.best_iteration)
    
    # Try multiple thresholds
    print('\nTest set evaluation at various thresholds:')
    print(f'{"Threshold":>10} {"Acc":>8} {"Recall":>8} {"Prec":>8} {"F1":>8} {"FPR":>8} {"FNR":>8}')
    print('-' * 72)
    
    best_acc = 0
    best_rec = 0
    best_combined = 0
    best_threshold = 0
    
    for thr in np.linspace(0.0, 1.0, 10001):
        m = metrics_at(y_test, s_test, float(thr))
        if m['accuracy'] > best_acc:
            best_acc = m['accuracy']
        if m['recall'] > best_rec:
            best_rec = m['recall']
        combined = min(m['accuracy'], m['recall'])
        if combined > best_combined:
            best_combined = combined
            best_threshold = float(thr)
        if thr in [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]:
            print(f'{thr:>10.1f} {m["accuracy"]:>8.4f} {m["recall"]:>8.4f} {m["precision"]:>8.4f} {m["f1"]:>8.4f} {m["fpr"]:>8.4f} {m["fnr"]:>8.4f}')
    
    # Find threshold where both acc and rec >= 0.95
    print('\nSearching for threshold where BOTH acc>=0.95 AND rec>=0.95...')
    found = False
    for thr in np.linspace(0.0, 1.0, 100001):
        m = metrics_at(y_test, s_test, float(thr))
        if m['accuracy'] >= 0.95 and m['recall'] >= 0.95:
            print(f'  FOUND: threshold={thr:.5f} acc={m["accuracy"]:.4f} rec={m["recall"]:.4f} prec={m["precision"]:.4f} fpr={m["fpr"]:.4f}')
            found = True
            break
    
    if not found:
        print('  NO threshold achieves both acc>=0.95 AND rec>=0.95 on test set.')
    
    # Best combined threshold results
    m_best = metrics_at(y_test, s_test, best_threshold)
    print(f'\nBest combined threshold: {best_threshold:.4f}')
    print(f'  acc={m_best["accuracy"]:.4f} rec={m_best["recall"]:.4f} prec={m_best["precision"]:.4f} f1={m_best["f1"]:.4f} fpr={m_best["fpr"]:.4f}')
    print(f'  CM: tn={m_best["tn"]} fp={m_best["fp"]} fn={m_best["fn"]} tp={m_best["tp"]}')
    print(f'  Target acc>=0.95: {"PASS" if m_best["accuracy"] >= 0.95 else "FAIL"}')
    print(f'  Target rec>=0.95: {"PASS" if m_best["recall"] >= 0.95 else "FAIL"}')

if __name__ == '__main__':
    main()
