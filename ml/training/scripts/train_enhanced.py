"""
Training with enhanced 73-feature set targeting 0.95/0.95.
"""
import os, sys, json, time, random
from collections import defaultdict
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier
from sklearn.metrics import confusion_matrix
import warnings
warnings.filterwarnings('ignore')

sys.path.insert(0, os.path.dirname(__file__))
from enhanced_features import extract_enhanced_features, FEATURE_COUNT_ENHANCED

sys.stdout.reconfigure(line_buffering=True)

SEED = 42
random.seed(SEED)
np.random.seed(SEED)

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

def find_threshold_for_recall(y_val, scores, target_recall=0.95):
    best = None
    for thr in np.linspace(0.0, 1.0, 10001):
        m = metrics_at(y_val, scores, float(thr))
        if m['recall'] >= target_recall:
            if best is None or m['fpr'] < best['fpr']:
                best = dict(m, threshold=float(thr))
    return best

def main():
    print('=' * 60)
    print('Enhanced Feature Training (73 features)')
    print('Target: accuracy >= 0.95 AND recall >= 0.95')
    print('=' * 60)

    MAX_PHISH = 120000
    MAX_BENIGN = 120000

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

    # Split
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

    print(f'\nSplit: train={len(train)}, val={len(val)}, test={len(test)}')

    # Features
    print('\nBuilding feature matrices...')
    X_train = build_feature_matrix(train, tld_rates)
    y_train = np.array([l for _, l in train], dtype=int)
    X_val = build_feature_matrix(val, tld_rates)
    y_val = np.array([l for _, l in val], dtype=int)
    X_test = build_feature_matrix(test, tld_rates)
    y_test = np.array([l for _, l in test], dtype=int)
    print(f'  X_train: {X_train.shape}, X_val: {X_val.shape}, X_test: {X_test.shape}')

    # Train models
    print('\n' + '=' * 60)
    print('Model Training with Enhanced Features')
    print('=' * 60)

    models = {
        'LogisticRegression': LogisticRegression(max_iter=5000, class_weight='balanced', random_state=SEED, n_jobs=1),
        'RandomForest': RandomForestClassifier(n_estimators=300, max_depth=14, class_weight='balanced', random_state=SEED, n_jobs=1),
        'HistGradientBoosting': HistGradientBoostingClassifier(max_iter=500, max_depth=12, class_weight='balanced', random_state=SEED),
    }

    results = {}
    for name, mdl in models.items():
        print(f'\nTraining {name}...')
        t0 = time.time()
        mdl.fit(X_train, y_train)
        tt = time.time() - t0

        sv = mdl.predict_proba(X_val)[:, 1]
        best = find_threshold_for_recall(y_val, sv, target_recall=0.95)

        if best is None:
            print(f'  WARNING: Cannot achieve 0.95 recall on val. Using max-F1 threshold.')
            # Find best F1
            best_f1 = None
            for thr in np.linspace(0.0, 1.0, 10001):
                m = metrics_at(y_val, sv, float(thr))
                if best_f1 is None or m['f1'] > best_f1['f1']:
                    best_f1 = dict(m, threshold=float(thr))
            best = best_f1

        st = mdl.predict_proba(X_test)[:, 1]
        test_m = metrics_at(y_test, st, best['threshold'])

        results[name] = {
            'train_time': tt,
            'val_threshold': best['threshold'],
            'test': test_m,
        }

        print(f'  {name}: train_time={tt:.1f}s')
        print(f'    val threshold: {best["threshold"]:.4f}')
        print(f'    test acc={test_m["accuracy"]:.4f} rec={test_m["recall"]:.4f} prec={test_m["precision"]:.4f} f1={test_m["f1"]:.4f} fpr={test_m["fpr"]:.4f}')
        print(f'    CM: tn={test_m["tn"]} fp={test_m["fp"]} fn={test_m["fn"]} tp={test_m["tp"]}')
        print(f'    Target acc>=0.95: {"PASS" if test_m["accuracy"] >= 0.95 else "FAIL"}')
        print(f'    Target rec>=0.95: {"PASS" if test_m["recall"] >= 0.95 else "FAIL"}')

    # Summary
    print('\n' + '=' * 60)
    print('SUMMARY')
    print('=' * 60)
    for name, r in results.items():
        m = r['test']
        acc_pass = "PASS" if m['accuracy'] >= 0.95 else "FAIL"
        rec_pass = "PASS" if m['recall'] >= 0.95 else "FAIL"
        print(f'  {name:22s} acc={m["accuracy"]:.4f} [{acc_pass}] rec={m["recall"]:.4f} [{rec_pass}] prec={m["precision"]:.4f} fpr={m["fpr"]:.4f}')

    # Save best
    best_name = max(results, key=lambda k: results[k]['test']['accuracy'] if results[k]['test']['recall'] >= 0.95 else 0)
    if results[best_name]['test']['recall'] >= 0.95:
        print(f'\nBest model meeting recall>=0.95: {best_name}')
    else:
        print(f'\nNo model meets recall>=0.95. Best recall: {max(r["test"]["recall"] for r in results.values()):.4f}')

if __name__ == '__main__':
    main()
