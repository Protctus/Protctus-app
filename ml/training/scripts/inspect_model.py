"""Inspect the trained model structure."""
import os, sys, pickle
import numpy as np

MODEL_DIR = r'A:\Protctus-app\ml\training\models'
PKL_PATH = os.path.join(MODEL_DIR, 'neuroshield_histgradientboosting.pkl')

with open(PKL_PATH, 'rb') as f:
    payload = pickle.load(f)

model = payload['model']
print(f"Model type: {type(model).__name__}")
print(f"classes_: {model.classes_}")
print(f"_baseline_prediction: {model._baseline_prediction}")
print(f"_predictors type: {type(model._predictors)}")
print(f"_predictors len: {len(model._predictors)}")

# Inspect first tree - _predictors is a list of lists (each inner list = one tree)
tree = model._predictors[0]
print(f"\nFirst tree type: {type(tree)}")
print(f"First tree len: {len(tree)}")
if isinstance(tree, list):
    tree_pred = tree[0]
    print(f"TreePredictor type: {type(tree_pred)}")
    print(f"TreePredictor attrs: {[a for a in dir(tree_pred) if not a.startswith('__')]}")
    for a in ['nodes', 'children_left', 'children_right', 'feature', 'threshold', 'value', 'value_shape']:
        if hasattr(tree_pred, a):
            v = getattr(tree_pred, a)
            print(f"  {a}: shape={v.shape if hasattr(v, 'shape') else 'N/A'}, dtype={v.dtype if hasattr(v, 'dtype') else 'N/A'}, len={len(v) if hasattr(v, '__len__') else 'N/A'}")
