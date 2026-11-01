"""
External dataset validation for NeuroShield ONNX model.

Evaluates the CURRENT production ONNX model on an INDEPENDENT external
phishing dataset (OpenPhish feed) + benign domains, using the threshold
selected on the held-out validation set (0.5730) — NOT tuned on external data.

Positive class = phishing.
"""
import sys, os, math, json, time
import numpy as np
import onnxruntime as ort
import urllib.parse

sys.path.insert(0, 'ml/evaluation')
from feature_extractor import LexicalFeatureExtractor

FE = LexicalFeatureExtractor(dynamic_data_path='app/src/main/assets/neuroshield_dynamic_data.json')
SESSION = ort.InferenceSession('app/src/main/assets/neuroshield_model.onnx', providers=['CPUExecutionProvider'])

# Threshold from held-out validation (max-F1). NOT tuned on external data.
THRESHOLD = 0.5730

def sanitize_host(url):
    """Production-style host extraction: strip scheme/www/port/path/query/fragment."""
    u = url.strip().lower()
    for pre in ('http://', 'https://'):
        if u.startswith(pre):
            u = u[len(pre):]
            break
    # strip path/query/fragment
    for sep in ('/', '?', '#'):
        i = u.find(sep)
        if i >= 0:
            u = u[:i]
    # strip port (only if after-colon is all digits)
    i = u.rfind(':')
    if i >= 0:
        after = u[i+1:]
        if after and all(c.isdigit() for c in after):
            u = u[:i]
    u = u.rstrip('.')
    if u.startswith('www.'):
        u = u[4:]
    try:
        import idna
        u = idna.encode(u).decode('ascii').lower()
    except Exception:
        pass
    return u

def onnx_score(domain):
    f = np.array([FE.extract(domain)], dtype=np.float32)
    _, probs = SESSION.run(None, {'float_input': f})
    raw = probs[0].get(1, list(probs[0].values())[-1] if probs[0] else 0.0)
    # temperature calibration T=1.0177
    T = 1.0177
    eps = 1e-6
    p = min(max(float(raw), eps), 1 - eps)
    logit = math.log(p / (1 - p))
    return 1.0 / (1.0 + math.exp(-logit / T))

def confusion(yt, yp):
    tn, fp, fn, tp = __import__('sklearn.metrics', fromlist=['confusion_matrix']).confusion_matrix(yt, yp, labels=[0,1]).ravel()
    return tn, fp, fn, tp

def metrics_at(yt, scores, thr):
    yp = (scores >= thr).astype(int)
    tn, fp, fn, tp = confusion(yt, yp)
    acc = (tp+tn)/max(1, tp+tn+fp+fn)
    rec = tp/max(1, tp+fn)
    prec = tp/max(1, tp+fp)
    f1 = 2*prec*rec/max(1e-9, prec+rec)
    fpr = fp/max(1, fp+tn)
    fnr = fn/max(1, fn+tp)
    return dict(threshold=thr, tn=int(tn), fp=int(fp), fn=int(fn), tp=int(tp),
                accuracy=float(acc), recall=float(rec), precision=float(prec),
                f1=float(f1), fpr=float(fpr), fnr=float(fnr))

# ---- Load external phishing (OpenPhish) ----
phish_urls = []
with open('ml/evaluation/fixtures/openphish_feed.txt', encoding='utf-8', errors='ignore') as f:
    for line in f:
        u = line.strip()
        if u:
            phish_urls.append(u)
print(f'OpenPhish URLs loaded: {len(phish_urls)}')

phish_hosts = [sanitize_host(u) for u in phish_urls]
phish_hosts = [h for h in phish_hosts if h and '.' in h]
print(f'OpenPhish valid hostnames: {len(phish_hosts)}')

# ---- Load benign ----
benign = []
with open('app/src/main/assets/vvip_domains.txt', encoding='utf-8', errors='ignore') as f:
    for line in f:
        d = line.strip().lower()
        if d and '.' in d and ' ' not in d:
            benign.append(d)
print(f'Benign domains loaded: {len(benign)}')

# Remove overlaps
benign_set = set(benign)
phish_hosts = [h for h in phish_hosts if h not in benign_set]
print(f'OpenPhish after overlap removal: {len(phish_hosts)}')

# Use a balanced benign subset (sample same count as phish for balanced eval, but keep all for FPR)
import random
rng = random.Random(42)
benign_sample = rng.sample(benign, min(len(benign), len(phish_hosts)*3))  # 3:1 benign:phish

print(f'Benign used: {len(benign_sample)}')
print(f'Phish used: {len(phish_hosts)}')

# ---- Evaluate ----
print('\nScoring benign ...')
benign_scores = [onnx_score(d) for d in benign_sample]
print('Scoring phishing ...')
phish_scores = [onnx_score(d) for d in phish_hosts]

yt = np.array([0]*len(benign_sample) + [1]*len(phish_hosts), dtype=int)
scores = np.array(benign_scores + phish_scores, dtype=float)

m = metrics_at(yt, scores, THRESHOLD)
print(f'\n=== External OpenPhish Validation (threshold={THRESHOLD}, from held-out val, NOT tuned) ===')
print(f'Test: benign={len(benign_sample)} phishing={len(phish_hosts)}')
print(f'Accuracy={m["accuracy"]:.4f} Recall={m["recall"]:.4f} Precision={m["precision"]:.4f}')
print(f'F1={m["f1"]:.4f} FPR={m["fpr"]:.4f} FNR={m["fnr"]:.4f}')
print(f'CM TN={m["tn"]} FP={m["fp"]} FN={m["fn"]} TP={m["tp"]}')

# Score distributions
print(f'\nBenign score range: min={min(benign_scores):.4f} max={max(benign_scores):.4f}')
print(f'Phish score range: min={min(phish_scores):.4f} max={max(phish_scores):.4f}')

# Show some misclassifications
fp = [(d, s) for d, s in zip(benign_sample, benign_scores) if s >= THRESHOLD]
fn = [(d, s) for d, s in zip(phish_hosts, phish_scores) if s < THRESHOLD]
print(f'\nFalse positives ({len(fp)}):')
for d, s in fp[:10]:
    print(f'  {s:.4f}  {d}')
print(f'False negatives ({len(fn)}):')
for d, s in fn[:10]:
    print(f'  {s:.4f}  {d}')

# Save results
result = {
    'dataset': 'OpenPhish external (independent)',
    'phish_source': 'https://www.openphish.com/feed.txt',
    'benign_source': 'app/src/main/assets/vvip_domains.txt',
    'threshold': THRESHOLD,
    'threshold_source': 'held-out validation (max-F1), NOT tuned on external',
    'test_benign': len(benign_sample),
    'test_phishing': len(phish_hosts),
    'metrics': m,
    'benign_score_min': min(benign_scores), 'benign_score_max': max(benign_scores),
    'phish_score_min': min(phish_scores), 'phish_score_max': max(phish_scores),
    'false_positives': fp[:50],
    'false_negatives': fn[:50],
}
os.makedirs('ml/evaluation/results', exist_ok=True)
with open('ml/evaluation/results/external_openphish_validation.json', 'w') as f:
    json.dump(result, f, indent=2)
print('\nSaved ml/evaluation/results/external_openphish_validation.json')
