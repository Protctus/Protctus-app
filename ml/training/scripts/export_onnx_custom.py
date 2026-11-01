"""
Custom ONNX exporter for HistGradientBoostingClassifier.

Builds an ONNX TreeEnsemble model directly using the onnx library,
avoiding skl2onnx compatibility issues with newer protobuf versions.
"""
import os, sys, json, pickle
import numpy as np
from onnx import helper, TensorProto, OperatorSetIdProto, save_model, checker

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

# Baseline prediction
baseline = float(model._baseline_prediction[0][0])
print(f"Baseline: {baseline:.6f}")

# Collect TreeEnsemble data
all_node_ids = []
all_feature_ids = []
all_thresholds = []
all_values = []
all_modes = []
all_left_children = []
all_right_children = []
all_tree_ids = []
all_base_values = []

node_id_counter = 0

for tree_idx, tree_list in enumerate(model._predictors):
    tree_pred = tree_list[0]
    nodes = tree_pred.nodes
    n_nodes = len(nodes)
    
    # Build local-to-global node ID mapping
    local_to_global = {}
    for i in range(n_nodes):
        local_to_global[i] = node_id_counter
        node_id_counter += 1
    
    for i in range(n_nodes):
        node = nodes[i]
        node_id = local_to_global[i]
        
        is_leaf = bool(node['is_leaf'])
        feature_idx = int(node['feature_idx'])
        threshold_val = float(node['num_threshold'])
        value = float(node['value'])
        
        all_tree_ids.append(tree_idx)
        all_node_ids.append(node_id)
        all_feature_ids.append(feature_idx)
        all_thresholds.append(threshold_val)
        all_values.append(value)
        
        if is_leaf:
            all_modes.append(2)  # LEAF
            all_left_children.append(0)
            all_right_children.append(0)
        else:
            all_modes.append(0)  # BRANCH_LEQ
            left_local = int(node['left'])
            right_local = int(node['right'])
            all_left_children.append(local_to_global[left_local])
            all_right_children.append(local_to_global[right_local])
    
    all_base_values.append(baseline / len(model._predictors))

print(f"Total nodes: {len(all_node_ids)}")
print(f"Total trees: {len(model._predictors)}")

# Build ONNX model inputs/outputs
X_input = helper.make_tensor_value_info('float_input', TensorProto.FLOAT, ['None', feature_count])
Y_label = helper.make_tensor_value_info('label', TensorProto.INT64, ['None'])
Y_prob = helper.make_tensor_value_info('probabilities', TensorProto.FLOAT, ['None', 2])

# Build TreeEnsemble node - pass raw values, not AttributeProto objects
node = helper.make_node(
    'TreeEnsemble',
    inputs=['float_input'],
    outputs=['label', 'probabilities'],
    domain='ai.onnx.ml',
    nodes=all_node_ids,
    feature_ids=all_feature_ids,
    feature_kind=[0] * len(all_feature_ids),
    thresholds=all_thresholds,
    values=all_values,
    modes=all_modes,
    left_children=all_left_children,
    right_children=all_right_children,
    node_parent_ids=all_node_ids,
    base_values=all_base_values,
    tree_ids=all_tree_ids,
    class_ids=[0, 1],
    class_node_ids=[0] * len(model._predictors),
    class_tree_ids=list(range(len(model._predictors))),
    post_transform='LOGISTIC',
)

graph = helper.make_graph(
    nodes=[node],
    name='neuroshield_tree_ensemble',
    inputs=[X_input],
    outputs=[Y_label, Y_prob],
)

opset_imports = [OperatorSetIdProto(domain='', version=13)]
opset_imports.append(OperatorSetIdProto(domain='ai.onnx.ml', version=3))

model_onnx = helper.make_model(graph, opset_imports=opset_imports)
model_onnx.ir_version = 7

save_model(model_onnx, ONNX_OUT)
print(f'Exported custom ONNX model to {ONNX_OUT} ({os.path.getsize(ONNX_OUT)} bytes)')

# Verify
import onnxruntime as ort
sess = ort.InferenceSession(ONNX_OUT, providers=['CPUExecutionProvider'])
print(f'\nVerifying ONNX model...')
for d in ['google.com', 'login-paypal.suspicious-site.tk', '192.168.1.1', 'xn--pple-43d.com']:
    sys.path.insert(0, os.path.dirname(__file__))
    from feature_pipeline import extract_features, add_dynamic_features
    f = extract_features(d)
    f = add_dynamic_features(f, d, None, {})
    X = np.array([f], dtype=np.float32)
    label, probs = sess.run(None, {'float_input': X})
    pmap = probs[0] if isinstance(probs, list) else probs
    phish_prob = float(pmap.get(1, list(pmap.values())[-1] if pmap else 0.0))
    pred = int(label[0])
    print(f'  {d:45s} pred={pred} phish_prob={phish_prob:.4f}')
