"""
Try skl2onnx export with proper type handling for HGB model.
"""
import os, sys, json, pickle
import numpy as np
import warnings
warnings.filterwarnings('ignore')

MODEL_DIR = r'A:\Protctus-app\ml\training\models'
PKL_PATH = os.path.join(MODEL_DIR, 'neuroshield_histgradientboosting.pkl')

with open(PKL_PATH, 'rb') as f:
    payload = pickle.load(f)
model = payload['model']
feature_count = payload['feature_count']

print(f"Model: {type(model).__name__}")
print(f"sklearn version: {__import__('sklearn').__version__}")

try:
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType
    print(f"skl2onnx version: {__import__('skl2onnx').__version__}")
    
    initial_type = [('float_input', FloatTensorType([None, feature_count]))]
    
    # Try different approaches
    for target_opset in [12, 13, 14, 15]:
        try:
            onnx_model = convert_sklearn(
                model, 
                initial_types=initial_type, 
                target_opset=target_opset,
                options={type(model): {'zipmap': False}}
            )
            out_path = os.path.join(MODEL_DIR, f'hgb_skl2onnx_opset{target_opset}.onnx')
            with open(out_path, 'wb') as f:
                f.write(onnx_model.SerializeToString())
            print(f"  opset {target_opset}: OK ({os.path.getsize(out_path)} bytes)")
            
            # Verify
            import onnxruntime as ort
            sess = ort.InferenceSession(out_path, providers=['CPUExecutionProvider'])
            print(f"    Verified: inputs={[(i.name, i.shape) for i in sess.get_inputs()]}, outputs={[(o.name, o.shape) for o in sess.get_outputs()]}")
            
            # Quick test
            X = np.array([[0.0]*feature_count], dtype=np.float32)
            label, probs = sess.run(None, {'float_input': X})
            print(f"    Test inference: label={label}, probs={probs}")
            break
            
        except Exception as e:
            print(f"  opset {target_opset}: FAILED - {str(e)[:150]}")

except ImportError as e:
    print(f"skl2onnx not available: {e}")
except Exception as e:
    print(f"Error: {e}")
