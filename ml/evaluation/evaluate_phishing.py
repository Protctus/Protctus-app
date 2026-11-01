

import argparse
import json
import math
import os
import random
import sys
import time

import numpy as np
import onnxruntime as ort
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier
from sklearn.metrics import confusion_matrix
from sklearn.model_selection import train_test_split

# Allow running both from inside ml/evaluation/ and from the repo root.
_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)
from feature_extractor import LexicalFeatureExtractor  # noqa: E402

SEED = 42
TRAIN_FRAC = 0.60
VAL_FRAC = 0.20


def load_domains(path, label, max_samples=None):
    domains = []
    seen = set()
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            d = line.strip().lower()
            if not d:
                continue
            for pre in ("http://", "https://"):
                if d.startswith(pre):
                    d = d[len(pre):]
            d = d.split("/")[0].split("?")[0].split("#")[0].rstrip(".")
            if not d:
                continue
            if " " in d or "." not in d or len(d) > 253:
                continue
            parts = d.split(".")
            if len(parts) == 4 and all(p.isdigit() for p in parts):
                continue  # bare IPv4
            if d in seen:
                continue
            seen.add(d)
            domains.append((d, label))
            if max_samples is not None and len(domains) >= max_samples:
                break
    return domains


def split_stratified(phish, benign, rng, train_frac=TRAIN_FRAC, val_frac=VAL_FRAC):
    """Stratified train/validation/test split.

    Domains are grouped by registrable domain so that related subdomains
    (e.g. login.example.com and account.example.com) always land in the same
    split — preventing leakage that would inflate held-out performance.
    """
    def registrable_domain(d: str) -> str:
        parts = d.split(".")
        if len(parts) >= 2:
            return ".".join(parts[-2:])
        return d

    def split_grouped(lst):
        # Group indices by registrable domain, shuffle groups, then split groups.
        groups = {}
        for d, l in lst:
            groups.setdefault(registrable_domain(d), []).append((d, l))
        group_items = list(groups.values())
        rng.shuffle(group_items)
        n = len(group_items)
        nt = int(n * train_frac)
        nv = int(n * val_frac)
        train_groups = group_items[:nt]
        val_groups = group_items[nt:nt + nv]
        test_groups = group_items[nt + nv:]

        def expand(gs):
            out = []
            for g in gs:
                out.extend(g)
            return out

        return expand(train_groups), expand(val_groups), expand(test_groups)

    (pt, pv, pte), (nt, nv, nte) = split_grouped(phish), split_grouped(benign)
    # split_grouped returns flattened (domain, label) tuples; combine phish+benign
    # per split while preserving the domain-grouped separation already applied.
    train = [(d, 1) for d, _ in pt] + [(d, 0) for d, _ in nt]
    val = [(d, 1) for d, _ in pv] + [(d, 0) for d, _ in nv]
    test = [(d, 1) for d, _ in pte] + [(d, 0) for d, _ in nte]
    rng.shuffle(train)
    rng.shuffle(val)
    rng.shuffle(test)
    return train, val, test


def extract_features(fe, domains):
    return np.array([fe.extract(d) for d, _ in domains], dtype=np.float32)


def labels_of(domains):
    return np.array([l for _, l in domains], dtype=int)


def onnx_scores(sess, X):
    label_out, probs = sess.run(None, {"float_input": X})
    pl = probs if isinstance(probs, list) else [probs]
    return np.array(
        [float(m.get(1, list(m.values())[-1] if m else 0.0)) for m in pl],
        dtype=np.float64,
    )


def confusion(yt, yp):
    tn, fp, fn, tp = confusion_matrix(yt, yp, labels=[0, 1]).ravel()
    return tn, fp, fn, tp


def metrics_at(yt, scores, thr):
    yp = (scores >= thr).astype(int)
    tn, fp, fn, tp = confusion(yt, yp)
    acc = (tp + tn) / max(1, (tp + tn + fp + fn))
    rec = tp / max(1, tp + fn)      # recall, phishing = positive
    prec = tp / max(1, tp + fp)
    f1 = 2 * prec * rec / max(1e-9, (prec + rec))
    fpr = fp / max(1, fp + tn)
    fnr = fn / max(1, fn + tp)
    return dict(
        threshold=float(thr), tn=int(tn), fp=int(fp), fn=int(fn), tp=int(tp),
        accuracy=float(acc), recall=float(rec), precision=float(prec),
        f1=float(f1), fpr=float(fpr), fnr=float(fnr),
    )


def best_threshold_by_f1(yt, scores):
    best = None
    for thr in np.linspace(0.0, 1.0, 2001):
        m = metrics_at(yt, scores, float(thr))
        if best is None or m["f1"] > best["f1"]:
            best = m
    return best


def bootstrap_ci(metric_fn, yt, scores, n_boot=2000, ci=95, seed=7):
    rng = np.random.default_rng(seed)
    idx = np.arange(len(yt))
    stats = []
    for _ in range(n_boot):
        sel = rng.choice(idx, size=len(idx), replace=True)
        stats.append(metric_fn(yt[sel], scores[sel]))
    stats = np.array(stats, dtype=float)
    alpha = 100 - ci
    lo = float(np.percentile(stats, alpha / 2))
    hi = float(np.percentile(stats, 100 - alpha / 2))
    return float(stats.mean()), lo, hi


def main():
    ap = argparse.ArgumentParser(description="Evaluate NeuroShield ONNX phishing model")
    ap.add_argument("--onnx", required=True, help="Path to neuroshield_model.onnx")
    ap.add_argument("--benign", required=True, help="Path to benign domains file")
    ap.add_argument("--phishing", required=True, help="Path to phishing domains file")
    ap.add_argument("--seed", type=int, default=SEED)
    ap.add_argument("--max-samples", type=int, default=None,
                    help="Cap samples per class (for smoke tests)")
    ap.add_argument("--out", default=None, help="Write metrics JSON to this path")
    ap.add_argument("--bootstrap", type=int, default=2000)
    ap.add_argument("--dynamic-data", default=None,
                    help="Path to neuroshield_dynamic_data.json. If provided, dynamic "
                         "features 46-52 are populated (matches production preprocessing). "
                         "If omitted, they are zeroed.")
    args = ap.parse_args()

    rng = random.Random(args.seed)
    np.random.seed(args.seed)

    print("Loading benign domains ...")
    benign = load_domains(args.benign, label=0, max_samples=args.max_samples)
    print(f"  benign loaded: {len(benign)}")
    print("Loading phishing domains ...")
    phish = load_domains(args.phishing, label=1, max_samples=args.max_samples)
    print(f"  phishing loaded: {len(phish)}")

    benign_set = {d for d, _ in benign}
    before = len(phish)
    phish = [(d, l) for d, l in phish if d not in benign_set]
    print(f"  phishing after overlap removal: {len(phish)} (removed {before - len(phish)})")

    all_data = benign + phish
    rng.shuffle(all_data)
    # Preserve (domain, label) tuples so split_grouped can unpack them.
    benign_d = [(d, 0) for d, l in all_data if l == 0]
    phish_d = [(d, 1) for d, l in all_data if l == 1]

    train, val, test = split_stratified(phish_d, benign_d, rng)
    print(f"split train/val/test: {len(train)}/{len(val)}/{len(test)}")
    for name, split in (("train", train), ("val", val), ("test", test)):
        npos = sum(l for _, l in split)
        print(f"  {name}: pos={npos} neg={len(split) - npos}")

    fe = LexicalFeatureExtractor(dynamic_data_path=args.dynamic_data)
    sess = ort.InferenceSession(args.onnx, providers=["CPUExecutionProvider"])

    Xtr, ytr = extract_features(fe, train), labels_of(train)
    Xva, yva = extract_features(fe, val), labels_of(val)
    Xte, yte = extract_features(fe, test), labels_of(test)

    print("Inferring ONNX on val ...")
    s_va = onnx_scores(sess, Xva)
    print("Inferring ONNX on test ...")
    s_te = onnx_scores(sess, Xte)
    print("Inferring ONNX on train (sklearn compare) ...")
    s_tr = onnx_scores(sess, Xtr)

    # ---- ONNX baseline: threshold chosen on VAL only ----
    best = best_threshold_by_f1(yva, s_va)
    test_m = metrics_at(yte, s_te, best["threshold"])
    print("\n=== NeuroShield ONNX (threshold max-F1 on validation) ===")
    print(f"  val   thr={best['threshold']:.4f} acc={best['accuracy']:.4f} "
          f"rec={best['recall']:.4f} prec={best['precision']:.4f} f1={best['f1']:.4f} fpr={best['fpr']:.4f}")
    print(f"  test  acc={test_m['accuracy']:.4f} rec={test_m['recall']:.4f} "
          f"prec={test_m['precision']:.4f} f1={test_m['f1']:.4f} fpr={test_m['fpr']:.4f} fnr={test_m['fnr']:.4f}")
    print(f"  CM    tn={test_m['tn']} fp={test_m['fp']} fn={test_m['fn']} tp={test_m['tp']}")
    print(f"  test  benign={int((yte == 0).sum())} phishing={int((yte == 1).sum())}")

    # Latency (re-time a forward pass on val batch)
    t0 = time.perf_counter()
    _ = onnx_scores(sess, Xva)
    dt = (time.perf_counter() - t0) * 1000.0  # ms for the whole val batch
    print(f"  latency: {dt / max(1, len(val)):.3f} ms/domain (batch on CPU)")

    # Bootstrap CIs on test
    thr = best["threshold"]
    ci = {}
    for name, fn in (
        ("accuracy", lambda yt, ys: metrics_at(yt, ys, thr)["accuracy"]),
        ("recall", lambda yt, ys: metrics_at(yt, ys, thr)["recall"]),
        ("precision", lambda yt, ys: metrics_at(yt, ys, thr)["precision"]),
        ("f1", lambda yt, ys: metrics_at(yt, ys, thr)["f1"]),
        ("fpr", lambda yt, ys: metrics_at(yt, ys, thr)["fpr"]),
    ):
        mean, lo, hi = bootstrap_ci(fn, yte, s_te, n_boot=args.bootstrap)
        ci[name] = (mean, lo, hi)
        print(f"  CI95 {name}: mean={mean:.4f} [{lo:.4f}, {hi:.4f}]")

    # Recall >= 0.95 feasibility on val -> test.
    # Select the threshold on VALIDATION ONLY that achieves recall >= 0.95 while
    # minimizing FPR (i.e. the highest threshold still meeting the recall bar).
    # Picking the first threshold from 0.0 upward would stop at thr≈0.0
    # (recall≈1.0, FPR≈1.0) — not a useful operating point.
    thr95 = None
    best_fpr_at_recall95 = float("inf")
    for thr_c in np.linspace(0.0, 1.0, 20001):
        m = metrics_at(yva, s_va, float(thr_c))
        if m["recall"] >= 0.95 and m["fpr"] < best_fpr_at_recall95:
            best_fpr_at_recall95 = m["fpr"]
            thr95 = float(thr_c)
    if thr95 is not None:
        m95 = metrics_at(yte, s_te, thr95)
        print(f"\n  recall>=0.95 @ val thr={thr95:.4f} (min-FPR subject to recall>=0.95) -> test: "
              f"acc={m95['accuracy']:.4f} rec={m95['recall']:.4f} prec={m95['precision']:.4f} "
              f"fpr={m95['fpr']:.4f} fnr={m95['fnr']:.4f} (fn={m95['fn']} fp={m95['fp']})")
    else:
        print("\n  recall 0.95 not reachable on this data (even at thr=0)")

    # ---- Sklearn lightweight models on the same features ----
    print("\n=== Sklearn lightweight models (same 53 features) ===")
    model_specs = [
        ("LogisticRegression", LogisticRegression(max_iter=3000, class_weight="balanced", random_state=args.seed)),
        ("RandomForest", RandomForestClassifier(n_estimators=300, max_depth=14, class_weight="balanced", random_state=args.seed, n_jobs=1)),
        ("HistGradientBoosting", HistGradientBoostingClassifier(max_iter=300, max_depth=8, class_weight="balanced", random_state=args.seed)),
    ]
    for name, mdl in model_specs:
        mdl.fit(Xtr, ytr)
        sv = mdl.predict_proba(Xva)[:, 1]
        st = mdl.predict_proba(Xte)[:, 1]
        b = best_threshold_by_f1(yva, sv)
        mt = metrics_at(yte, st, b["threshold"])
        print(f"  {name:22s} val_thr={b['threshold']:.3f}  "
              f"test acc={mt['accuracy']:.4f} rec={mt['recall']:.4f} prec={mt['precision']:.4f} "
              f"f1={mt['f1']:.4f} fpr={mt['fpr']:.4f} (tp={mt['tp']} fn={mt['fn']})")

    result = {
        "seed": args.seed,
        "model": os.path.basename(args.onnx),
        "model_size_bytes": os.path.getsize(args.onnx),
        "feature_count": LexicalFeatureExtractor.FEATURE_COUNT,
        "positive_class": "phishing",
        "train": len(train), "val": len(val), "test": len(test),
        "test_benign": int((yte == 0).sum()),
        "test_phishing": int((yte == 1).sum()),
        "threshold": best["threshold"],
        "onnx_test": test_m,
        "onnx_ci95": {k: {"mean": v[0], "lo": v[1], "hi": v[2]} for k, v in ci.items()},
        "recall_0p95_feasible": thr95 is not None,
        "recall_0p95_test": (metrics_at(yte, s_te, thr95) if thr95 is not None else None),
        "sklearn": {},
    }
    for name, mdl in model_specs:
        mdl.fit(Xtr, ytr)
        st = mdl.predict_proba(Xte)[:, 1]
        sv = mdl.predict_proba(Xva)[:, 1]
        b = best_threshold_by_f1(yva, sv)
        mt = metrics_at(yte, st, b["threshold"])
        result["sklearn"][name] = {
            "val_threshold": b["threshold"],
            "test": mt,
        }

    if args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)) or ".", exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(result, f, indent=2)
        print(f"\nWrote results to {args.out}")

    return result


if __name__ == "__main__":
    main()
