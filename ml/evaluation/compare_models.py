"""
Compare candidate models on the same held-out test set, same 53 features
(dynamic features 46-52 populated to match production DynamicFeatureProvider).

Models compared:
  - NeuroShield ONNX (current bundled asset, pretrained, no training)
  - LogisticRegression
  - RandomForest
  - HistGradientBoosting
  - LightGBM (if lightgbm package installed)

Methodology:
  - Positive class = phishing.
  - Domain-grouped stratified train/val/test split (60/20/20), seed 42.
  - Threshold chosen on VALIDATION only (max F1). Test set held out.
  - Recall>=0.95 operating point also reported honestly (min-FPR subject to recall>=0.95 on val).
"""
import argparse, json, math, os, random, sys, time

import numpy as np
import onnxruntime as ort
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier
from sklearn.metrics import confusion_matrix
from sklearn.model_selection import train_test_split

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)
from feature_extractor import LexicalFeatureExtractor

SEED = 42
TRAIN_FRAC = 0.60
VAL_FRAC = 0.20


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


def split_grouped(phish, benign, rng):
    def grouped(lst):
        groups = {}
        for d, l in lst:
            groups.setdefault(registrable_domain(d), []).append((d, l))
        items = list(groups.values())
        rng.shuffle(items)
        n = len(items)
        nt = int(n * TRAIN_FRAC)
        nv = int(n * VAL_FRAC)
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


def onnx_scores(sess, X):
    label_out, probs = sess.run(None, {"float_input": X})
    pl = probs if isinstance(probs, list) else [probs]
    return np.array([float(m.get(1, list(m.values())[-1] if m else 0.0)) for m in pl], dtype=np.float64)


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


def best_threshold_recall95_min_fpr(yt, scores):
    """Threshold on val achieving recall>=0.95 with min FPR. Honest operating point."""
    best = None
    for thr in np.linspace(0.0, 1.0, 20001):
        m = metrics_at(yt, scores, float(thr))
        if m["recall"] >= 0.95:
            if best is None or m["fpr"] < best["fpr"]:
                best = m
    return best


def evaluate_model(name, get_val_scores, get_test_scores, yte, yva):
    print(f"\n=== {name} ===")
    # threshold by max-F1 on validation
    sv = get_val_scores()
    st = get_test_scores()
    b = best_threshold_by_f1(yva, sv)
    mt = metrics_at(yte, st, b["threshold"])
    print(f"  max-F1 threshold (val) = {b['threshold']:.4f}")
    print(f"  test: acc={mt['accuracy']:.4f} rec={mt['recall']:.4f} prec={mt['precision']:.4f} "
          f"f1={mt['f1']:.4f} fpr={mt['fpr']:.4f} fnr={mt['fnr']:.4f}")
    print(f"  CM tn={mt['tn']} fp={mt['fp']} fn={mt['fn']} tp={mt['tp']}")

    # recall>=0.95 operating point (min-FPR) on validation
    r95 = best_threshold_recall95_min_fpr(yva, sv)
    if r95:
        m95 = metrics_at(yte, st, r95["threshold"])
        print(f"  recall>=0.95 @ val thr={r95['threshold']:.4f} -> test: "
              f"acc={m95['accuracy']:.4f} rec={m95['recall']:.4f} fpr={m95['fpr']:.4f}")
    else:
        print(f"  recall>=0.95 not reachable on validation")

    return mt


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--onnx", required=True)
    ap.add_argument("--benign", required=True)
    ap.add_argument("--phishing", required=True)
    ap.add_argument("--seed", type=int, default=SEED)
    ap.add_argument("--max-samples", type=int, default=None,
                    help="Cap samples per class (smoke test). Omit for full data.")
    ap.add_argument("--dynamic-data", default=None,
                    help="Path to neuroshield_dynamic_data.json (populates features 46-52).")
    ap.add_argument("--out", default=None)
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

    sess = ort.InferenceSession(args.onnx, providers=["CPUExecutionProvider"])
    print("Inferring ONNX on val ...")
    s_va = onnx_scores(sess, Xva)
    print("Inferring ONNX on test ...")
    s_te = onnx_scores(sess, Xte)

    results = {}
    # ONNX (pretrained, no training)
    results["NeuroShield ONNX"] = evaluate_model(
        "NeuroShield ONNX", lambda: s_va, lambda: s_te, yte, yva)

    model_specs = [
        ("LogisticRegression", LogisticRegression(max_iter=3000, class_weight="balanced", random_state=args.seed)),
        ("RandomForest", RandomForestClassifier(n_estimators=300, max_depth=14, class_weight="balanced",
                                                random_state=args.seed, n_jobs=1)),
        ("HistGradientBoosting", HistGradientBoostingClassifier(max_iter=300, max_depth=8,
                                                                class_weight="balanced", random_state=args.seed)),
    ]
    try:
        import lightgbm as lgb
        model_specs.append(("LightGBM", lgb.LGBMClassifier(n_estimators=300, max_depth=14,
                                                           class_weight="balanced", random_state=args.seed,
                                                           verbosity=-1, n_jobs=1)))
        print("lightgbm available — included in comparison.")
    except Exception as e:
        print(f"lightgbm not available ({e}) — skipped.")

    for name, mdl in model_specs:
        t0 = time.perf_counter()
        mdl.fit(Xtr, ytr)
        fit_s = time.perf_counter() - t0
        sv = mdl.predict_proba(Xva)[:, 1]
        st = mdl.predict_proba(Xte)[:, 1]
        results[name] = evaluate_model(
            name, lambda sv=sv: sv, lambda st=st: st, yte, yva)
        print(f"  fit time: {fit_s:.1f}s")

    # Summary ranking by test F1
    print("\n" + "=" * 60)
    print("SUMMARY (ranked by test F1, held-out test set)")
    print("=" * 60)
    print(f"{'Model':26s} {'acc':>7s} {'rec':>7s} {'prec':>7s} {'f1':>7s} {'fpr':>7s}")
    ranked = sorted(results.items(), key=lambda kv: -kv[1]["f1"])
    for name, m in ranked:
        print(f"{name:26s} {m['accuracy']:7.4f} {m['recall']:7.4f} {m['precision']:7.4f} "
              f"{m['f1']:7.4f} {m['fpr']:7.4f}")

    if args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)) or ".", exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump({"seed": args.seed, "train": len(train), "val": len(val), "test": len(test),
                       "test_benign": int((yte == 0).sum()), "test_phishing": int((yte == 1).sum()),
                       "results": results}, f, indent=2)
        print(f"\nWrote {args.out}")


if __name__ == "__main__":
    main()
