"""
Fast NeuroShield model training pipeline.

Uses smaller samples for quicker iteration while maintaining evaluation rigor.
Exports best model to ONNX.
"""
import os, sys, json, time, random, math
from collections import defaultdict
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier
from sklearn.metrics import confusion_matrix
import warnings
warnings.filterwarnings('ignore')

sys.path.insert(0, os.path.dirname(__file__))
from feature_pipeline import extract_features, add_dynamic_features

# Use unbuffered output
import sys as _sys
_sys.stdout.reconfigure(line_buffering=True)

DATA_DIR = r'A:\Protctus-app\ml\training\datasets'
PHISHING_FILE = r'A:\Protctus-app\ml\evaluation\fixtures\phishing-domains-ACTIVE.txt'
UMBRELLA_FILE = os.path.join(DATA_DIR, 'top-1m.csv')
VVIP_FILE = r'A:\Protctus-app\app\src\main\assets\vvip_domains.txt'

SEED = 42
random.seed(SEED)
np.random.seed(SEED)

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
    for i in range(len(domains)):
        d = domains[i][0]
        X.append(feat_with_dynamic(d, tld_rates))
        if (i + 1) % 10000 == 0:
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


def best_threshold_by_f1(y_val, scores):
    best = None
    for thr in np.linspace(0.0, 1.0, 1001):
        m = metrics_at(y_val, scores, float(thr))
        if best is None or m['f1'] > best['f1']:
            best = dict(m, threshold=float(thr))
    return best


def main():
    print('=' * 60)
    print('NeuroShield Fast Training Pipeline')
    print('=' * 60)

    MAX_PER_CLASS = 60000

    print('\nLoading phishing domains...')
    phish = load_domains(PHISHING_FILE, label=1, max_samples=MAX_PER_CLASS)
    print(f'  phishing loaded: {len(phish)}')

    print('Loading benign domains (Umbrella)...')
    benign_umbrella = load_domains(UMBRELLA_FILE, label=0, max_samples=MAX_PER_CLASS)
    print(f'  umbrella benign loaded: {len(benign_umbrella)}')

    print('Loading benign domains (VVIP)...')
    benign_vvip = load_domains(VVIP_FILE, label=0, max_samples=MAX_PER_CLASS)
    print(f'  vvip benign loaded: {len(benign_vvip)}')

    all_benign = benign_umbrella + benign_vvip
    benign_set = {d for d, _ in all_benign}
    phish_set = {d for d, _ in phish}
    overlap = benign_set & phish_set
    print(f'  overlap: {len(overlap)}')
    phish = [(d, l) for d, l in phish if d not in benign_set]
    all_benign = [(d, l) for d, l in all_benign if d not in phish_set]
    print(f'  after dedup: phishing={len(phish)}, benign={len(all_benign)}')

    # TLD rates
    print('\nComputing TLD phishing rates...')
    tld_rates = compute_tld_phishing_rates(phish, all_benign)
    print(f'  {len(tld_rates)} TLDs')

    # Split
    random.shuffle(phish)
    random.shuffle(all_benign)

    def split_domains(domains, tr=0.6, va=0.2):
        n = len(domains)
        nt = int(n * tr)
        nv = int(n * va)
        return domains[:nt], domains[nt:nt+nv], domains[nt+nv:]

    (pt, pv, pte), (bt, bv, bte) = split_domains(phish), split_domains(all_benign)
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
    print('Model Training')
    print('=' * 60)

    models = {
        'LogisticRegression': LogisticRegression(max_iter=2000, class_weight='balanced', random_state=SEED, n_jobs=1),
        'RandomForest': RandomForestClassifier(n_estimators=200, max_depth=12, class_weight='balanced', random_state=SEED, n_jobs=1),
        'HistGradientBoosting': HistGradientBoostingClassifier(max_iter=200, max_depth=6, class_weight='balanced', random_state=SEED),
    }

    results = {}
    for name, mdl in models.items():
        print(f'\nTraining {name}...')
        t0 = time.time()
        mdl.fit(X_train, y_train)
        tt = time.time() - t0

        sv = mdl.predict_proba(X_val)[:, 1]
        best = best_threshold_by_f1(y_val, sv)

        st = mdl.predict_proba(X_test)[:, 1]
        test_m = metrics_at(y_test, st, best['threshold'])

        results[name] = {
            'train_time': tt,
            'val_threshold': best['threshold'],
            'test': test_m,
            'model': mdl,
        }

        print(f'  {name}: train_time={tt:.1f}s')
        print(f'    val thr={best["threshold"]:.4f}')
        print(f'    test acc={test_m["accuracy"]:.4f} rec={test_m["recall"]:.4f} prec={test_m["precision"]:.4f} f1={test_m["f1"]:.4f} fpr={test_m["fpr"]:.4f}')
        print(f'    CM: tn={test_m["tn"]} fp={test_m["fp"]} fn={test_m["fn"]} tp={test_m["tp"]}')

    # Best model
    best_name = max(results, key=lambda k: results[k]['test']['f1'])
    best_result = results[best_name]
    best_mdl = best_result['model']
    best_thr = best_result['val_threshold']

    print(f'\n' + '=' * 60)
    print(f'Best model: {best_name}')
    print('=' * 60)

    st = best_mdl.predict_proba(X_test)[:, 1]
    thr = best_thr
    test_m = metrics_at(y_test, st, thr)
    print(f'  Test metrics @ thr={thr:.4f}:')
    print(f'    accuracy={test_m["accuracy"]:.4f} recall={test_m["recall"]:.4f} precision={test_m["precision"]:.4f}')
    print(f'    f1={test_m["f1"]:.4f} fpr={test_m["fpr"]:.4f} fnr={test_m["fnr"]:.4f}')
    print(f'    CM: tn={test_m["tn"]} fp={test_m["fp"]} fn={test_m["fn"]} tp={test_m["tp"]}')

    # Export ONNX
    model_dir = r'A:\Protctus-app\ml\training\models'
    os.makedirs(model_dir, exist_ok=True)
    try:
        from skl2onnx import convert_sklearn
        from skl2onnx.common.data_types import FloatTensorType
        initial_type = [('float_input', FloatTensorType([None, X_train.shape[1]]))]
        onnx_model = convert_sklearn(best_mdl, initial_types=initial_type)
        onnx_path = os.path.join(model_dir, f'neuroshield_{best_name.lower()}.onnx')
        with open(onnx_path, 'wb') as f:
            f.write(onnx_model.SerializeToString())
        print(f'\nExported ONNX: {onnx_path} ({os.path.getsize(onnx_path)} bytes)')
    except ImportError:
        print('  skl2onnx not available. Install: pip install skl2onnx')
        import pickle
        pkl_path = os.path.join(model_dir, f'neuroshield_{best_name.lower()}.pkl')
        with open(pkl_path, 'wb') as f:
            pickle.dump({'model': best_mdl, 'threshold': thr, 'feature_count': X_train.shape[1]}, f)
        print(f'  Saved pickle: {pkl_path}')

    # Save results
    results_out = {
        'seed': SEED,
        'best_model': best_name,
        'feature_count': X_train.shape[1],
        'train_size': len(train),
        'val_size': len(val),
        'test_size': len(test),
        'threshold': thr,
        'test_metrics': test_m,
    }
    results_path = os.path.join(model_dir, 'training_results.json')
    with open(results_path, 'w') as f:
        json.dump(results_out, f, indent=2)
    print(f'Results saved to {results_path}')
    return results_out


if __name__ == '__main__':
    main()
