"""
Cascade training pipeline for 0.95/0.95 target.

Strategy: Two-stage cascade
  Stage 1: High-recall model (catch almost all phishing, accept high FPR)
  Stage 2: High-precision model (filter FPs from stage 1)

Overall recall = R1 * R2
Overall FPR = FPR1 * FPR2

Target: accuracy >= 0.95 AND recall >= 0.95
With balanced classes: need recall >= 0.95 AND FPR <= 0.05

If R1=0.99, R2=0.96 → recall=0.9504
If FPR1=0.30, FPR2=0.15 → FPR=0.045 ✓
"""
import os, sys, json, time, random
from collections import defaultdict
import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier
from sklearn.metrics import confusion_matrix
from sklearn.preprocessing import PolynomialFeatures
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
    """Find threshold achieving target recall with minimum FPR."""
    best = None
    for thr in np.linspace(0.0, 1.0, 10001):
        m = metrics_at(y_val, scores, float(thr))
        if m['recall'] >= target_recall:
            if best is None or m['fpr'] < best['fpr']:
                best = dict(m, threshold=float(thr))
    return best

def cascade_predict(stage1_scores, stage2_scores, thr1, thr2):
    """Cascade: stage1 must pass AND stage2 must pass for positive."""
    stage1_pos = (stage1_scores >= thr1)
    stage2_pos = (stage2_scores >= thr2)
    return (stage1_pos & stage2_pos).astype(int)

def main():
    print('=' * 60)
    print('Cascade Training for 0.95/0.95 Target')
    print('=' * 60)

    MAX_PHISH = 100000
    MAX_BENIGN = 100000

    print('\nLoading data...')
    phish = load_domains(PHISHING_FILE, label=1, max_samples=MAX_PHISH)
    benign = load_domains(UMBRELLA_FILE, label=0, max_samples=MAX_BENIGN)
    print(f'  phishing: {len(phish)}, benign: {len(benign)}')

    # Dedupe
    benign_set = {d for d, _ in benign}
    phish_set = {d for d, _ in phish}
    overlap = benign_set & phish_set
    phish = [(d, l) for d, l in phish if d not in benign_set]
    benign = [(d, l) for d, l in benign if d not in phish_set]
    print(f'  after dedup: phishing={len(phish)}, benign={len(benign)}, overlap={len(overlap)}')

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

    # === STAGE 1: High-recall model ===
    print('\n' + '=' * 60)
    print('STAGE 1: High-recall model (target recall >= 0.99)')
    print('=' * 60)

    s1 = HistGradientBoostingClassifier(max_iter=500, max_depth=12, class_weight='balanced', random_state=SEED)
    s1.fit(X_train, y_train)
    s1_val_scores = s1.predict_proba(X_val)[:, 1]

    # Find threshold for recall >= 0.99
    s1_best = find_threshold_for_recall(y_val, s1_val_scores, target_recall=0.99)
    if s1_best is None:
        print('  WARNING: Cannot achieve 0.99 recall on val. Using max-recall threshold.')
        s1_best = {'threshold': 0.0, **metrics_at(y_val, s1_val_scores, 0.0)}
    
    s1_test_scores = s1.predict_proba(X_test)[:, 1]
    s1_test = metrics_at(y_test, s1_test_scores, s1_best['threshold'])
    print(f'  Stage 1 threshold: {s1_best["threshold"]:.4f}')
    print(f'  Stage 1 test: acc={s1_test["accuracy"]:.4f} rec={s1_test["recall"]:.4f} prec={s1_test["precision"]:.4f} fpr={s1_test["fpr"]:.4f}')

    # === STAGE 2: High-precision model ===
    print('\n' + '=' * 60)
    print('STAGE 2: High-precision model (filter stage-1 FPs)')
    print('=' * 60)

    # Train stage 2 on data that stage 1 flagged as positive
    # Get stage 1 predictions on training data
    s1_train_scores = s1.predict_proba(X_train)[:, 1]
    s1_train_pred = (s1_train_scores >= s1_best['threshold']).astype(int)
    
    # Stage 2 training data: only samples that stage 1 flagged as positive
    s2_train_mask = s1_train_pred == 1
    X_s2_train = X_train[s2_train_mask]
    y_s2_train = y_train[s2_train_mask]
    
    print(f'  Stage 1 flagged {s2_train_mask.sum()} of {len(train)} training samples as positive')
    print(f'  Of those, {y_s2_train.sum()} are actually phishing, {len(y_s2_train)-y_s2_train.sum()} are benign')

    # Train stage 2 model (LogisticRegression for precision)
    s2 = LogisticRegression(max_iter=5000, class_weight='balanced', random_state=SEED, n_jobs=1)
    s2.fit(X_s2_train, y_s2_train)
    
    # Stage 2 validation: only samples stage 1 flagged as positive
    s1_val_pred = (s1_val_scores >= s1_best['threshold']).astype(int)
    s2_val_mask = s1_val_pred == 1
    X_s2_val = X_val[s2_val_mask]
    y_s2_val = y_val[s2_val_mask]
    
    print(f'  Stage 1 flagged {s2_val_mask.sum()} of {len(val)} val samples as positive')
    print(f'  Of those, {y_s2_val.sum()} are actually phishing, {len(y_s2_val)-y_s2_val.sum()} are benign')

    if len(y_s2_val) > 0:
        s2_val_scores = s2.predict_proba(X_s2_val)[:, 1]
        
        # Find threshold for stage 2: maximize precision while keeping recall >= 0.96
        best_s2 = None
        for thr in np.linspace(0.0, 1.0, 10001):
            m = metrics_at(y_s2_val, s2_val_scores, float(thr))
            if m['recall'] >= 0.96:
                if best_s2 is None or m['precision'] > best_s2['precision']:
                    best_s2 = dict(m, threshold=float(thr))
        
        if best_s2 is None:
            print('  WARNING: Cannot achieve 0.96 recall on val for stage 2.')
            best_s2 = {'threshold': 0.5, **metrics_at(y_s2_val, s2_val_scores, 0.5)}
        
        # Stage 2 test
        s1_test_pred = (s1_test_scores >= s1_best['threshold']).astype(int)
        s2_test_mask = s1_test_pred == 1
        X_s2_test = X_test[s2_test_mask]
        y_s2_test = y_test[s2_test_mask]
        
        print(f'  Stage 1 flagged {s2_test_mask.sum()} of {len(test)} test samples as positive')
        
        if len(y_s2_test) > 0:
            s2_test_scores = s2.predict_proba(X_s2_test)[:, 1]
            s2_test = metrics_at(y_s2_test, s2_test_scores, best_s2['threshold'])
            print(f'  Stage 2 threshold: {best_s2["threshold"]:.4f}')
            print(f'  Stage 2 test (on stage-1 positives): acc={s2_test["accuracy"]:.4f} rec={s2_test["recall"]:.4f} prec={s2_test["precision"]:.4f} fpr={s2_test["fpr"]:.4f}')
            
            # === CASCADE COMBINED ===
            print('\n' + '=' * 60)
            print('CASCADE COMBINED RESULTS')
            print('=' * 60)
            
            # Apply cascade to full test set
            cascade_pred = cascade_predict(s1_test_scores, s2_test_scores, s1_best['threshold'], best_s2['threshold'])
            
            tn, fp, fn, tp = confusion_matrix(y_test, cascade_pred, labels=[0, 1]).ravel()
            cascade_acc = (tp + tn) / max(1, tp + tn + fp + fn)
            cascade_rec = tp / max(1, tp + fn)
            cascade_prec = tp / max(1, tp + fp)
            cascade_fpr = fp / max(1, fp + tn)
            cascade_fnr = fn / max(1, fn + tp)
            cascade_f1 = 2 * cascade_prec * cascade_rec / max(1e-9, (cascade_prec + cascade_rec))
            
            print(f'  Cascade threshold1={s1_best["threshold"]:.4f} threshold2={best_s2["threshold"]:.4f}')
            print(f'  Cascade: acc={cascade_acc:.4f} rec={cascade_rec:.4f} prec={cascade_prec:.4f} f1={cascade_f1:.4f} fpr={cascade_fpr:.4f} fnr={cascade_fnr:.4f}')
            print(f'  CM: tn={tn} fp={fp} fn={fn} tp={tp}')
            print(f'  Target acc>=0.95: {"PASS" if cascade_acc >= 0.95 else "FAIL"}')
            print(f'  Target rec>=0.95: {"PASS" if cascade_rec >= 0.95 else "FAIL"}')

    # Also try single model with polynomial features
    print('\n' + '=' * 60)
    print('Polynomial Features + HGB')
    print('=' * 60)
    
    poly = PolynomialFeatures(degree=2, interaction_only=True, include_bias=False)
    X_train_poly = poly.fit_transform(X_train)
    X_val_poly = poly.transform(X_val)
    X_test_poly = poly.transform(X_test)
    print(f'  Features: {X_train.shape[1]} → {X_train_poly.shape[1]}')
    
    poly_hgb = HistGradientBoostingClassifier(max_iter=500, max_depth=8, class_weight='balanced', random_state=SEED)
    poly_hgb.fit(X_train_poly, y_train)
    poly_val_scores = poly_hgb.predict_proba(X_val_poly)[:, 1]
    poly_best = find_threshold_for_recall(y_val, poly_val_scores, target_recall=0.95)
    
    if poly_best is not None:
        poly_test_scores = poly_hgb.predict_proba(X_test_poly)[:, 1]
        poly_test = metrics_at(y_test, poly_test_scores, poly_best['threshold'])
        print(f'  Poly HGB threshold: {poly_best["threshold"]:.4f}')
        print(f'  Poly HGB test: acc={poly_test["accuracy"]:.4f} rec={poly_test["recall"]:.4f} prec={poly_test["precision"]:.4f} f1={poly_test["f1"]:.4f} fpr={poly_test["fpr"]:.4f}')
        print(f'  Target acc>=0.95: {"PASS" if poly_test["accuracy"] >= 0.95 else "FAIL"}')
        print(f'  Target rec>=0.95: {"PASS" if poly_test["recall"] >= 0.95 else "FAIL"}')

if __name__ == '__main__':
    main()
