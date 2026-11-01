"""
Test different opset versions for TreeEnsemble compatibility with onnxruntime 1.31.0
"""
import os, sys, json, pickle
import numpy as np
from onnx import helper, TensorProto, OperatorSetIdProto, save_model
import onnxruntime as ort

MODEL_DIR = r'A:\Protctus-app\ml\training\models'
PKL_PATH = os.path.join(MODEL_DIR, 'neuroshield_histgradientboosting.pkl')

with open(PKL_PATH, 'rb') as f:
    payload = pickle.load(f)
model = payload['model']
feature_count = payload['feature_count']

# Build minimal TreeEnsemble with just 2 trees for testing
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
for tree_idx, tree_list in enumerate(model._predictors[:2]):  # Just 2 trees for test
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
        all_tree_ids.append(tree_idx)
        all_node_ids.append(int(gid))
        all_feature_ids.append(int(node['feature_idx']))
        all_thresholds.append(float(node['num_threshold']))
        all_values.append(float(node['value']))
        if bool(node['is_leaf']):
            all_modes.append(2)
            all_left_children.append(0)
            all_right_children.append(0)
        else:
            all_modes.append(0)
            all_left_children.append(int(local_to_global[int(node['left'])]))
            all_right_children.append(int(local_to_global[int(node['right'])]))
    all_base_values.append(float(model._baseline_prediction[0][0]) / len(model._predictors))

X_input = helper.make_tensor_value_info('float_input', TensorProto.FLOAT, ['None', feature_count])
Y_label = helper.make_tensor_value_info('label', TensorProto.INT64, ['None'])
Y_prob = helper.make_tensor_value_info('probabilities', TensorProto.FLOAT, ['None', 2])

ml_opset_versions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

for ml_ver in ml_opset_versions:
    try:
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
            class_node_ids=[0] * 2,
            class_tree_ids=[0, 1],
            post_transform='LOGISTIC',
        )
        graph = helper.make_graph(
            nodes=[node],
            name='test_hgb',
            inputs=[X_input],
            outputs=[Y_label, Y_prob],
        )
        opset_imports = [
            OperatorSetIdProto(domain='', version=13),
            OperatorSetIdProto(domain='ai.onnx.ml', version=ml_ver),
        ]
        model_onnx = helper.make_model(graph, opset_imports=opset_imports)
        model_onnx.ir_version = 7
        
        test_path = os.path.join(MODEL_DIR, f'test_hgb_opset{ml_ver}.onnx')
        save_model(model_onnx, test_path)
        
        sess = ort.InferenceSession(test_path, providers=['CPUExecutionProvider'])
        print(f'ml opset {ml_ver}: OK')
        
        # Quick test
        X = np.array([[0.0]*feature_count], dtype=np.float32)
        label, probs = sess.run(None, {'float_input': X})
        print(f'  label={label}, probs={probs}')
        
    except Exception as e:
        print(f'ml opset {ml_ver}: FAILED - {str(e)[:100]}')
