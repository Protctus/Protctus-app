import onnxruntime as ort
import numpy as np
import sys
sys.path.insert(0, r"A:\Protctus-app\ml\training\scripts")
from feature_pipeline import extract_features, add_dynamic_features

path = r"A:\Protctus-app\ml\training\models\neuroshield_model.onnx"
sess = ort.InferenceSession(path, providers=['CPUExecutionProvider'])
print('Inputs:', [(i.name, i.shape, str(i.type)) for i in sess.get_inputs()])
print('Outputs:', [(o.name, o.shape, str(o.type)) for o in sess.get_outputs()])

# Test inference
X = np.array([[0.0]*53], dtype=np.float32)
label, probs = sess.run(None, {'float_input': X})
print('Label:', label)
pmap = probs[0] if isinstance(probs, list) else probs
print('pmap type:', type(pmap))
print('pmap:', pmap)
phish_prob = float(pmap.get(1, list(pmap.values())[-1] if pmap else 0.0))
print(f'Phishing probability: {phish_prob:.4f}')

# Test with various domains
test_domains = [
    ('google.com', 0),
    ('microsoft.com', 0),
    ('apple.com', 0),
    ('login-paypal.suspicious-site.tk', 1),
    ('secure-bank-verify.xyz', 1),
    ('appleid.account-update.ml', 1),
    ('paypal-security-update.tk', 1),
    ('192.168.1.1', 1),  # raw IP - suspicious
    ('xn--pple-43d.com', 1),  # punycode homograph
    ('facebook.com', 0),
    ('github.io', 0),
]

print('\nDomain inference tests:')
for domain, expected in test_domains:
    f = extract_features(domain)
    f = add_dynamic_features(f, domain, None, {})
    X = np.array([f], dtype=np.float32)
    label, probs = sess.run(None, {'float_input': X})
    pmap = probs[0] if isinstance(probs, list) else probs
    phish_prob = float(pmap.get(1, list(pmap.values())[-1] if pmap else 0.0))
    pred = int(label[0])
    status = "OK" if pred == expected else "MISMATCH"
    print(f'  {domain:45s} pred={pred} exp={expected} prob={phish_prob:.4f} [{status}]')
