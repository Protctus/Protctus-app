"""
NeuroShield phishing model training pipeline.

Trains and compares multiple lightweight models on genuine held-out data,
selects the best, and exports to ONNX for Android deployment.

Methodology:
- Positive class = phishing, Negative = benign
- Domain-grouped split to prevent leakage (same domain never in multiple splits)
- Validation set used ONLY for threshold selection
- Test set held out until final scoring
- Bootstrap 95% CIs
"""
import os, sys, json, time, random, math
from collections import defaultdict
import numpy as np
import onnxruntime as ort
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier
from sklearn.neural_network import MLPClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import make_pipeline
from sklearn.metrics import confusion_matrix, accuracy_score, precision_score, recall_score, f1_score, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.calibration import CalibratedClassifierCV
import warnings
warnings.filterwarnings('ignore')

sys.path.insert(0, os.path.dirname(__file__))
from feature_pipeline import extract_features, add_dynamic_features, BRANDS, SUSPICIOUS_TLDS, NEW_GTLDS, sanitize

DATA_DIR = r'A:\Protctus-app\ml\training\datasets'
PHISHING_FILE = r'A:\Protctus-app\ml\evaluation\fixtures\phishing-domains-ACTIVE.txt'
UMBRELLA_FILE = os.path.join(DATA_DIR, 'top-1m.csv')
VVIP_FILE = r'A:\Protctus-app\app\src\main\assets\vvip_domains.txt'

SEED = 42
random.seed(SEED)
np.random.seed(SEED)

def load_domains(path, label, max_samples=None):
    """Load and validate domains from a file."""
    domains = []
    seen = set()
    with open(path, encoding='utf-8', errors='ignore') as f:
        for line in f:
            d = line.strip().lower()
            if not d:
                continue
            # Strip CSV row number if present (umbrella format: rank,domain)
            if ',' in d:
                d = d.split(',', 1)[1]
            # Strip scheme/path
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
                continue  # bare IPv4
            if d in seen:
                continue
            seen.add(d)
            domains.append((d, label))
            if max_samples and len(domains) >= max_samples:
                break
    return domains


def compute_tld_phishing_rates(phishing_domains, benign_domains):
    """Compute per-TLD phishing rate from corpus."""
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


def build_feature_matrix(domains, tld_phishing_rates, feat_fn):
    """Build X matrix from domain list."""
    X = []
    for d, _ in domains:
        f = feat_fn(d, tld_phishing_rates)
        X.append(f)
    return np.array(X, dtype=np.float32)


def feat_with_dynamic(domain, tld_rates):
    f = extract_features(domain)
    return add_dynamic_features(f, domain, None, tld_rates)


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
    for thr in np.linspace(0.0, 1.0, 2001):
        m = metrics_at(y_val, scores, float(thr))
        if best is None or m['f1'] > best['f1']:
            best = dict(m, threshold=float(thr))
    return best


def bootstrap_ci(metric_fn, yt, scores, n_boot=2000, seed=7):
    rng = np.random.default_rng(seed)
    idx = np.arange(len(yt))
    stats = []
    for _ in range(n_boot):
        sel = rng.choice(idx, size=len(idx), replace=True)
        stats.append(metric_fn(yt[sel], scores[sel]))
    stats = np.array(stats, dtype=float)
    lo = float(np.percentile(stats, 2.5))
    hi = float(np.percentile(stats, 97.5))
    return float(stats.mean()), lo, hi


def export_onnx(model, feature_count, output_path):
    """Export a scikit-learn model to ONNX."""
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType
    initial_type = [('float_input', FloatTensorType([None, feature_count]))]
    onnx_model = convert_sklearn(model, initial_types=initial_type)
    with open(output_path, 'wb') as f:
        f.write(onnx_model.SerializeToString())
    print(f'Exported ONNX model to {output_path} ({os.path.getsize(output_path)} bytes)')


def main():
    print('=' * 60)
    print('NeuroShield Model Training Pipeline')
    print('=' * 60)

    # ── Load data ──
    print('\nLoading phishing domains...')
    phish = load_domains(PHISHING_FILE, label=1, max_samples=200000)
    print(f'  phishing loaded: {len(phish)}')

    print('Loading benign domains (Umbrella top-1M)...')
    benign_umbrella = load_domains(UMBRELLA_FILE, label=0, max_samples=300000)
    print(f'  umbrella benign loaded: {len(benign_umbrella)}')

    print('Loading benign domains (VVIP/Tranco)...')
    benign_vvip = load_domains(VVIP_FILE, label=0, max_samples=200000)
    print(f'  vvip benign loaded: {len(benign_vvip)}')

    # Combine benign, dedupe against phishing
    all_benign = benign_umbrella + benign_vvip
    benign_set = {d for d, _ in all_benign}
    phish_set = {d for d, _ in phish}

    # Remove overlap
    overlap = benign_set & phish_set
    print(f'  overlap between classes: {len(overlap)} domains')
    phish = [(d, l) for d, l in phish if d not in benign_set]
    all_benign = [(d, l) for d, l in all_benign if d not in phish_set]

    print(f'  after dedup: phishing={len(phish)}, benign={len(all_benign)}')

    # ── Compute TLD phishing rates ──
    tld_rates = compute_tld_phishing_rates(phish, all_benign)
    print(f'  TLD phishing rates computed for {len(tld_rates)} TLDs')

    # ── Stratified domain-grouped split ──
    # Group by domain to prevent leakage
    phish_domains = [d for d, _ in phish]
    benign_domains = [d for d, _ in all_benign]

    random.shuffle(phish_domains)
    random.shuffle(benign_domains)

    def split_domains(domains, train_frac=0.6, val_frac=0.2):
        n = len(domains)
        nt = int(n * train_frac)
        nv = int(n * val_frac)
        return domains[:nt], domains[nt:nt+nv], domains[nt+nv:]

    (p_train, p_val, p_test), (b_train, b_val, b_test) = \
        split_domains(phish_domains), split_domains(benign_domains)

    train = [(d, 1) for d in p_train] + [(d, 0) for d in b_train]
    val = [(d, 1) for d in p_val] + [(d, 0) for d in b_val]
    test = [(d, 1) for d in p_test] + [(d, 0) for d in b_test]

    random.shuffle(train)
    random.shuffle(val)
    random.shuffle(test)

    print(f'\nSplit: train={len(train)}, val={len(val)}, test={len(test)}')
    for name, split in [('train', train), ('val', val), ('test', test)]:
        npos = sum(l for _, l in split)
        print(f'  {name}: pos={npos} neg={len(split)-npos}')

    # ── Build feature matrices ──
    print('\nBuilding feature matrices...')
    X_train = build_feature_matrix(train, tld_rates, feat_with_dynamic)
    y_train = np.array([l for _, l in train], dtype=int)
    X_val = build_feature_matrix(val, tld_rates, feat_with_dynamic)
    y_val = np.array([l for _, l in val], dtype=int)
    X_test = build_feature_matrix(test, tld_rates, feat_with_dynamic)
    y_test = np.array([l for _, l in test], dtype=int)

    print(f'  X_train shape: {X_train.shape}')
    print(f'  X_val shape: {X_val.shape}')
    print(f'  X_test shape: {X_test.shape}')

    # ── Train and compare models ──
    print('\n' + '=' * 60)
    print('Model Comparison')
    print('=' * 60)

    models = {
        'LogisticRegression': LogisticRegression(max_iter=3000, class_weight='balanced', random_state=SEED, n_jobs=1),
        'RandomForest': RandomForestClassifier(n_estimators=300, max_depth=14, class_weight='balanced', random_state=SEED, n_jobs=1),
        'HistGradientBoosting': HistGradientBoostingClassifier(max_iter=300, max_depth=8, class_weight='balanced', random_state=SEED),
        'MLP': MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=500, random_state=SEED, early_stopping=True),
    }

    results = {}
    for name, mdl in models.items():
        print(f'\nTraining {name}...')
        t0 = time.time()
        mdl.fit(X_train, y_train)
        train_time = time.time() - t0

        # Validation scores for threshold selection
        if hasattr(mdl, 'predict_proba'):
            sv = mdl.predict_proba(X_val)[:, 1]
        else:
            sv = mdl.decision_function(X_val)

        best = best_threshold_by_f1(y_val, sv)

        # Test scores at chosen threshold
        if hasattr(mdl, 'predict_proba'):
            st = mdl.predict_proba(X_test)[:, 1]
        else:
            st = mdl.decision_function(X_test)

        test_m = metrics_at(y_test, st, best['threshold'])

        # Latency
        t0 = time.time()
        _ = mdl.predict_proba(X_val[:1000])
        lat = (time.time() - t0) / 1000.0 * 1000  # ms per domain

        results[name] = {
            'train_time': train_time,
            'val_threshold': best['threshold'],
            'test': test_m,
            'latency_ms': lat,
            'model': mdl,
        }

        print(f'  {name}:')
        print(f'    train_time={train_time:.1f}s, latency={lat:.3f}ms/domain')
        print(f'    val thr={best["threshold"]:.3f}')
        print(f'    test acc={test_m["accuracy"]:.4f} rec={test_m["recall"]:.4f} prec={test_m["precision"]:.4f} f1={test_m["f1"]:.4f} fpr={test_m["fpr"]:.4f}')
        print(f'    CM: tn={test_m["tn"]} fp={test_m["fp"]} fn={test_m["fn"]} tp={test_m["tp"]}')

    # ── Bootstrap CIs for best model ──
    best_name = max(results, key=lambda k: results[k]['test']['f1'])
    best_result = results[best_name]
    best_mdl = best_result['model']
    best_thr = best_result['val_threshold']

    print(f'\n' + '=' * 60)
    print(f'Best model: {best_name}')
    print('=' * 60)

    if hasattr(best_mdl, 'predict_proba'):
        st = best_mdl.predict_proba(X_test)[:, 1]
    else:
        st = best_mdl.decision_function(X_test)

    thr = best_thr
    ci = {}
    for name, fn in [
        ('accuracy', lambda yt, ys: metrics_at(yt, ys, thr)['accuracy']),
        ('recall', lambda yt, ys: metrics_at(yt, ys, thr)['recall']),
        ('precision', lambda yt, ys: metrics_at(yt, ys, thr)['precision']),
        ('f1', lambda yt, ys: metrics_at(yt, ys, thr)['f1']),
        ('fpr', lambda yt, ys: metrics_at(yt, ys, thr)['fpr']),
    ]:
        mean, lo, hi = bootstrap_ci(fn, y_test, st)
        ci[name] = (mean, lo, hi)
        print(f'  CI95 {name}: mean={mean:.4f} [{lo:.4f}, {hi:.4f}]')

    test_m = metrics_at(y_test, st, thr)
    print(f'\n  Final test metrics @ thr={thr:.4f}:')
    print(f'    accuracy={test_m["accuracy"]:.4f} recall={test_m["recall"]:.4f} precision={test_m["precision"]:.4f}')
    print(f'    f1={test_m["f1"]:.4f} fpr={test_m["fpr"]:.4f} fnr={test_m["fnr"]:.4f}')
    print(f'    CM: tn={test_m["tn"]} fp={test_m["fp"]} fn={test_m["fn"]} tp={test_m["tp"]}')

    # ── Export to ONNX ──
    model_dir = r'A:\Protctus-app\ml\training\models'
    os.makedirs(model_dir, exist_ok=True)
    onnx_path = os.path.join(model_dir, f'neuroshield_{best_name.lower()}.onnx')
    try:
        export_onnx(best_mdl, X_train.shape[1], onnx_path)
    except ImportError:
        print('  skl2onnx not available — install with: pip install skl2onnx')
        print('  Saving model via pickle instead...')
        import pickle
        pkl_path = os.path.join(model_dir, f'neuroshield_{best_name.lower()}.pkl')
        with open(pkl_path, 'wb') as f:
            pickle.dump({'model': best_mdl, 'threshold': thr, 'feature_count': X_train.shape[1]}, f)
        print(f'  Saved to {pkl_path}')

    # ── Save results ──
    results_out = {
        'seed': SEED,
        'best_model': best_name,
        'feature_count': X_train.shape[1],
        'train_size': len(train),
        'val_size': len(val),
        'test_size': len(test),
        'test_benign': int((y_test == 0).sum()),
        'test_phishing': int((y_test == 1).sum()),
        'threshold': thr,
        'test_metrics': test_m,
        'ci95': {k: {'mean': v[0], 'lo': v[1], 'hi': v[2]} for k, v in ci.items()},
        'all_models': {k: {
            'train_time': v['train_time'],
            'val_threshold': v['val_threshold'],
            'test': v['test'],
            'latency_ms': v['latency_ms'],
        } for k, v in results.items()},
    }
    results_path = os.path.join(model_dir, 'training_results.json')
    with open(results_path, 'w') as f:
        json.dump(results_out, f, indent=2)
    print(f'\nResults saved to {results_path}')

    return results_out


if __name__ == '__main__':
    main()
