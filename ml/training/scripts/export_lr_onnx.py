"""
Export LogisticRegression model to ONNX (simpler, better skl2onnx support).
"""
import os, sys, json, pickle
import numpy as np
from sklearn.linear_model import LogisticRegression

sys.path.insert(0, os.path.dirname(__file__))

MODEL_DIR = r'A:\Protctus-app\ml\training\models'
PKL_PATH = os.path.join(MODEL_DIR, 'neuroshield_histgradientboosting.pkl')
RESULTS_PATH = os.path.join(MODEL_DIR, 'training_results.json')
ONNX_OUT = os.path.join(MODEL_DIR, 'neuroshield_model.onnx')

# Load results
with open(RESULTS_PATH, 'r') as f:
    results = json.load(f)

# Load the HGB model to get feature count and threshold
with open(PKL_PATH, 'rb') as f:
    payload = pickle.load(f)
feature_count = payload['feature_count']
threshold = payload['threshold']

print(f"Feature count: {feature_count}")
print(f"HGB threshold: {threshold:.4f}")

# Train a fresh LogisticRegression on the same data
from feature_pipeline import extract_features, add_dynamic_features
from train_model_fast import load_domains, compute_tld_phishing_rates, feat_with_dynamic, build_feature_matrix, metrics_at, best_threshold_by_f1

DATA_DIR = r'A:\Protctus-app\ml\training\datasets'
PHISHING_FILE = r'A:\Protctus-app\ml\evaluation\fixtures\phishing-domains-ACTIVE.txt'
UMBRELLA_FILE = os.path.join(DATA_DIR, 'top-1m.csv')
VVIP_FILE = r'A:\Protctus-app\app\src\main\assets\vvip_domains.txt'
SEED = 42

print('\nLoading data...')
phish = load_domains(PHISHING_FILE, label=1, max_samples=60000)
benign_umbrella = load_domains(UMBRELLA_FILE, label=0, max_samples=60000)
benign_vvip = load_domains(VVIP_FILE, label=0, max_samples=60000)
all_benign = benign_umbrella + benign_vvip
benign_set = {d for d, _ in all_benign}
phish_set = {d for d, _ in phish}
phish = [(d, l) for d, l in phish if d not in benign_set]
all_benign = [(d, l) for d, l in all_benign if d not in phish_set]

print(f'phishing={len(phish)}, benign={len(all_benign)}')

# TLD rates
tld_rates = compute_tld_phishing_rates(phish, all_benign)

# Split
import random
random.seed(SEED)
np.random.seed(SEED)

def split_domains(domains, tr=0.6, va=0.2):
    n = len(domains)
    nt = int(n * tr)
    nv = int(n * va)
    return domains[:nt], domains[nt:nt+nv], domains[nt+nv:]

random.shuffle(phish)
random.shuffle(all_benign)
(pt, pv, pte), (bt, bv, bte) = split_domains(phish), split_domains(all_benign)
train = [(d, 1) for (d, _) in pt] + [(d, 0) for (d, _) in bt]
val = [(d, 1) for (d, _) in pv] + [(d, 0) for (d, _) in bv]
test = [(d, 1) for (d, _) in pte] + [(d, 0) for (d, _) in bte]
random.shuffle(train)
random.shuffle(val)
random.shuffle(test)

print(f'Splits: train={len(train)}, val={len(val)}, test={len(test)}')

# Features
print('Building feature matrices...')
X_train = build_feature_matrix(train, tld_rates)
y_train = np.array([l for _, l in train], dtype=int)
X_val = build_feature_matrix(val, tld_rates)
y_val = np.array([l for _, l in val], dtype=int)
X_test = build_feature_matrix(test, tld_rates)
y_test = np.array([l for _, l in test], dtype=int)

# Train LogisticRegression
print('Training LogisticRegression...')
lr = LogisticRegression(max_iter=3000, class_weight='balanced', random_state=SEED, n_jobs=1)
lr.fit(X_train, y_train)

# Threshold selection on val
sv = lr.predict_proba(X_val)[:, 1]
best = best_threshold_by_f1(y_val, sv)
print(f'Val threshold: {best["threshold"]:.4f}')

# Test
st = lr.predict_proba(X_test)[:, 1]
test_m = metrics_at(y_test, st, best['threshold'])
print(f'Test: acc={test_m["accuracy"]:.4f} rec={test_m["recall"]:.4f} prec={test_m["precision"]:.4f} f1={test_m["f1"]:.4f} fpr={test_m["fpr"]:.4f}')
print(f'CM: tn={test_m["tn"]} fp={test_m["fp"]} fn={test_m["fn"]} tp={test_m["tp"]}')

# Export to ONNX
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

initial_type = [('float_input', FloatTensorType([None, feature_count]))]
onnx_model = convert_sklearn(lr, initial_types=initial_type, target_opset=12)

with open(ONNX_OUT, 'wb') as f:
    f.write(onnx_model.SerializeToString())
print(f'\nExported ONNX to {ONNX_OUT} ({os.path.getsize(ONNX_OUT)} bytes)')

# Verify
import onnxruntime as ort
sess = ort.InferenceSession(ONNX_OUT, providers=['CPUExecutionProvider'])
print('\nVerification:')
for d in ['google.com', 'login-paypal.suspicious-site.tk', '192.168.1.1', 'xn--pple-43d.com']:
    f = extract_features(d)
    f = add_dynamic_features(f, d, None, {})
    X = np.array([f], dtype=np.float32)
    label, probs = sess.run(None, {'float_input': X})
    pmap = probs[0] if isinstance(probs, list) else probs
    phish_prob = float(pmap.get(1, list(pmap.values())[-1] if pmap else 0.0))
    pred = int(label[0])
    print(f'  {d:45s} pred={pred} phish_prob={phish_prob:.4f}')
