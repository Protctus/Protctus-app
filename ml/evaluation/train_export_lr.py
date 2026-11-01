"""
Train a production phishing-detection model and export to ONNX.

Trains LogisticRegression (best accuracy/battery trade-off) on the same
53 lexical features used by production, with dynamic features 46-52
populated from neuroshield_dynamic_data.json (matching production).

Exports to ONNX (float32, input "float_input [None,53]") to replace the
weak/degenerate 1 KB neuroshield_model.onnx asset.

Also fits a temperature-scaling calibration on validation data so the
exported model outputs well-calibrated probabilities (Platt-like).

Usage:
  python ml/evaluation/train_export_lr.py \
    --benign app/src/main/assets/vvip_domains.txt \
    --phishing ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \
    --dynamic-data app/src/main/assets/neuroshield_dynamic_data.json \
    --out app/src/main/assets/neuroshield_model.onnx \
    --calibration-out ml/evaluation/results/lr_temperature.txt \
    --seed 42
"""
import argparse, json, math, os, random, sys

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)
from feature_extractor import LexicalFeatureExtractor

SEED = 42


def load_domains(path, label, max_samples=None):
    domains, seen = [], set()
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            d = line.strip().lower()
            if not d:
                continue
            for pre in ("http://", "https://"):
                if d.startswith(pre):
                    d = d[len(pre):]
            d = d.split("/")[0].split("?")[0].split("#")[0].rstrip(".")
            if not d or " " in d or "." not in d or len(d) > 253:
                continue
            parts = d.split(".")
            if len(parts) == 4 and all(p.isdigit() for p in parts):
                continue
            if d in seen:
                continue
            seen.add(d)
            domains.append((d, label))
            if max_samples and len(domains) >= max_samples:
                break
    return domains


def registrable_domain(d):
    parts = d.split(".")
    return ".".join(parts[-2:]) if len(parts) >= 2 else d


def split_grouped(phish, benign, rng, train_frac=0.60, val_frac=0.20):
    def grouped(lst):
        groups = {}
        for d, l in lst:
            groups.setdefault(registrable_domain(d), []).append((d, l))
        items = list(groups.values())
        rng.shuffle(items)
        n = len(items)
        nt = int(n * train_frac)
        nv = int(n * val_frac)
        return items[:nt], items[nt:nt + nv], items[nt + nv:]

    def expand(gs):
        out = []
        for g in gs:
            out.extend(g)
        return out

    (pt, pv, pte), (nt, nv, nte) = grouped(phish), grouped(benign)
    train = [(d, 1) for g in pt for d, _ in g] + [(d, 0) for g in nt for d, _ in g]
    val = [(d, 1) for g in pv for d, _ in g] + [(d, 0) for g in nv for d, _ in g]
    test = [(d, 1) for g in pte for d, _ in g] + [(d, 0) for g in nte for d, _ in g]
    rng.shuffle(train); rng.shuffle(val); rng.shuffle(test)
    return train, val, test


def extract_features(fe, domains):
    return np.array([fe.extract(d) for d, _ in domains], dtype=np.float32)


def labels_of(domains):
    return np.array([l for _, l in domains], dtype=int)


def confusion(yt, yp):
    tn, fp, fn, tp = confusion_matrix(yt, yp, labels=[0, 1]).ravel()
    return tn, fp, fn, tp


def metrics_at(yt, scores, thr):
    yp = (scores >= thr).astype(int)
    tn, fp, fn, tp = confusion(yt, yp)
    acc = (tp + tn) / max(1, tp + tn + fp + fn)
    rec = tp / max(1, tp + fn)
    prec = tp / max(1, tp + fp)
    f1 = 2 * prec * rec / max(1e-9, prec + rec)
    fpr = fp / max(1, fp + tn)
    fnr = fn / max(1, fn + tp)
    return dict(threshold=float(thr), tn=int(tn), fp=int(fp), fn=int(fn), tp=int(tp),
                accuracy=float(acc), recall=float(rec), precision=float(prec),
                f1=float(f1), fpr=float(fpr), fnr=float(fnr))


def best_threshold_by_f1(yt, scores):
    best = None
    for thr in np.linspace(0.0, 1.0, 2001):
        m = metrics_at(yt, scores, float(thr))
        if best is None or m["f1"] > best["f1"]:
            best = m
    return best


def fit_temperature(y_val, p_val):
    """Fit temperature T to minimize calibration error on validation.
    Calibrated prob = sigmoid(logit(p) / T). T=1 means uncalibrated."""
    # Avoid exact 0/1 for logit
    eps = 1e-6
    p = np.clip(p_val, eps, 1 - eps)
    logits = np.log(p / (1 - p))
    y = y_val.astype(float)

    def nll(T):
        pc = 1.0 / (1.0 + np.exp(-logits / T))
        pc = np.clip(pc, eps, 1 - eps)
        return -np.mean(y * np.log(pc) + (1 - y) * np.log(1 - pc))

    best_T, best_nll = 1.0, nll(1.0)
    for T in np.linspace(0.05, 5.0, 400):
        v = nll(T)
        if v < best_nll:
            best_nll, best_T = v, float(T)
    return best_T


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--benign", required=True)
    ap.add_argument("--phishing", required=True)
    ap.add_argument("--max-samples", type=int, default=None)
    ap.add_argument("--dynamic-data", default=None)
    ap.add_argument("--out", required=True, help="Output ONNX path")
    ap.add_argument("--calibration-out", default=None)
    ap.add_argument("--seed", type=int, default=SEED)
    args = ap.parse_args()

    rng = random.Random(args.seed)
    np.random.seed(args.seed)

    print("Loading benign ...")
    benign = load_domains(args.benign, 0, args.max_samples)
    print(f"  benign: {len(benign)}")
    print("Loading phishing ...")
    phish = load_domains(args.phishing, 1, args.max_samples)
    print(f"  phishing: {len(phish)}")

    benign_set = {d for d, _ in benign}
    before = len(phish)
    phish = [(d, l) for d, l in phish if d not in benign_set]
    print(f"  phishing after overlap removal: {len(phish)} (removed {before - len(phish)})")

    all_data = benign + phish
    rng.shuffle(all_data)
    benign_d = [(d, 0) for d, l in all_data if l == 0]
    phish_d = [(d, 1) for d, l in all_data if l == 1]

    train, val, test = split_grouped(phish_d, benign_d, rng)
    print(f"split train/val/test: {len(train)}/{len(val)}/{len(test)}")
    for nm, sp in (("train", train), ("val", val), ("test", test)):
        npos = sum(l for _, l in sp)
        print(f"  {nm}: pos={npos} neg={len(sp) - npos}")

    fe = LexicalFeatureExtractor(dynamic_data_path=args.dynamic_data)
    Xtr, ytr = extract_features(fe, train), labels_of(train)
    Xva, yva = extract_features(fe, val), labels_of(val)
    Xte, yte = extract_features(fe, test), labels_of(test)

    print("Training LogisticRegression ...")
    lr = LogisticRegression(max_iter=3000, class_weight="balanced", random_state=args.seed)
    lr.fit(Xtr, ytr)

    p_va = lr.predict_proba(Xva)[:, 1]
    p_te = lr.predict_proba(Xte)[:, 1]

    # Uncalibrated metrics at max-F1 val threshold
    b = best_threshold_by_f1(yva, p_va)
    mu = metrics_at(yte, p_te, b["threshold"])
    print(f"\nUncalibrated (max-F1 val thr={b['threshold']:.4f}): "
          f"acc={mu['accuracy']:.4f} rec={mu['recall']:.4f} prec={mu['precision']:.4f} "
          f"f1={mu['f1']:.4f} fpr={mu['fpr']:.4f}")

    # Temperature calibration on validation
    T = fit_temperature(yva, p_va)
    print(f"Fitted temperature T={T:.4f} (1.0 = uncalibrated)")

    # Calibrated probabilities
    def calibrate(p):
        eps = 1e-6
        p = np.clip(p, eps, 1 - eps)
        logits = np.log(p / (1 - p))
        return 1.0 / (1.0 + np.exp(-logits / T))

    p_va_c = calibrate(p_va)
    p_te_c = calibrate(p_te)
    b_c = best_threshold_by_f1(yva, p_va_c)
    mc = metrics_at(yte, p_te_c, b_c["threshold"])
    print(f"\nCalibrated (max-F1 val thr={b_c['threshold']:.4f}): "
          f"acc={mc['accuracy']:.4f} rec={mc['recall']:.4f} prec={mc['precision']:.4f} "
          f"f1={mc['f1']:.4f} fpr={mc['fpr']:.4f}")
    print(f"  CM tn={mc['tn']} fp={mc['fp']} fn={mc['fn']} tp={mc['tp']}")

    # Export calibrated wrapper: raw LR + temperature stored separately.
    # ONNX stores raw LR; temperature applied in Kotlin post-processing.
    initial_type = [("float_input", FloatTensorType([None, Xtr.shape[1]]))]
    onnx_model = convert_sklearn(lr, initial_types=initial_type)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)) or ".", exist_ok=True)
    with open(args.out, "wb") as f:
        f.write(onnx_model.SerializeToString())
    print(f"\nExported ONNX model to {args.out} ({os.path.getsize(args.out)} bytes)")

    if args.calibration_out:
        os.makedirs(os.path.dirname(os.path.abspath(args.calibration_out)) or ".", exist_ok=True)
        with open(args.calibration_out, "w") as f:
            json.dump({"temperature": T, "feature_count": int(Xtr.shape[1]),
                       "model": "LogisticRegression", "seed": args.seed}, f, indent=2)
        print(f"Wrote calibration to {args.calibration_out}")

    # Save metrics
    result = {
        "seed": args.seed, "train": len(train), "val": len(val), "test": len(test),
        "test_benign": int((yte == 0).sum()), "test_phishing": int((yte == 1).sum()),
        "uncalibrated": mu, "calibrated": mc,
        "temperature": T, "val_threshold": b["threshold"],
        "calibrated_val_threshold": b_c["threshold"],
        "model": "LogisticRegression",
    }
    out_json = args.out.replace(".onnx", "_metrics.json").replace("\\", "/")
    os.makedirs(os.path.dirname(os.path.abspath(out_json)) or ".", exist_ok=True)
    with open(out_json, "w") as f:
        json.dump(result, f, indent=2)
    print(f"Wrote metrics to {out_json}")


if __name__ == "__main__":
    main()
