"""
Export the trained NeuroShield model to ONNX format for Android deployment.
"""
import os, sys, json, pickle
import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from feature_pipeline import extract_features, add_dynamic_features

MODEL_DIR = r'A:\Protctus-app\ml\training\models'
PKL_PATH = os.path.join(MODEL_DIR, 'neuroshield_histgradientboosting.pkl')
RESULTS_PATH = os.path.join(MODEL_DIR, 'training_results.json')
ONNX_OUT = os.path.join(MODEL_DIR, 'neuroshield_model.onnx')

# Load results
with open(RESULTS_PATH, 'r') as f:
    results = json.load(f)
print(f"Best model: {results['best_model']}")
print(f"Threshold: {results['threshold']:.4f}")
print(f"Test accuracy: {results['test_metrics']['accuracy']:.4f}")
print(f"Test recall: {results['test_metrics']['recall']:.4f}")

# Load model
with open(PKL_PATH, 'rb') as f:
    payload = pickle.load(f)
model = payload['model']
feature_count = payload['feature_count']
threshold = payload['threshold']

print(f"Model type: {type(model).__name__}")
print(f"Feature count: {feature_count}")
print(f"Threshold: {threshold:.4f}")

# Export to ONNX
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

initial_type = [('float_input', FloatTensorType([None, feature_count]))]
onnx_model = convert_sklearn(model, initial_types=initial_type, target_opset=12)

with open(ONNX_OUT, 'wb') as f:
    f.write(onnx_model.SerializeToString())

print(f'Exported ONNX model to {ONNX_OUT} ({os.path.getsize(ONNX_OUT)} bytes)')

# Verify the ONNX model loads and runs correctly
import onnxruntime as ort
sess = ort.InferenceSession(ONNX_OUT, providers=['CPUExecutionProvider'])

# Test with a few domains
test_domains = ['google.com', 'login-paypal.suspicious-site.tk', '192.168.1.1', 'xn--pple-43d.com']
from collections import defaultdict
tld_rates = {}  # empty rates for test - in production, these come from bundled data

def feat_with_dynamic(domain, tld_rates):
    f = extract_features(domain)
    return add_dynamic_features(f, domain, None, tld_rates)

for d in test_domains:
    f = feat_with_dynamic(d, tld_rates)
    X = np.array([f], dtype=np.float32)
    label, probs = sess.run(None, {'float_input': X})
    # probs is seq(map(int64, tensor(float)))
    pmap = probs[0] if isinstance(probs, list) else probs
    phish_prob = float(pmap.get(1, list(pmap.values())[-1] if pmap else 0.0))
    pred = int(label[0])
    print(f'  {d:45s} pred={pred} phish_prob={phish_prob:.4f}')

print('\nONNX export and verification complete!')
