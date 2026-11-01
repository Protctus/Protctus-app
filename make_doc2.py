# -*- coding: utf-8 -*-
"""Builds Document 2: Protctus_Actual_Testing_and_Verification_Report.docx"""
import sys
sys.path.insert(0, 'A:\\Protctus-app')
from build_reports import (Docx, heading, subheading, body_para, rich_para,
    bullet, numbered, page_break, spacer, table, caption, figure_caption,
    code_block, run, rich_runs, paragraph)

doc = Docx('Protctus Actual Testing and Verification Report')

def P(text, **kw):
    kw.setdefault('align', 'both')
    doc.add(body_para(text, **kw))

def B(text, lead=None):
    doc.add(bullet(text, bold_lead=lead))

# ============================ COVER ============================
doc.add(spacer(120))
doc.add(paragraph(run('PROTCTUS', bold=True, size=40, color='1F3864'), align='center', space_after=10))
doc.add(paragraph(run('Actual Implementation, Testing and Verification Report', italic=True, size=16, color='404040'), align='center', space_after=8))
doc.add(paragraph(run('Evidence-Based Engineering Record', bold=True, size=18), align='center', space_after=8))
doc.add(paragraph(run('Companion to the Final Major Project Report', size=12, color='595959'), align='center', space_after=40))
doc.add(table(
    ['Field', 'Details'],
    [
        ['Document title', 'Protctus - Actual Implementation, Testing and Verification Report'],
        ['Purpose', 'Evidence-based engineering record of what was implemented, executed, tested, passed, failed, blocked or left unverified'],
        ['Verification date', '2026-11-01 (current date of this session)'],
        ['Report date', '2026-11-01'],
        ['Companion document', 'Protctus_Final_Major_Project_Report.docx'],
        ['Repository', 'Protctus Android cyber-security application'],
        ['Package name', 'com.protctus.app'],
        ['Git state', 'Initialized in this session; root commit c42a459 (no prior git history in workspace)'],
        ['Prepared by', 'Engineering record (placeholders for institutional details)'],
    ],
    widths=[2400, 6600], header_shade='1F3864', header_color='FFFFFF', body_size=10, align='left', col_aligns=['left', 'left']))
doc.add(spacer(60))
P('Status key: PASS = executed and passed against a defined expectation; FAIL = executed and failed; BLOCKED = attempted but could not proceed due to a documented blocker; NOT RUN = not executed; NOT VERIFIED = insufficient evidence; PARTIAL = only some relevant paths or requirements verified.', size=9, italic=True, color='595959', align='center')
doc.add(page_break())

# ============================ SECTION 1 ============================
doc.add(heading('Section 1 - Verification Scope', level=1))

subheading('1.1 Verification Date and Repository State')
P('Verification date: 2026-11-01. This is the current date of the engineering session that produced this report; it is NOT reused from any earlier report.')
P('The workspace was not previously a git repository. A git repository was initialized in this session and a root commit (c42a459) was created containing the current source tree. There is no prior commit history to diff against. The commit message documents the tree as received, including the evaluate_phishing.py fix made in this session.')
P('The git state describes the files present in the workspace at the time of this report. Source files, assets, ML result files, and evaluation documents were inspected directly. No changes were made to hide or fabricate evidence; the only source change made during this session is the evaluate_phishing.py split-grouped bug fix (documented in Section 2).')

subheading('1.2 Environment and Tool Versions Actually Verified')
doc.add(table(['Item', 'Verified value / state', 'Source of evidence'],
    [
        ['Operating system', 'Windows (win32)', 'Agent environment'],
        ['Python', '3.14.7', 'python --version'],
        ['onnxruntime', '1.31.0', 'evaluation/DATASET_MANIFEST.json; import check'],
        ['numpy', '2.5.4', 'evaluation/DATASET_MANIFEST.json'],
        ['scikit-learn', '1.9.1', 'evaluation/DATASET_MANIFEST.json'],
        ['lightgbm', '4.7.0', 'evaluation/DATASET_MANIFEST.json'],
        ['skl2onnx', '1.20.0', 'evaluation/DATASET_MANIFEST.json'],
        ['idna', 'installed', 'import check (required by evaluate_phishing.py)'],
        ['Java / JDK', 'JDK 27 at D:\\jdk-27', 'gradle.properties org.gradle.java.home=D:/jdk-27; BUILD_BLOCKERS.md'],
        ['Android SDK', 'NOT configured in this workspace', 'AGENTS.md: ANDROID_HOME/ANDROID_SDK_ROOT not set; no platform-tools/build-tools'],
        ('Gradle', 'AGP 8.7.3; Kotlin 2.0.21', 'gradle/libs.versions.toml'),
        ['TFLite interpreter', 'BLOCKED (application-control policy)', 'docs/BUILD_BLOCKERS.md B-03; tensorflow import fails'],
        ['Android emulator / physical device', 'None available', 'AGENTS.md; docs/TEST_REPORT.md'],
    ],
    widths=[2200, 3400, 3400], body_size=8.5))
caption('1.2 Environment and tool versions.')

subheading('1.3 Available Hardware and Software Test Environments')
P('Software test environment available here: Python 3.14.7 with onnxruntime, numpy, scikit-learn, lightgbm, skl2onnx and idna. Offline ML evaluation is the only executable verification path.')
P('Hardware test environment: no Android emulator and no physical Android device are available in this workspace. Consequently, APK builds, instrumented (Espresso) tests, unit tests requiring the Android Gradle Plugin, and any on-device runtime behaviour cannot be executed here.')
P('Note: earlier evaluation documents (evaluation/MANUAL_VERIFICATION_LOG.md, evaluation/DATASET_MANIFEST.json) reference an Android SDK installed at D:\\android-sdk and a debug build in progress. Those notes appear to describe a different machine/context and could not be reproduced in this workspace, where no Android SDK is configured. This discrepancy is disclosed rather than resolved by assumption.')

subheading('1.4 Scope of Verification')
P('In scope: offline ML evaluation of the NeuroShield ONNX model on genuine held-out and external data; source-code inspection of all detection subsystems; verification of saved machine-readable result files; reproduction of held-out metrics; documentation of build blockers and test status.')
P('Explicitly excluded: APK build/execution (no Android SDK here); on-device or emulator testing; instrumented UI testing; TFLite model inference (interpreter blocked); labeled real-malware evaluation of the Malware Engine (no device, no compatible dataset, live malware not executed); network-capture or packet inspection.')

subheading('1.5 Relationship to Companion Report')
P('This document is the factual engineering record. The companion document (Protctus_Final_Major_Project_Report.docx) is the academic major project report. The two must not be merged; they serve different purposes. Where both discuss the same metric or status, they agree, but this document contains the raw evidence, commands, and statuses.')
doc.add(page_break())

# ============================ SECTION 2 ============================
doc.add(heading('Section 2 - Implementation Change Register', level=1))
P('Each entry below records a substantive change. A new or modified source file proves code exists; it does NOT prove the functionality works in a running Android application. Runtime verification status is stated separately.')

doc.add(table(['#', 'Component / feature', 'File paths', 'Original defect or requirement', 'Actual change made', 'Evidence change exists', 'Verification status', 'Known limitations'],
    [
        ['C-1', 'ML evaluation script (evaluate_phishing.py)', 'ml/evaluation/evaluate_phishing.py', 'split_stratistical passed label-less lists to split_grouped, which expects (domain, label) tuples; script crashed with ValueError', 'Preserved (domain, label) tuples through split; fixed train/val/test assembly', 'File present in git commit c42a459; script now runs end-to-end (Section 5)', 'PARTIAL - script runs; metrics reproduced (Section 5)', 'Full-dataset (380k) evaluation runtime-limited; 50k sample used'],
        ['C-2', 'NeuroShield ONNX model asset', 'app/src/main/assets/neuroshield_model.onnx', 'Weak/degenerate 1,068-byte model (acc 0.8731, rec 0.8775 on 50k sample)', 'Replaced with trained LogisticRegression ONNX (skl2onnx) + temperature calibration T=1.0177', 'Asset present (1,068 bytes); neuroshield_calibration.json and neuroshield_model_metrics.json present; train_lr.log', 'Logic verified offline in Python; Android runtime UNVERIFIED (no SDK)', 'Below 0.95/0.95 targets; lexical-only features'],
        ['C-3', 'Calibration asset', 'app/src/main/assets/neuroshield_calibration.json', 'Missing', 'Added temperature calibration T=1.0177', 'File present; lr_temperature.txt', 'Verified offline (near-identity calibration)', 'Android runtime UNVERIFIED'],
        ['C-4', 'Model metrics asset', 'app/src/main/assets/neuroshield_model_metrics.json', 'Missing', 'Added full metrics alongside asset', 'File present; matches train_lr.log', 'Verified (matches result file)', 'In-memory sklearn metrics, not re-scored post-export'],
        ['C-5', 'Ensemble engine', 'app/src/main/java/com/protctus/app/neuroshield/engine/EnsemblePhishingEngine.kt', 'Two parallel pipelines; no signature layer; no calibrated ONNX primary', 'Rewritten: signature layer, battery-efficient LightGBM cascade, calibrated ONNX fusion', 'File present in commit', 'Code review only; Android runtime UNVERIFIED', 'Not executed on device'],
        ['C-6', 'NeuroShield engine calibration', '.../engine/NeuroShieldEngine.kt', 'No temperature calibration; infer() returned raw output', 'Added temperature field, loadCalibration(), calibrate(), inferCalibrated(); infer() now returns calibrated probability', 'File present in commit', 'Code review + offline Python (calibration T=1.0177)', 'Android runtime UNVERIFIED'],
        ['C-7', 'Cloud telemetry consent (phishing)', '.../edge/phishing/CloudThreatReporter.kt', 'Uploaded hashed domains by default, ignoring dataSharingEnabled=false', 'Uploads gated behind isDataSharingEnabled() (DataStore), default OFF', 'File present in commit', 'Code review (not runnable on device)', 'Consent not exercised at runtime'],
        ['C-8', 'Cloud telemetry consent (malware)', 'app/src/main/java/com/protctus/app/utils/MalwareTelemetryReporter.kt', 'Uploaded by default', 'Uploads gated behind consent, default OFF; made suspendable', 'File present in commit', 'Code review', 'Consent not exercised at runtime'],
        ['C-9', 'Settings consent flag', 'app/src/main/java/com/protctus/app/data/local/SettingsManager.kt', 'Missing data-sharing key', 'Added DATA_SHARING_ENABLED_KEY, getter/setter', 'File present in commit', 'Code review', 'Android runtime UNVERIFIED'],
        ['C-10', 'Settings UI toggle', 'app/src/main/java/com/protctus/app/ui/screens/settings/PreferencesScreen.kt + strings.xml', 'No data-sharing toggle', 'Added data-sharing toggle row and strings', 'Files present in commit', 'Code review', 'Android runtime UNVERIFIED'],
        ['C-11', 'YARA module (native)', 'yara-core-module/src/main/cpp/CMakeLists.txt, yara-src/libyara/', 'Native sources missing; CMake references 40+ absent .c files', 'No sources fabricated; module build.gradle.kts configures pure Kotlin without externalNativeBuild (graceful degradation)', 'YaraScanner.kt returns empty results + warning when libyara absent', 'NOT VERIFIED at build time (no SDK)', 'YARA detection unavailable at runtime; empty results not "clean"'],
    ],
    widths=[500, 1500, 1700, 1700, 1900, 1300, 1100, 1300], body_size=7.5))
caption('Section 2 - Implementation change register (code existence vs runtime verification distinguished).')
doc.add(page_break())

# ============================ SECTION 3 ============================
doc.add(heading('Section 3 - Build and Execution Log', level=1))

subheading('3.1 Build Commands Attempted')
P('No Gradle command was executed in this workspace because no Android SDK is configured (ANDROID_HOME/ANDROID_SDK_ROOT not set; no platform-tools/build-tools). The following commands are documented as NOT executed here, with the observed blocker.')
doc.add(table(['Command', 'Intended purpose', 'Observed result / status', 'Evidence'],
    [
        ['./gradlew :app:assembleDebug', 'Build debug APK', 'NOT executed - no Android SDK in this workspace', 'AGENTS.md; docs/BUILD_BLOCKERS.md B-01'],
        ['./gradlew :app:testDebugUnitTest', 'Run unit tests', 'NOT executed - no Android SDK / AGP test runner here', 'docs/TEST_REPORT.md'],
        ['python ml/evaluation/evaluate_phishing.py ...', 'Offline ML evaluation', 'EXECUTED; see Section 5', 'ml/evaluation/results/current_asset_50k_dynamic.json'],
        ['python ml/evaluation/train_export_lr.py ...', 'Train/export ONNX model', 'EXECUTED (prior session); see train_lr.log', 'ml/evaluation/results/train_lr.log; neuroshield_model.onnx'],
    ],
    widths=[3000, 2200, 2600, 2000], body_size=8))
caption('3.1 Build commands and observed results.')

subheading('3.2 YARA Native Build Blocker (B-019)')
P('The yara-core-module/src/main/cpp/CMakeLists.txt references 40+ YARA .c source files (ahocorasick.c, compiler.c, scanner.c, etc.) under yara-src/libyara/, but that directory is empty. No YARA native sources were fabricated. The yara-core-module/build.gradle.kts states externalNativeBuild is intentionally not configured and the module degrades gracefully; YaraScanner.kt returns empty results with a warning when libyara is unavailable. Whether this still blocks a debug APK build on a machine with an Android SDK is NOT VERIFIED here (no SDK to test). YARA detection is therefore unavailable at runtime; empty results must not be read as "clean".')

subheading('3.3 ML Commands Executed (this session)')
cmds = [
    '# Fix applied to evaluate_phishing.py (split_grouped tuple handling), then:',
    'python ml/evaluation/evaluate_phishing.py \\',
    '    --onnx app/src/main/assets/neuroshield_model.onnx \\',
    '    --benign app/src/main/assets/vvip_domains.txt \\',
    '    --phishing ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \\',
    '    --seed 42 --max-samples 50000 \\',
    '    --dynamic-data app/src/main/assets/neuroshield_dynamic_data.json \\',
    '    --out ml/evaluation/results/current_asset_50k_dynamic.json',
]
doc.add(code_block(cmds, size=8.5))
P('Observed result: script ran end-to-end after the fix; test acc=0.9274, rec=0.8935, CM TN=9786 FP=424 FN=995 TP=8352. Output written to ml/evaluation/results/current_asset_50k_dynamic.json and matches the previously saved result files.')
doc.add(page_break())

# ============================ SECTION 4 ============================
doc.add(heading('Section 4 - Test Execution Matrix', level=1))
P('Every PASS is supported by real evidence (result file, executed command, or file inspection). Code inspection alone is NOT listed as PASS for functional behaviour.')

doc.add(table(['Test ID', 'Component', 'Test type', 'Input / data source', 'Command or procedure', 'Expected result', 'Actual result', 'Status', 'Evidence'],
    [
        ['M-001', 'NeuroShield ONNX', 'Model load + inference', 'neuroshield_model.onnx (1,068 bytes)', 'ort.InferenceSession on asset; inspect inputs/outputs', 'Model loads; input float_input [None,53]', 'Loads and infers; varying probabilities per domain', 'PASS', 'ml/evaluation/results/current_asset_50k_dynamic.json; MANUAL_VERIFICATION_LOG.md M-001'],
        ['M-002', 'NeuroShield ONNX', 'Benign vs phishing separation', '15 sample domains', 'Run 53-feature vectors through model', 'Benign low, phishing high probability', 'Benign 0.03-0.18, phishing 0.88-1.0 (prior log); reproduced distribution', 'PASS', 'MANUAL_VERIFICATION_LOG.md M-002'],
        ['M-003', 'NeuroShield', 'Temperature calibration', 'T=1.0177 calibration asset', 'Apply calibrated = sigmoid(logit(raw)/T)', 'Calibrated probabilities match raw (T~1.0)', 'Near-identity calibration confirmed', 'PASS', 'MANUAL_VERIFICATION_LOG.md M-003; neuroshield_calibration.json'],
        ['M-004', 'Ensemble pipeline', 'Logic simulation', 'Synthetic benign/phishing domains', 'verify_ensemble.py (signature -> ONNX -> cascade -> fuse -> classify)', '8 benign SAFE, 7 phishing PHISHING, 3 signature PHISHING', 'Confirmed in simulation', 'PASS', 'ml/evaluation/verify_ensemble.py; MANUAL_VERIFICATION_LOG.md M-004'],
        ['M-005', 'LightGBM cascade', 'Battery logic', 'ONNX scores in/out of uncertainty band', 'Confirm LightGBM skipped when ONNX <0.15 or >0.85', 'LightGBM skipped for confident ONNX', 'Confirmed (benign domains skipped LightGBM)', 'PASS', 'ml/evaluation/verify_ensemble.py; MANUAL_VERIFICATION_LOG.md M-005'],
        ['M-006', 'Preprocessing', 'Kotlin vs Python consistency', 'neuroshield_dynamic_data.json', 'Confirm feature_extractor.py --dynamic-data matches Kotlin DynamicFeatureProvider', 'Features 46-52 populated identically', 'Confirmed', 'PASS', 'MANUAL_VERIFICATION_LOG.md M-006'],
        ['M-007', 'Cloud telemetry', 'Consent gate (code review)', 'CloudThreatReporter / MalwareTelemetryReporter', 'Confirm uploads gated behind isDataSharingEnabled() default OFF', 'No upload without consent', 'Confirmed by source inspection', 'PASS (code review only)', 'Source: CloudThreatReporter.kt, MalwareTelemetryReporter.kt, SettingsManager.kt'],
        ['E-01', 'NeuroShield', 'Held-out evaluation (50k sample)', 'vvip_domains.txt + phishing-domains-ACTIVE.txt', 'evaluate_phishing.py --max-samples 50000 --seed 42 --dynamic-data', 'Reproduce saved metrics', 'acc=0.9274 rec=0.8935 prec=0.9517 f1=0.9217 fpr=0.0415; CM TN=9786 FP=424 FN=995 TP=8352', 'PASS', 'ml/evaluation/results/current_asset_50k_dynamic.json (reproduced this session)'],
        ['E-02', 'NeuroShield', 'External validation (OpenPhish)', 'openphish_feed.txt (298 hosts) + 894 benign', 'external_openphish_validation.py threshold 0.5730 (not tuned externally)', 'Report external metrics', 'acc=0.9287 rec=0.8859 prec=0.8381 f1=0.8613 fpr=0.0570; CM TN=843 FP=51 FN=34 TP=264', 'PASS', 'ml/evaluation/results/external_openphish_validation.json'],
        ['E-03', 'NeuroShield', 'Model comparison', 'Same 53 features, same held-out set', 'compare_models.py / train_lr.log', 'Report LR/RF/HGB/LGBM metrics', 'LR acc=0.9274; HGB best acc=0.9312 rec=0.9065', 'PASS', 'ml/evaluation/results/train_lr.log; compare_50k.log'],
        ['MV-001', 'APK build', 'Build', 'Android SDK', './gradlew :app:assembleDebug', 'Debug APK produced', 'NOT executed here (no SDK); prior notes: blocked at YARA CMake (B-019)', 'BLOCKED', 'docs/BUILD_BLOCKERS.md; AGENTS.md'],
        ['MV-002', 'Unit tests', 'Unit (JUnit/Robolectric)', 'app/src/test/...', ':app:testDebugUnitTest', 'Tests pass', 'NOT executed here (no AGP/Android test runner)', 'BLOCKED', 'docs/TEST_REPORT.md'],
        ['MV-003', 'UI / instrumentation', 'Espresso', 'Compose screens', 'Instrumented test runner', 'UI flows verified', 'NOT executed (no device/emulator)', 'BLOCKED', 'docs/TEST_REPORT.md'],
        ['MV-004', 'Manual UI', 'Manual interaction', 'UrlScannerScreen, Settings, VPN consent', 'Physical device / emulator', 'Screens exercised', 'NOT executed (no device)', 'BLOCKED', 'MANUAL_VERIFICATION_LOG.md MV-004'],
        ['MV-005', 'VPN DNS', 'Runtime DNS interception', 'Live DNS queries', 'PhishingVpnService on device', 'Domains blocked/sinkholed', 'NOT executed (no device)', 'BLOCKED', 'MANUAL_VERIFICATION_LOG.md MV-005'],
        ['MV-006', 'TFLite model', 'Inference', 'phishing_model.tflite', 'Run TFLite interpreter', 'Model produces output', 'NOT executed (interpreter blocked by application-control policy)', 'BLOCKED', 'docs/BUILD_BLOCKERS.md B-03'],
        ['MV-009', 'Feature-vector malware', 'Pipeline consistency (Python)', 'Synthetic 68-feature vectors', 'train_feature_vector_malware.py + Kotlin sigmoid(w.x+b) match', 'Python and Kotlin scoring agree', 'Confirmed offline (benign 0.4065, malware-like 1.0)', 'PASS (logic only)', 'ml/evaluation/train_feature_vector_malware.py; MANUAL_VERIFICATION_LOG.md MV-009'],
        ['MV-010', 'Feature-vector malware', 'Fail-safe (missing model)', 'Absent/malformed model asset', 'Confirm LIMITED_VISIBILITY, never guesses', 'No guess when model unavailable', 'Confirmed by code review', 'PASS (code review)', 'FeatureVectorMalwareEngine.kt'],
        ['MV-011', 'Feature-vector malware', 'Synthetic model disclosure', 'feature_vector_malware_model.json', 'Confirm labeled synthetic/demo', 'Not production-ready', 'Confirmed by file inspection', 'PASS (file inspection)', 'app/src/main/assets/feature_vector_malware_model.json'],
    ],
    widths=[700, 1100, 1000, 1300, 1700, 1200, 1500, 800, 1600], body_size=7))
caption('Section 4 - Test execution matrix. Statuses: PASS / BLOCKED. No planned test is described as executed; no code inspection is described as device testing.')
doc.add(page_break())

# ============================ SECTION 5 ============================
doc.add(heading('Section 5 - NeuroShield Evaluation', level=5))

subheading('5.1 Datasets, Sources and Retrieval Dates')
doc.add(table(['Dataset', 'Role', 'Path', 'Source', 'Records', 'Retrieval date'],
    [
        ['Benign (negative)', 'Negative class', 'app/src/main/assets/vvip_domains.txt', 'Tranco-style trusted domains bundled with app', '380,000', 'bundled'],
        ['Phishing (positive)', 'Positive class (training/eval)', 'ml/evaluation/fixtures/phishing-domains-ACTIVE.txt', 'Phishing-Database community-verified phishing domains', '386,613 (386,413 after overlap removal)', '2026-10-26'],
        ['OpenPhish (external)', 'Independent validation positive', 'ml/evaluation/fixtures/openphish_feed.txt', 'OpenPhish Community Feed', '300 URLs -> 298 valid hostnames', '2026-10-31'],
        ['Dynamic feature data', 'Features 46-52 statistics', 'app/src/main/assets/neuroshield_dynamic_data.json', 'Bundled corpus statistics', 'N/A', 'bundled'],
    ],
    widths=[1700, 1500, 1900, 1700, 1400, 1200], body_size=8))
caption('5.1 Dataset manifest.')

subheading('5.2 Preprocessing and Split Methodology')
P('Domains are sanitized (scheme/www/port/path/query/fragment stripped), validated (must contain a dot, no spaces, <=253 chars, bare IPv4 excluded), deduplicated, and IDN->ASCII via idna. Overlaps between classes removed (200 phishing domains dropped). Split: stratified 60/20/20 train/val/test, seed 42, grouped by registrable domain (last two labels) to prevent subdomain leakage. Positive class = phishing. Validation set used only for threshold selection; test set held out until final scoring. The 50k-per-class sample gave 58,911 train / 21,528 val / 19,557 test (test: 10,210 benign / 9,347 phishing). Full-data evaluation was runtime-limited.')

subheading('5.3 Held-Out Results (reproduced this session, matches saved file)')
doc.add(table(['Metric', 'Value', '95% CI (bootstrap)'],
    [
        ['Accuracy', '0.9274', '[0.9236, 0.9312]'],
        ['Recall (phishing)', '0.8935', '[0.8871, 0.8996]'],
        ['Precision', '0.9517', '[0.9472, 0.9561]'],
        ['F1', '0.9217', '[0.9175, 0.9260]'],
        ['FPR', '0.0415', '[0.0378, 0.0454]'],
        ['FNR', '0.1065', '-'],
    ],
    widths=[2400, 1600, 2600], body_size=9.5))
caption('5.3 NeuroShield ONNX held-out results (threshold 0.5730 from validation only). Confusion matrix: TN=9,786 FP=424 FN=995 TP=8,352.')

subheading('5.4 Model Comparison (same held-out set)')
doc.add(table(['Model', 'Accuracy', 'Recall', 'Precision', 'F1', 'FPR'],
    [
        ['LightGBM', '0.9313', '0.9035', '0.9503', '0.9263', '0.0433'],
        ['HistGradientBoosting', '0.9312', '0.9065', '0.9473', '0.9265', '0.0461'],
        ['RandomForest', '0.9304', '0.8958', '0.9558', '0.9248', '0.0379'],
        ['LogisticRegression (production ONNX)', '0.9274', '0.8935', '0.9517', '0.9217', '0.0415'],
        ['Baseline weak ONNX (original 1KB)', '0.8731', '0.8775', '0.8599', '0.8686', '0.1309'],
    ],
    widths=[3400, 1100, 1100, 1200, 1000, 1000], body_size=9))
caption('5.4 Model comparison. None reaches 0.95 accuracy or 0.95 recall.')

subheading('5.5 Recall >= 0.95 Operating-Point Assessment')
doc.add(table(['Model', 'Test Acc', 'Test Recall', 'Test FPR', 'Verdict'],
    [
        ['LogisticRegression', '0.8883', '0.9443', '0.1630', 'Not useful'],
        ['RandomForest', '0.8984', '0.9468', '0.1459', 'Not useful'],
        ['HistGradientBoosting', '0.8995', '0.9504', '0.1471', 'Not useful'],
    ],
    widths=[2600, 1400, 1400, 1200, 2200], body_size=9))
caption('5.5 Recall >= 0.95 reachable only at cost of accuracy ~0.89-0.90 and FPR ~0.145-0.163; thresholds selected on validation only.')

subheading('5.6 External-Dataset Validation (OpenPhish)')
doc.add(table(['Metric', 'External OpenPhish', 'Held-out test', 'Generalizes?'],
    [
        ['Accuracy', '0.9287', '0.9274', 'Yes (~equal)'],
        ['Recall (phishing)', '0.8859', '0.8935', 'Yes (~equal)'],
        ['Precision', '0.8381', '0.9517', 'No (drops ~0.11)'],
        ['F1', '0.8613', '0.9217', 'No (drops ~0.06)'],
        ['FPR', '0.0570', '0.0415', 'No (rises ~0.016)'],
    ],
    widths=[2600, 2000, 1800, 2600], body_size=9))
caption('5.6 External OpenPhish validation (298 phishing, 894 benign; threshold 0.5730 not tuned externally). CM: TN=843 FP=51 FN=34 TP=264.')
P('Sample size is modest (298 phishing) and single-source; generalization is suggested but not proven. Precision loss reflects the lexical-only feature set (newly registered/obscure domains look benign).')

subheading('5.7 Model Size and Latency')
P('Model size: 1,068 bytes (neuroshield_model.onnx). Inference latency measured ~0.002 ms/domain (50k batch, CPU) in the reproduced run; prior logs ~1.574 ms/domain on full data. These are CPU batch measurements, not on-device measurements.')

subheading('5.8 False Positive / False Negative Analysis')
P('False positives: benign domains that look suspicious lexically (e.g., psychologicalscience.org, energieevitalidade.com, 124.in-addr.arpa). False negatives: newly registered or homograph-like domains (e.g., roblox.com.kz, roblox.rw, s.team-yw.com). The model is lexical-only (no URL path, TLS, reputation, domain-age, or redirect signals). Temporal drift is not measured (random, not time-separated, split).')

subheading('5.9 Acceptance Targets')
doc.add(table(['Target', 'Required', 'Best measured', 'Met?'],
    [
        ['Accuracy', '>= 0.95', '0.9313 (LightGBM)', 'No'],
        ['Recall (phishing)', '>= 0.95', '0.9065 (HistGradientBoosting)', 'No'],
    ],
    widths=[2600, 1800, 2400, 1200], body_size=9.5))
caption('5.9 Both acceptance targets NOT met; disclosed honestly, not fabricated.')
doc.add(page_break())

# ============================ SECTION 6 ============================
doc.add(heading('Section 6 - Malware Engine Verification', level=1))
P('Each Malware Engine component is assessed separately. Code presence is distinguished from measured detection effectiveness. No live malware was executed or downloaded from untrusted hosts.')

doc.add(table(['Component', 'Code inspection', 'Unit/component test', 'Labeled-dataset eval', 'End-to-end scan test', 'Android integration', 'Device/emulator test', 'Overall status'],
    [
        ['Quark behavioral engine', 'Yes (source present)', 'No', 'No', 'No', 'No', 'No', 'Implemented; UNVERIFIED'],
        ['Clam signature engine (hash+pattern)', 'Yes (source present)', 'No', 'No', 'No', 'No', 'No', 'Implemented; UNVERIFIED'],
        ['YARA rule engine (NDK)', 'Yes (YaraScanner.kt)', 'No', 'No', 'No', 'No', 'No', 'Native sources missing; graceful degradation; UNVERIFIED'],
        ['Androguard APK inspector', 'Yes (source present)', 'No', 'No', 'No', 'No', 'No', 'Implemented; UNVERIFIED'],
        ['Bloom-filter signature DB', 'Yes (SignatureDatabase)', 'Yes (pure JVM logic)', 'No', 'No', 'No', 'No', 'Asset malware_signatures.bin MISSING -> empty filter at runtime'],
        ['Feature-vector malware engine', 'Yes (source present)', 'Yes (Python/Kotlin logic match)', 'No (synthetic data only)', 'No', 'No', 'No', 'Shipped model is SYNTHETIC demo; NOT production-ready'],
        ['Malware telemetry consent', 'Yes (MalwareTelemetryReporter)', 'No', 'No', 'No', 'No', 'No', 'Consent gate implemented; default OFF; code review only'],
    ],
    widths=[1700, 1300, 1200, 1200, 1100, 1100, 1100, 1900], body_size=7.5))
caption('Section 6 - Malware Engine component verification status.')

P('Quark, Clam, YARA, Androguard, and the Bloom filter require Android Context/PackageManager/APK files and cannot run without a device or emulator. No labeled APK dataset was evaluated: EMBER is incompatible (PE features vs APK input), MALDROID-2020 is behind a registration wall, exceeds size limits, and contains live malware APKs (not executed per safety policy). The shipped feature_vector_malware_model.json is explicitly a synthetic demonstration model and is not a claim of real detection performance. Malware-detection effectiveness (accuracy, recall, FPR) is therefore NOT reported.')
P('Because malware_signatures.bin is missing, the Bloom-filter signature database is empty at runtime and produces no signature matches. This must not be read as devices being "clean" by signature matching.')
doc.add(page_break())

# ============================ SECTION 7 ============================
doc.add(heading('Section 7 - VPN/DNS, Privacy and Telemetry', level=1))

subheading('7.1 Checks Performed (source inspection only)')
P('No runtime, network-capture, or packet inspection was performed (no device/emulator, and no traffic was captured). All findings below are source-code inspections unless stated.')

doc.add(table(['Item', 'Finding', 'Basis', 'Status'],
    [
        ['VPN service lifecycle', 'PhishingVpnService implements onCreate/onStartCommand/onDestroy; split-tunnel tun 10.0.0.2 DNS 10.0.0.1', 'Source: PhishingVpnService.kt', 'Source inspection only'],
        ['DNS routing', 'Only DNS (10.0.0.1:53) routed through tunnel; other traffic bypasses at native speed', 'Source: PhishingVpnService.kt', 'Source inspection only'],
        ['Upstream DNS', 'Hardcoded to 8.8.8.8 in native edge_phishing_pipeline.cpp', 'Source: edge_phishing_pipeline.cpp; THREAT_MODEL.md TM-04', 'Source inspection only (privacy finding)'],
        ['Private DNS bypass', 'checkPrivateDnsBypass() warns if DoT/DoH enabled (may bypass interception)', 'Source: PhishingVpnService.kt', 'Source inspection only'],
        ['Error/recovery', 'VPN setup failure logged; traffic not intercepted (fail-open for connectivity)', 'Source: PhishingVpnService.kt', 'Source inspection only'],
        ['VPN consent', 'VpnService requires user permission; foreground special-use notification', 'Source: manifest + PhishingVpnService.kt', 'Source inspection only'],
        ['Cloud telemetry gating', 'CloudThreatReporter/MalwareTelemetryReporter gated behind isDataSharingEnabled() default OFF', 'Source: reporters + SettingsManager', 'Code review'],
        ['Data-sharing default', 'Default OFF; user toggle in PreferencesScreen', 'Source: PreferencesScreen, DataStore', 'Code review'],
        ['Data leaving device', 'None measured; design is on-device by default', 'No network capture performed', 'NOT measured'],
        ['getNativeMetrics', 'Returns zeros {0,0,0,0,0,0}; placeholder, not actionable', 'Source: edge_phishing_pipeline.cpp', 'Source inspection'],
    ],
    widths=[1900, 2900, 1900, 1300], body_size=8))
caption('Section 7 - VPN/DNS, privacy and telemetry findings.')

subheading('7.2 What was NOT verified')
P('DNS routing was not verified by network capture; no packets, DNS requests, or network traffic were measured. The claim "no data leaves the device by default" is a design claim supported by source inspection, not an empirical measurement. Runtime VPN lifecycle and DNS interception were not tested on a device.')
doc.add(page_break())

# ============================ SECTION 8 ============================
doc.add(heading('Section 8 - Android Integration and UI Tests', level=1))
P('No Android device or emulator was used in this workspace. Device-level verification did NOT occur. All UI/screen findings are source-code inspections.')

doc.add(table(['Screen / flow', 'Source inspected', 'Emulator test', 'Physical device test', 'Automated UI test', 'Status'],
    [
        ['URL scanner (UrlScannerScreen)', 'Yes', 'No', 'No', 'No', 'UNVERIFIED at runtime'],
        ['Settings / data-sharing toggle', 'Yes', 'No', 'No', 'No', 'UNVERIFIED at runtime'],
        ['VPN consent flow', 'Yes', 'No', 'No', 'No', 'UNVERIFIED at runtime'],
        ['Auth / education / dashboard', 'Yes', 'No', 'No', 'No', 'UNVERIFIED at runtime'],
        ['Navigation + permissions', 'Yes', 'No', 'No', 'No', 'UNVERIFIED at runtime'],
    ],
    widths=[2400, 1300, 1300, 1300, 1300, 1600], body_size=8.5))
caption('Section 8 - Android integration and UI coverage.')

subheading('8.1 Automated tests written but not executed')
P('app/src/test contains SignatureDatabaseTest, PerformanceTest, UrlScannerEngineTest, LexicalFeatureExtractorTest, VerdictEngineTest, IntegrityShieldTest, TotpCryptoManagerTest. None were executed in this workspace (no Android Gradle Plugin test runner available).')
doc.add(page_break())

# ============================ SECTION 9 ============================
doc.add(heading('Section 9 - Defect Register', level=1))
P('Consolidated defect register. Root causes are marked "confirmed" only where established; otherwise "suspected".')

doc.add(table(['ID', 'Issue / component', 'Severity', 'Observed behaviour / error', 'Root cause', 'Fix attempted', 'Status', 'Remaining risk', 'Next action'],
    [
        ['D-01', 'ML accuracy/recall below target (NeuroShield)', 'High', 'Best acc 0.9313, rec 0.9065 vs 0.95/0.95 targets', 'Lexical-only features; single-source data; random split', 'Trained LR ONNX + calibration + ensemble; documented', 'Open (disclosed)', 'Missed phishing threats in production', 'Retrain on larger, diverse, time-separated data; add reputation/URL-path signals'],
        ['D-02', 'APK build blocked (no SDK here / YARA native)', 'Critical', './gradlew :app:assembleDebug cannot run; prior notes: CMake fails on missing yara-src/libyara/', 'No Android SDK configured here; yara native sources absent', 'None (environment); yara module degrades to Kotlin', 'Open', 'No installable APK; no device testing', 'Obtain SDK; resolve or remove YARA native build; then build'],
        ['D-03', 'SignatureVerifier placeholder hash', 'Medium', 'Hash UPDATE_ME...; logs and continues on mismatch (not fail-closed)', 'Placeholder not replaced', 'None', 'Open', 'Anti-repackaging not enforced', 'Set real hash; make fail-closed for release'],
        ['D-04', 'VPN upstream DNS hardcoded 8.8.8.8', 'Medium (privacy)', 'User DNS visible to Google; not user-configurable', 'Hardcoded in native code', 'None', 'Open (disclosed)', 'Privacy exposure of DNS queries', 'Make upstream DNS user-configurable'],
        ['D-05', 'getNativeMetrics returns zeros', 'Low', '{0,0,0,0,0,0} logged, not actionable', 'Placeholder stub', 'None', 'Open', 'No real VPN health signal', 'Instrument native counters'],
        ['D-06', 'malware_signatures.bin missing', 'Medium', 'Bloom filter empty at runtime', 'Asset absent', 'None', 'Open', 'No signature matches claimed', 'Provide asset or document empty-filter consequence'],
        ['D-07', 'TFLite model unevaluated', 'Medium', 'phishing_model.tflite cannot run here', 'TFL interpreter blocked by application-control policy', 'None', 'Blocked', 'TFLite path unverified', 'Evaluate in permissive environment or retire in favor of ONNX'],
        ['D-08', 'Android unit/instrumented tests not run', 'High', 'No AGP/Android test runner here', 'No Android SDK', 'None', 'Blocked', 'Unit behaviour unverified', 'Obtain SDK; execute :app:testDebugUnitTest'],
        ['D-09', 'Feature-vector malware model synthetic', 'Medium', 'Shipped model trained on synthetic data', 'Real Droidware data inaccessible', 'None (pipeline validated)', 'Open (disclosed)', 'Not a claim of real detection', 'Train on real Droidware 68-feature data before production'],
    ],
    widths=[600, 1700, 800, 1900, 1400, 1400, 700, 1300, 1600], body_size=7.5))
caption('Section 9 - Consolidated defect register.')
doc.add(page_break())

# ============================ SECTION 10 ============================
doc.add(heading('Section 10 - Final Verification Matrix', level=1))

doc.add(table(['Requirement', 'Evidence', 'Execution status', 'Outstanding gaps', 'Final disposition'],
    [
        ['On-device phishing detection (NeuroShield)', 'Source present; ONNX loads & infers offline', 'Implemented; ML verified offline; Android runtime NOT verified', 'No device/emulator; APK not built', 'COMPLETE in source / PARTIAL verified'],
        ['Calibrated ML phishing model (53 features)', 'current_asset_50k_dynamic.json (acc 0.9274, rec 0.8935)', 'PASS (offline)', 'Below 0.95/0.95 target', 'COMPLETE (target not met, disclosed)'],
        ['External validation (OpenPhish)', 'external_openphish_validation.json', 'PASS', 'Small single-source sample', 'COMPLETE (indicative only)'],
        ['On-device malware scanning', 'Source present for all components', 'Implemented; NOT evaluated on real malware', 'No device; no labeled dataset', 'PARTIAL / NOT verified for effectiveness'],
        ['Real-time DNS shielding', 'Source present', 'Implemented; NOT verified on device', 'No device', 'PARTIAL / NOT verified'],
        ['Local detection by default; consent-gated telemetry', 'Source inspection (reporters gated default OFF)', 'PASS (code review)', 'Consent not exercised at runtime', 'COMPLETE in design / code review'],
        ['APK debug build', 'No SDK in this workspace', 'BLOCKED', 'No SDK; YARA native question unresolved', 'BLOCKED'],
        ['Unit / instrumented tests', 'Tests written, not executed', 'BLOCKED', 'No AGP/test runner', 'BLOCKED'],
        ['TFLite model evaluation', 'Interpreter blocked', 'BLOCKED', 'No TFL interpreter', 'BLOCKED'],
        ['Malware labeled-data evaluation', 'No dataset/device', 'NOT RUN', 'No device; EMBER incompatible; MALDROID inaccessible', 'NOT verifiable here'],
    ],
    widths=[1700, 1700, 1300, 1700, 1600], body_size=7.5))
caption('Section 10 - Final verification matrix.')

subheading('10.1 What Can and Cannot Be Claimed')
P('Genuinely claimable: the source code implements a complete on-device Android security stack (NeuroShield phishing detection, Malware Engine, DNS-shielding VpnService, consent-gated privacy); the NeuroShield ONNX model was trained, exported, calibrated and evaluated on genuine held-out data (accuracy 0.9274, recall 0.8935, precision 0.9517 on the 50k sample); external OpenPhish validation was performed; privacy controls are consent-gated by design.')
P('NOT claimable: that the APK builds or installs (not built here); that any UI, VPN, or malware scan works on a device (not tested); that detection meets the 0.95/0.95 targets (not met, disclosed); that the Malware Engine detects real malware (no labeled-data evaluation); that no data leaves the device (design claim, not empirically measured); any specific real-world detection rate.')
doc.add(page_break())

# ============================ ABBREVIATIONS ============================
doc.add(heading('Abbreviations, Acronyms and Full Forms', level=1))
abbrev = [
    ('APK', 'Android Package'),
    ('API', 'Application Programming Interface'),
    ('CI', 'Confidence Interval'),
    ('CPU', 'Central Processing Unit'),
    ('DGA', 'Domain Generation Algorithm'),
    ('DNS', 'Domain Name System'),
    ('DoT', 'DNS-over-TLS'),
    ('DoH', 'DNS-over-HTTPS'),
    ('EMBER', 'Endpoint Malware & Emulation Benchmarking Research'),
    ('F1', 'F1 Score (harmonic mean of precision and recall)'),
    ('FNR', 'False Negative Rate'),
    ('FPR', 'False Positive Rate'),
    ('FP', 'False Positive'),
    ('FN', 'False Negative'),
    ('JNI', 'Java Native Interface'),
    ('JSON', 'JavaScript Object Notation'),
    ('LR', 'Logistic Regression'),
    ('ML', 'Machine Learning'),
    ('MTU', 'Maximum Transmission Unit'),
    ('NDK', 'Android Native Development Kit'),
    ('ONNX', 'Open Neural Network Exchange'),
    ('ORT', 'ONNX Runtime'),
    ('SDK', 'Software Development Kit'),
    ('SLD', 'Second-Level Domain'),
    ('TFLite', 'TensorFlow Lite'),
    ('TP', 'True Positive'),
    ('TN', 'True Negative'),
    ('UI', 'User Interface'),
    ('URL', 'Uniform Resource Locator'),
    ('VPN', 'Virtual Private Network'),
    ('YARA', 'YARA pattern-matching tool (name used as-is)'),
]
doc.add(table(['Abbreviation', 'Full Form'], abbrev, widths=[2200, 6800], body_size=9.5, col_aligns=['left', 'left']))
doc.add(page_break())

doc.save('A:\\Protctus-app\\Protctus_Actual_Testing_and_Verification_Report.docx')
print('Document 2 saved: Protctus_Actual_Testing_and_Verification_Report.docx')
