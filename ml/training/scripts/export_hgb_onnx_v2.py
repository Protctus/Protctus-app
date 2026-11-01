"""
Export HistGradientBoostingClassifier to ONNX TreeEnsemble.
Properly handles attribute types to avoid protobuf boolean/int errors.
"""
import os, sys, json, pickle
import numpy as np
from onnx import helper, TensorProto, OperatorSetIdProto, save_model
from onnx.helper import make_attribute

sys.path.insert(0, os.path.dirname(__file__))

MODEL_DIR = r'A:\Protctus-app\ml\training\models'
PKL_PATH = os.path.join(MODEL_DIR, 'neuroshield_histgradientboosting.pkl')
ONNX_OUT = os.path.join(MODEL_DIR, 'neuroshield_hgb.onnx')

# Load model
with open(PKL_PATH, 'rb') as f:
    payload = pickle.load(f)
model = payload['model']
feature_count = payload['feature_count']

print(f"Model: {type(model).__name__}, trees: {len(model._predictors)}, features: {feature_count}")
print(f"Baseline: {float(model._baseline_prediction[0][0]):.6f}")

# Collect all tree data
all_node_ids = []
all_feature_ids = []
all_thresholds = []
all_values = []
all_modes = []
all_left_children = []
all_right_children = []
all_tree_ids = []
all_base_values = []
all_node_parent_ids = []

node_id_counter = 0

for tree_idx, tree_list in enumerate(model._predictors):
    tree_pred = tree_list[0]
    nodes = tree_pred.nodes
    n_nodes = len(nodes)
    
    local_to_global = {}
    for i in range(n_nodes):
        local_to_global[i] = node_id_counter
        node_id_counter += 1
    
    for i in range(n_nodes):
        node = nodes[i]
        gid = local_to_global[i]
        
        is_leaf = bool(node['is_leaf'])
        feature_idx = int(node['feature_idx'])
        threshold_val = float(node['num_threshold'])
        value = float(node['value'])
        
        all_tree_ids.append(tree_idx)
        all_node_ids.append(gid)
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
    
    all_base_values.append(float(model._baseline_prediction[0][0]) / len(model._predictors))

print(f"Total nodes: {len(all_node_ids)}, trees: {len(model._predictors)}")

# Build ONNX
X_input = helper.make_tensor_value_info('float_input', TensorProto.FLOAT, ['None', feature_count])
Y_label = helper.make_tensor_value_info('label', TensorProto.INT64, ['None'])
Y_prob = helper.make_tensor_value_info('probabilities', TensorProto.FLOAT, ['None', 2])

# Build node with explicit attribute construction
node = helper.make_node(
    'TreeEnsemble',
    inputs=['float_input'],
    outputs=['label', 'probabilities'],
    domain='ai.onnx.ml',
    nodes=[int(x) for x in all_node_ids],
    feature_ids=[int(x) for x in all_feature_ids],
    feature_kind=[0] * len(all_feature_ids),
    thresholds=[float(x) for x in all_thresholds],
    values=[float(x) for x in all_values],
    modes=[int(x) for x in all_modes],
    left_children=[int(x) for x in all_left_children],
    right_children=[int(x) for x in all_right_children],
    node_parent_ids=[int(x) for x in all_node_ids],
    base_values=[float(x) for x in all_base_values],
    tree_ids=[int(x) for x in all_tree_ids],
    class_ids=[0, 1],
    class_node_ids=[0] * len(model._predictors),
    class_tree_ids=list(range(len(model._predictors))),
    post_transform='LOGISTIC',
)

graph = helper.make_graph(
    nodes=[node],
    name='neuroshield_hgb',
    inputs=[X_input],
    outputs=[Y_label, Y_prob],
)

opset_imports = [
    OperatorSetIdProto(domain='', version=13),
    OperatorSetIdProto(domain='ai.onnx.ml', version=1),
]

model_onnx = helper.make_model(graph, opset_imports=opset_imports)
model_onnx.ir_version = 7

save_model(model_onnx, ONNX_OUT)
size = os.path.getsize(ONNX_OUT)
print(f'Exported HGB ONNX to {ONNX_OUT} ({size} bytes)')

# Verify
import onnxruntime as ort
sess = ort.InferenceSession(ONNX_OUT, providers=['CPUExecutionProvider'])
print(f'\nInputs: {[(i.name, i.shape) for i in sess.get_inputs()]}')
print(f'Outputs: {[(o.name, o.shape) for o in sess.get_outputs()]}')

sys.path.insert(0, os.path.dirname(__file__))
from feature_pipeline import extract_features, add_dynamic_features

test_domains = [
    ('google.com', 0),
    ('microsoft.com', 0),
    ('apple.com', 0),
    ('login-paypal.suspicious-site.tk', 1),
    ('secure-bank-verify.xyz', 1),
    ('appleid.account-update.ml', 1),
    ('paypal-security-update.tk', 1),
    ('192.168.1.1', 1),
    ('xn--pple-43d.com', 1),
    ('facebook.com', 0),
    ('github.io', 0),
]

print('\nVerification:')
correct = 0
for domain, expected in test_domains:
    f = extract_features(domain)
    f = add_dynamic_features(f, domain, None, {})
    X = np.array([f], dtype=np.float32)
    label, probs = sess.run(None, {'float_input': X})
    pmap = probs[0] if isinstance(probs, list) else probs
    phish_prob = float(pmap.get(1, list(pmap.values())[-1] if pmap else 0.0))
    pred = int(label[0])
    status = 'OK' if pred == expected else 'MISMATCH'
    if pred == expected:
        correct += 1
    print(f'  {domain:45s} pred={pred} exp={expected} prob={phish_prob:.4f} [{status}]')
print(f'\nAccuracy on test samples: {correct}/{len(test_domains)}')
