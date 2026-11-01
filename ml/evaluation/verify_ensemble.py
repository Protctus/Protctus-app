"""
End-to-end verification of the Kotlin ensemble pipeline (simulated in Python).

Simulates EnsemblePhishingEngine.evaluate():
  1. Extract 53 features (dynamic 46-52 populated)
  2. Signature layer (exact match)
  3. Calibrated ONNX (LR) inference
  4. Cascade: skip LightGBM if ONNX confident
  5. Fuse ONNX + LightGBM + Heuristics + Signature
  6. Classify (PHISHING >=0.65, SUSPICIOUS >=0.30)
"""
import json, math, sys
import numpy as np
import onnxruntime as ort

sys.path.insert(0, 'ml/evaluation')
from feature_extractor import LexicalFeatureExtractor

FE = LexicalFeatureExtractor(dynamic_data_path='app/src/main/assets/neuroshield_dynamic_data.json')
SESSION = ort.InferenceSession('app/src/main/assets/neuroshield_model.onnx', providers=['CPUExecutionProvider'])
TEMPERATURE = 1.0177

PHISHING_SIGNATURES = {
    "paypal-security.suspicious.tk","apple-id-verify.ml","microsoft-account.gq",
    "netflix-login.cf","amazon-verify.ga","bankofamerica-secure.tk",
    "chase-online.ml","wellsfargo-verify.gq","update-apple-id.xyz",
    "signin-amazon.freehosting.com","secure-paypal-login.cf","verify-bankofamerica.tk",
}
THRESHOLD_PHISHING = 0.65
THRESHOLD_SUSPICIOUS = 0.30
CASCADE_LOW = 0.15
CASCADE_HIGH = 0.85
SIGNAL_ABSENT = 0.01


def calibrate(p):
    eps = 1e-6
    p = min(max(p, eps), 1 - eps)
    logit = math.log(p / (1 - p))
    return 1.0 / (1.0 + math.exp(-logit / TEMPERATURE))


def onnx_score(domain):
    f = np.array([FE.extract(domain)], dtype=np.float32)
    _, probs = SESSION.run(None, {'float_input': f})
    raw = probs[0].get(1, list(probs[0].values())[-1] if probs[0] else 0.0)
    return calibrate(float(raw))


def heuristic_score(domain):
    # Reuse a lightweight heuristic proxy: structural risk
    score = 0.0
    d = domain.lower()
    if d.count('-') >= 3: score += 0.2
    if d.count('.') >= 4: score += 0.15
    tld = d.split('.')[-1]
    suspicious_tlds = {'tk','ml','ga','cf','gq','buzz','top','icu','xyz','click','link'}
    if tld in suspicious_tlds: score += 0.15
    if 'xn--' in d: score += 0.15
    import re
    if re.match(r'^(\d{1,3}\.){3}\d{1,3}$', d): score += 0.4
    return min(score, 0.8)


def fuse(onnx, lgb, heur, sig):
    total_w, wsum = 0.0, 0.0
    W_ONNX, W_LGB, W_HEUR, W_SIG = 0.50, 0.30, 0.15, 0.05
    for s, w in [(onnx, W_ONNX), (lgb, W_LGB), (heur, W_HEUR), (sig, W_SIG)]:
        if s > SIGNAL_ABSENT:
            wsum += s * w
            total_w += w
    return wsum / total_w if total_w > 0 else 0.0


def evaluate(domain):
    d = domain.strip().lower()
    if not d:
        return 'REQUIRES_NETWORK_VERIFICATION', 0.0, []
    sig_match = d in PHISHING_SIGNATURES
    sig_score = 0.95 if sig_match else 0.0
    if sig_match:
        return 'PHISHING', sig_score, ['signature match']
    onnx = onnx_score(d)
    run_lgb = CASCADE_LOW < onnx < CASCADE_HIGH
    lgb = 0.0  # LightGBM not available in this sim; skipped
    heur = heuristic_score(d)
    fused = fuse(onnx, lgb, heur, sig_score)
    evidence = onnx > SIGNAL_ABSENT or heur > SIGNAL_ABSENT or sig_match
    if not evidence:
        return 'REQUIRES_NETWORK_VERIFICATION', 0.0, []
    if fused >= THRESHOLD_PHISHING:
        verdict = 'PHISHING'
    elif fused >= THRESHOLD_SUSPICIOUS:
        verdict = 'SUSPICIOUS'
    else:
        verdict = 'SAFE'
    inds = []
    if onnx > SIGNAL_ABSENT: inds.append(f'onnx={onnx:.2f}')
    if heur > SIGNAL_ABSENT: inds.append(f'heur={heur:.2f}')
    if sig_match: inds.append('signature')
    if not run_lgb: inds.append('lgb_skipped_battery')
    return verdict, fused, inds


if __name__ == '__main__':
    tests = [
        # (domain, expected_risk)
        ('google.com', 'safe'),
        ('paypal.com', 'safe'),
        ('microsoft.com', 'safe'),
        ('apple.com', 'safe'),
        ('example.com', 'safe'),
        ('login-paypal.suspicious.tk', 'phish'),
        ('secure-bank-verify.ml', 'phish'),
        ('paypal-verify.tk', 'phish'),
        ('update-apple-id.xyz', 'phish'),
        ('signin-amazon.freehosting.com', 'phish'),
        ('192.168.1.1', 'phish'),
        ('xn--pple-43d.com', 'phish'),
        ('paypal-security.suspicious.tk', 'phish'),  # signature
        ('apple-id-verify.ml', 'phish'),  # signature
        ('verify-bankofamerica.tk', 'phish'),  # signature
    ]
    print(f"{'Domain':45s} {'Verdict':18s} {'Score':7s} {'Indicators'}")
    print('-'*90)
    for domain, expected in tests:
        verdict, score, inds = evaluate(domain)
        flag = 'OK' if (expected=='phish') == (verdict in ('PHISHING','SUSPICIOUS')) or (expected=='safe') == (verdict=='SAFE') else 'CHECK'
        print(f"{domain:45s} {verdict:18s} {score:7.3f} {inds}  [{flag}]")
