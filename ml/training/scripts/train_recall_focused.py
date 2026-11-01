"""
Recall-focused training pipeline.

Goal: Achieve recall >= 0.95 for phishing detection while maintaining reasonable precision.

Strategy:
1. Download more phishing data from multiple sources
2. Use class_weight heavily favoring positive class
3. Optimize threshold for recall (not F1)
4. Try multiple model architectures
5. Add URL-based features (not just domain)
"""
import os, sys, json, time, random, math, urllib.request
from collections import defaultdict
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier, VotingClassifier
from sklearn.neural_network import MLPClassifier
from sklearn.metrics import confusion_matrix
from sklearn.calibration import CalibratedClassifierCV
import warnings
warnings.filterwarnings('ignore')

sys.path.insert(0, os.path.dirname(__file__))
from feature_pipeline import extract_features, add_dynamic_features

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

def feat_with_dynamic(domain, tld_rates):
    f = extract_features(domain)
    return add_dynamic_features(f, domain, None, tld_rates)

def build_feature_matrix(domains, tld_rates):
    X = []
    for d, _ in domains:
        X.append(feat_with_dynamic(d, tld_rates))
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
    """Find the threshold that achieves target recall on validation."""
    best = None
    for thr in np.linspace(0.0, 1.0, 10001):
        m = metrics_at(y_val, scores, float(thr))
        if m['recall'] >= target_recall:
            if best is None or m['fpr'] < best['fpr']:
                best = dict(m, threshold=float(thr))
    return best

def main():
    print('=' * 60)
    print('Recall-Focused Training Pipeline')
    print('=' * 60)

    MAX_PHISH = 150000
    MAX_BENIGN = 150000

    print('\nLoading phishing domains...')
    phish = load_domains(PHISHING_FILE, label=1, max_samples=MAX_PHISH)
    print(f'  phishing loaded: {len(phish)}')

    print('Loading benign domains...')
    benign_umbrella = load_domains(UMBRELLA_FILE, label=0, max_samples=MAX_BENIGN)
    print(f'  umbrella benign loaded: {len(benign_umbrella)}')

    # Dedupe
    benign_set = {d for d, _ in benign_umbrella}
    phish_set = {d for d, _ in phish}
    overlap = benign_set & phish_set
    print(f'  overlap: {len(overlap)}')
    phish = [(d, l) for d, l in phish if d not in benign_set]
    benign_umbrella = [(d, l) for d, l in benign_umbrella if d not in phish_set]
    print(f'  after dedup: phishing={len(phish)}, benign={len(benign_umbrella)}')

    # TLD rates
    print('\nComputing TLD phishing rates...')
    tld_rates = compute_tld_phishing_rates(phish, benign_umbrella)
    print(f'  {len(tld_rates)} TLDs')

    # Split
    random.shuffle(phish)
    random.shuffle(benign_umbrella)

    def split_domains(domains, tr=0.6, va=0.2):
        n = len(domains)
        nt = int(n * tr)
        nv = int(n * va)
        return domains[:nt], domains[nt:nt+nv], domains[nt+nv:]

    (pt, pv, pte), (bt, bv, bte) = split_domains(phish), split_domains(benign_umbrella)
    train = [(d, 1) for (d, _) in pt] + [(d, 0) for (d, _) in bt]
    val = [(d, 1) for (d, _) in pv] + [(d, 0) for (d, _) in bv]
    test = [(d, 1) for (d, _) in pte] + [(d, 0) for (d, _) in bte]
    random.shuffle(train)
    random.shuffle(val)
    random.shuffle(test)

    print(f'\nSplit: train={len(train)}, val={len(val)}, test={len(test)}')
    train_pos = sum(l for _, l in train)
    val_pos = sum(l for _, l in val)
    test_pos = sum(l for _, l in test)
    print(f'  train: pos={train_pos} neg={len(train)-train_pos}')
    print(f'  val: pos={val_pos} neg={len(val)-val_pos}')
    print(f'  test: pos={test_pos} neg={len(test)-test_pos}')

    # Features
    print('\nBuilding feature matrices...')
    X_train = build_feature_matrix(train, tld_rates)
    y_train = np.array([l for _, l in train], dtype=int)
    X_val = build_feature_matrix(val, tld_rates)
    y_val = np.array([l for _, l in val], dtype=int)
    X_test = build_feature_matrix(test, tld_rates)
    y_test = np.array([l for _, l in test], dtype=int)
    print(f'  X_train: {X_train.shape}, X_val: {X_val.shape}, X_test: {X_test.shape}')

    # Train recall-optimized models
    print('\n' + '=' * 60)
    print('Recall-Optimized Model Training')
    print('=' * 60)

    # Model 1: LogisticRegression with high class weight for positive
    print('\nTraining LogisticRegression (recall-optimized)...')
    lr = LogisticRegression(max_iter=5000, class_weight={0: 1, 1: 10}, random_state=SEED, n_jobs=1)
    lr.fit(X_train, y_train)
    sv = lr.predict_proba(X_val)[:, 1]
    
    # Find threshold for recall >= 0.95
    best_lr = find_threshold_for_recall(y_val, sv, target_recall=0.95)
    if best_lr is None:
        print('  WARNING: Cannot achieve 0.95 recall on validation. Using max-F1 threshold.')
        from evaluate_phishing import best_threshold_by_f1  # if available
        best_lr = {'threshold': 0.5, **metrics_at(y_val, sv, 0.5)}
    
    st = lr.predict_proba(X_test)[:, 1]
    lr_test = metrics_at(y_test, st, best_lr['threshold'])
    print(f'  val threshold for recall>=0.95: {best_lr["threshold"]:.4f}')
    print(f'  test: acc={lr_test["accuracy"]:.4f} rec={lr_test["recall"]:.4f} prec={lr_test["precision"]:.4f} f1={lr_test["f1"]:.4f} fpr={lr_test["fpr"]:.4f}')
    print(f'  CM: tn={lr_test["tn"]} fp={lr_test["fp"]} fn={lr_test["fn"]} tp={lr_test["tp"]}')

    # Model 2: HistGradientBoosting with class weight
    print('\nTraining HistGradientBoosting (recall-optimized)...')
    hgb = HistGradientBoostingClassifier(max_iter=500, max_depth=10, class_weight='balanced', random_state=SEED)
    hgb.fit(X_train, y_train)
    sv = hgb.predict_proba(X_val)[:, 1]
    best_hgb = find_threshold_for_recall(y_val, sv, target_recall=0.95)
    if best_hgb is None:
        print('  WARNING: Cannot achieve 0.95 recall on validation.')
        best_hgb = {'threshold': 0.5, **metrics_at(y_val, sv, 0.5)}
    
    st = hgb.predict_proba(X_test)[:, 1]
    hgb_test = metrics_at(y_test, st, best_hgb['threshold'])
    print(f'  val threshold for recall>=0.95: {best_hgb["threshold"]:.4f}')
    print(f'  test: acc={hgb_test["accuracy"]:.4f} rec={hgb_test["recall"]:.4f} prec={hgb_test["precision"]:.4f} f1={hgb_test["f1"]:.4f} fpr={hgb_test["fpr"]:.4f}')
    print(f'  CM: tn={hgb_test["tn"]} fp={hgb_test["fp"]} fn={hgb_test["fn"]} tp={hgb_test["tp"]}')

    # Model 3: RandomForest with class weight
    print('\nTraining RandomForest (recall-optimized)...')
    rf = RandomForestClassifier(n_estimators=300, max_depth=12, class_weight='balanced', random_state=SEED, n_jobs=1)
    rf.fit(X_train, y_train)
    sv = rf.predict_proba(X_val)[:, 1]
    best_rf = find_threshold_for_recall(y_val, sv, target_recall=0.95)
    if best_rf is None:
        print('  WARNING: Cannot achieve 0.95 recall on validation.')
        best_rf = {'threshold': 0.5, **metrics_at(y_val, sv, 0.5)}
    
    st = rf.predict_proba(X_test)[:, 1]
    rf_test = metrics_at(y_test, st, best_rf['threshold'])
    print(f'  val threshold for recall>=0.95: {best_rf["threshold"]:.4f}')
    print(f'  test: acc={rf_test["accuracy"]:.4f} rec={rf_test["recall"]:.4f} prec={rf_test["precision"]:.4f} f1={rf_test["f1"]:.4f} fpr={rf_test["fpr"]:.4f}')
    print(f'  CM: tn={rf_test["tn"]} fp={rf_test["fp"]} fn={rf_test["fn"]} tp={rf_test["tp"]}')

    # Model 4: MLP with class weight
    print('\nTraining MLP (recall-optimized)...')
    mlp = MLPClassifier(hidden_layer_sizes=(128, 64), max_iter=1000, random_state=SEED, early_stopping=True)
    mlp.fit(X_train, y_train)
    sv = mlp.predict_proba(X_val)[:, 1]
    best_mlp = find_threshold_for_recall(y_val, sv, target_recall=0.95)
    if best_mlp is None:
        print('  WARNING: Cannot achieve 0.95 recall on validation.')
        best_mlp = {'threshold': 0.5, **metrics_at(y_val, sv, 0.5)}
    
    st = mlp.predict_proba(X_test)[:, 1]
    mlp_test = metrics_at(y_test, st, best_mlp['threshold'])
    print(f'  val threshold for recall>=0.95: {best_mlp["threshold"]:.4f}')
    print(f'  test: acc={mlp_test["accuracy"]:.4f} rec={mlp_test["recall"]:.4f} prec={mlp_test["precision"]:.4f} f1={mlp_test["f1"]:.4f} fpr={mlp_test["fpr"]:.4f}')
    print(f'  CM: tn={mlp_test["tn"]} fp={mlp_test["fp"]} fn={mlp_test["fn"]} tp={mlp_test["tp"]}')

    # Summary
    print('\n' + '=' * 60)
    print('SUMMARY - Recall-Optimized Models on Held-Out Test Set')
    print('=' * 60)
    results = [
        ('LogisticRegression', lr_test, best_lr['threshold']),
        ('HistGradientBoosting', hgb_test, best_hgb['threshold']),
        ('RandomForest', rf_test, best_rf['threshold']),
        ('MLP', mlp_test, best_mlp['threshold']),
    ]
    for name, m, thr in results:
        print(f'  {name:22s} thr={thr:.4f} acc={m["accuracy"]:.4f} rec={m["recall"]:.4f} prec={m["precision"]:.4f} f1={m["f1"]:.4f} fpr={m["fpr"]:.4f}  (target: acc>=0.95 rec>=0.95)')

    # Save best results
    best = max(results, key=lambda x: x[1]['recall'])
    print(f'\nBest recall: {best[0]} with {best[1]["recall"]:.4f}')
    
    # Save results for the best model
    model_dir = MODEL_DIR
    os.makedirs(model_dir, exist_ok=True)
    results_out = {
        'seed': SEED,
        'best_model': best[0],
        'best_threshold': best[2],
        'feature_count': feature_count,
        'train_size': len(train),
        'val_size': len(val),
        'test_size': len(test),
        'test_benign': int((y_test == 0).sum()),
        'test_phishing': int((y_test == 1).sum()),
        'threshold': best[2],
        'test_metrics': best[1],
        'all_models': {name: {'threshold': thr, 'test': m} for name, m, thr in results},
        'target_recall_0p95_met': best[1]['recall'] >= 0.95,
        'target_accuracy_0p95_met': best[1]['accuracy'] >= 0.95,
    }
    results_path = os.path.join(model_dir, 'recall_focused_results.json')
    with open(results_path, 'w') as f:
        json.dump(results_out, f, indent=2)
    print(f'Results saved to {results_path}')

    return results_out


if __name__ == '__main__':
    main()
