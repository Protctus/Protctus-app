"""
LightGBM v2 with enhanced 73 features, targeting 0.95/0.95.
Uses enhanced_features.py for the 73-feature set.
"""
import os, sys, json, time, random
from collections import defaultdict
import numpy as np
import lightgbm as lgb
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

def main():
    print('=' * 60)
    print('LightGBM v2 with Enhanced 73 Features')
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

    # Train LightGBM with tuned hyperparameters
    print('\n' + '=' * 60)
    print('Training LightGBM v2')
    print('=' * 60)

    lgb_train = lgb.Dataset(X_train, label=y_train)
    lgb_val = lgb.Dataset(X_val, label=y_val, reference=lgb_train)

    params = {
        'objective': 'binary',
        'metric': ['binary_logloss', 'auc'],
        'verbose': -1,
        'seed': SEED,
        'num_leaves': 63,
        'max_depth': 10,
        'learning_rate': 0.03,
        'feature_fraction': 0.8,
        'bagging_fraction': 0.7,
        'bagging_freq': 5,
        'min_data_in_leaf': 30,
        'lambda_l1': 0.1,
        'lambda_l2': 1.0,
        'is_unbalance': True,
    }

    t0 = time.time()
    model = lgb.train(
        params,
        lgb_train,
        valid_sets=[lgb_val],
        num_boost_round=2000,
        callbacks=[lgb.early_stopping(100), lgb.log_evaluation(200)],
    )
    tt = time.time() - t0
    print(f'  Training time: {tt:.1f}s')
    print(f'  Best iteration: {model.best_iteration}')
    print(f'  Best score: {model.best_score}')

    # Evaluate on validation
    s_val = model.predict(X_val, num_iteration=model.best_iteration)
    
    # Find threshold for best combined score (accuracy + recall balanced)
    best_val = None
    for thr in np.linspace(0.0, 1.0, 10001):
        m = metrics_at(y_val, s_val, float(thr))
        if m['recall'] >= 0.95 and m['accuracy'] >= 0.95:
            if best_val is None or m['f1'] > best_val['f1']:
                best_val = dict(m, threshold=float(thr))
    
    if best_val is None:
        print('  No threshold achieves both acc>=0.95 and rec>=0.95 on validation.')
        # Find threshold that maximizes min(acc, rec)
        best_combined = None
        for thr in np.linspace(0.0, 1.0, 10001):
            m = metrics_at(y_val, s_val, float(thr))
            combined = min(m['accuracy'], m['recall'])
            if best_combined is None or combined > best_combined['combined']:
                best_combined = dict(m, threshold=float(thr), combined=combined)
        best_val = best_combined
        print(f'  Best combined threshold: {best_val["threshold"]:.4f}, min(acc,rec)={best_val["combined"]:.4f}')
    
    # Evaluate on test
    s_test = model.predict(X_test, num_iteration=model.best_iteration)
    test_m = metrics_at(y_test, s_test, best_val['threshold'])

    print(f'\n  Validation threshold: {best_val["threshold"]:.4f}')
    print(f'  Test: acc={test_m["accuracy"]:.4f} rec={test_m["recall"]:.4f} prec={test_m["precision"]:.4f} f1={test_m["f1"]:.4f} fpr={test_m["fpr"]:.4f} fnr={test_m["fnr"]:.4f}')
    print(f'  CM: tn={test_m["tn"]} fp={test_m["fp"]} fn={test_m["fn"]} tp={test_m["tp"]}')
    print(f'  Target acc>=0.95: {"PASS" if test_m["accuracy"] >= 0.95 else "FAIL"}')
    print(f'  Target rec>=0.95: {"PASS" if test_m["recall"] >= 0.95 else "FAIL"}')

    # Save model
    model_dir = r'A:\Protctus-app\ml\training\models'
    os.makedirs(model_dir, exist_ok=True)
    model_path = os.path.join(model_dir, 'lightgbm_v2_model.txt')
    model.save_model(model_path)
    print(f'\n  LightGBM v2 model saved to {model_path}')

    # Save results
    results_out = {
        'seed': SEED,
        'model': 'LightGBM_v2_73features',
        'feature_count': X_train.shape[1],
        'train_size': len(train),
        'val_size': len(val),
        'test_size': len(test),
        'test_benign': int((y_test == 0).sum()),
        'test_phishing': int((y_test == 1).sum()),
        'threshold': best_val['threshold'],
        'test_metrics': test_m,
        'best_iteration': model.best_iteration,
        'best_score': model.best_score,
        'target_acc_0p95_met': test_m['accuracy'] >= 0.95,
        'target_rec_0p95_met': test_m['recall'] >= 0.95,
    }
    results_path = os.path.join(model_dir, 'lightgbm_v2_results.json')
    with open(results_path, 'w') as f:
        json.dump(results_out, f, indent=2)
    print(f'  Results saved to {results_path}')

    return results_out, model, X_test, y_test, s_test


if __name__ == '__main__':
    main()
